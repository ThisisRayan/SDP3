package com.example.sdp3

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.sdp3.database.AppDatabase
import com.example.sdp3.database.UserProfile as DbUserProfile
import com.example.sdp3.database.AdminProfile as DbAdminProfile
import com.example.sdp3.ui.theme.SDP3Theme
import com.example.sdp3.ui.auth.*
import com.example.sdp3.ui.map.*
import com.example.sdp3.ui.history.*
import com.example.sdp3.ui.profile.*
import com.example.sdp3.ui.admin.*
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_profile")

data class UserProfile(
    val id: Int = 0,
    val name: String,
    val age: Int,
    val phone: String,
    val hometown: String,
    val upazila: String,
    val detailedAddress: String,
    val balance: Double,
    val pendingFine: Double = 0.0
)

data class AdminProfile(
    val adminId: Int = 0,
    val name: String,
    val adminAddress: String,
    val phone: String
)

sealed class ProfileState {
    object Loading : ProfileState()
    object Welcome : ProfileState()
    object Permissions : ProfileState()
    object Login : ProfileState()
    data class Registration(val phone: String) : ProfileState()
    data class UserLoaded(val profile: UserProfile) : ProfileState()
    data class AdminLoaded(val profile: AdminProfile) : ProfileState()
}

data class Trip(
    val id: String,
    val userPhone: String,
    val userName: String,
    val startStation: String = "",
    val endStation: String = "",
    val stationLocation: String,
    val durationSeconds: Long,
    val cost: Double,
    val date: String,
    val startLat: Double,
    val startLon: Double,
    val endLat: Double,
    val endLon: Double,
    val distanceKm: Double,
    val routeJson: String = "",
    val fine: Double = 0.0,
    val wasBooked: Boolean = false
)

data class Booking(
    val id: String,
    val userPhone: String,
    val stationName: String,
    val startTime: Long,
    val date: String,
    val status: String,
    val amountPaid: Double,
    val refundAmount: Double = 0.0
)

sealed class Screen(val icon: ImageVector, val label: String) {
    object Map : Screen(Icons.Default.LocationOn, "Cycles")
    object History : Screen(Icons.Default.History, "History")
    object Profile : Screen(Icons.Default.Person, "Profile")
    object Admin : Screen(Icons.Default.AdminPanelSettings, "Admin")
}

class MainActivity : ComponentActivity() {
    private lateinit var database: AppDatabase
    private lateinit var firebaseAnalytics: FirebaseAnalytics

    val locationRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = AppDatabase.getDatabase(this)
        firebaseAnalytics = FirebaseAnalytics.getInstance(this)
        
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName
        
        val themePrefFlow = dataStore.data.map { it[stringPreferencesKey("theme_preference")] ?: "System" }

        enableEdgeToEdge()
        setContent {
            val themePreference by themePrefFlow.collectAsState(initial = "System")
            SDP3Theme(themePreference = themePreference) {
                val scope = rememberCoroutineScope()
                
                @OptIn(ExperimentalCoroutinesApi::class)
                val profileStateFlow = remember {
                    dataStore.data.flatMapLatest { prefs ->
                        val phone = prefs[stringPreferencesKey("active_user_phone")]
                        if (phone == null) {
                            flowOf(ProfileState.Welcome)
                        } else {
                            val adminFlow = database.adminProfileDao().getAdminProfileFlow(phone)
                            val userFlow = database.userProfileDao().getUserByPhoneFlow(phone)
                            
                            adminFlow.flatMapLatest { admin ->
                                if (admin != null) {
                                    flowOf(ProfileState.AdminLoaded(dbAdminToUi(admin)!!) as ProfileState)
                                } else {
                                    userFlow.map { user ->
                                        if (user != null) {
                                            ProfileState.UserLoaded(dbProfileToUi(user)!!) as ProfileState
                                        } else {
                                            ProfileState.Welcome as ProfileState
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                var internalProfileState by remember { mutableStateOf<ProfileState?>(null) }
                val dbProfileState by profileStateFlow.collectAsState(initial = ProfileState.Loading)
                val profileState = internalProfileState ?: dbProfileState
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Map) }

                when (val state = profileState) {
                    is ProfileState.Loading -> LoadingScreen()
                    is ProfileState.Welcome -> WelcomeScreen(onGetStarted = { 
                        val permissions = mutableListOf(
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION,
                            android.Manifest.permission.CAMERA
                        ).apply {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                add(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        val allGranted = permissions.all { 
                            ContextCompat.checkSelfPermission(this@MainActivity, it) == android.content.pm.PackageManager.PERMISSION_GRANTED 
                        }
                        internalProfileState = if (allGranted) ProfileState.Login else ProfileState.Permissions 
                    })
                    is ProfileState.Permissions -> PermissionScreen(onAllPermissionsGranted = { internalProfileState = ProfileState.Login })
                    is ProfileState.Login -> LoginScreen(
                        onLoginSuccess = { phone ->
                            scope.launch {
                                val admin = database.adminProfileDao().getAdminProfile(phone)
                                if (admin != null) {
                                    dataStore.edit { it[stringPreferencesKey("active_user_phone")] = phone }
                                    internalProfileState = ProfileState.AdminLoaded(dbAdminToUi(admin)!!)
                                } else {
                                    val user = database.userProfileDao().getUserByPhone(phone)
                                    if (user != null) {
                                        dataStore.edit { it[stringPreferencesKey("active_user_phone")] = phone }
                                        internalProfileState = ProfileState.UserLoaded(dbProfileToUi(user)!!)
                                    } else {
                                        internalProfileState = ProfileState.Registration(phone)
                                    }
                                }
                            }
                        },
                        onGoToRegister = { internalProfileState = ProfileState.Registration("") },
                        database = database
                    )
                    is ProfileState.Registration -> Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        ProfileSetupScreen(
                            initialPhone = state.phone,
                            onProfileSaved = { name, age, phone, hometown, upazila, detailedAddress ->
                                scope.launch {
                                    if (phone == "01999999999") {
                                        database.adminProfileDao().insertAdminProfile(DbAdminProfile(name = name, adminAddress = "$detailedAddress, $upazila, $hometown", phone = phone))
                                    } else {
                                        database.userProfileDao().insertProfile(DbUserProfile(name = name, age = age, phone = phone, hometown = hometown, upazila = upazila, detailedAddress = detailedAddress, balance = 60.0))
                                    }
                                    internalProfileState = ProfileState.Login
                                }
                            },
                            onBackToLogin = { internalProfileState = ProfileState.Login }
                        )
                    }
                    is ProfileState.UserLoaded -> {
                        val userProfile = state.profile
                        val tripHistory by database.tripDao().getTripsByUser(userProfile.phone).collectAsState(initial = emptyList())
                        val bookingHistory by database.bookingDao().getBookingsByUser(userProfile.phone).collectAsState(initial = emptyList())
                        val userReports by database.reportDao().getReportsByUser(userProfile.phone).collectAsState(initial = emptyList())
                        val isRenting by remember { dataStore.data.map { it[booleanPreferencesKey("is_renting")] ?: false } }.collectAsState(initial = false)
                        val isBooked by remember { dataStore.data.map { it[booleanPreferencesKey("is_booked")] ?: false } }.collectAsState(initial = false)

                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            bottomBar = {
                                NavigationBar {
                                    listOf(Screen.Map, Screen.History, Screen.Profile).forEach { screen ->
                                        NavigationBarItem(
                                            icon = { Icon(screen.icon, contentDescription = null) },
                                            label = { Text(screen.label) },
                                            selected = currentScreen == screen,
                                            onClick = { currentScreen = screen }
                                        )
                                    }
                                }
                            }
                        ) { innerPadding ->
                            Surface(modifier = Modifier.fillMaxSize().padding(innerPadding), color = MaterialTheme.colorScheme.background) {
                                when (currentScreen) {
                                    Screen.Map -> MapScreen(
                                        userProfile = userProfile,
                                        database = database,
                                        onTripEnd = { trip ->
                                            scope.launch {
                                                val lastId = database.tripDao().getLastTripId()
                                                val currentTimeMs = System.currentTimeMillis()
                                                val lastTimeMs = lastId?.removePrefix("TRP")?.toLongOrNull() ?: 0L
                                                val newId = "TRP${maxOf(currentTimeMs, lastTimeMs + 1)}"
                                                database.tripDao().insertTrip(com.example.sdp3.database.Trip(newId, trip.userPhone, trip.userName, trip.startStation, trip.endStation, trip.stationLocation, trip.durationSeconds, trip.cost, trip.date, trip.startLat, trip.startLon, trip.endLat, trip.endLon, trip.distanceKm, trip.routeJson, trip.fine, trip.wasBooked))
                                                val dbUser = database.userProfileDao().getUserByPhone(userProfile.phone)
                                                dbUser?.let { user ->
                                                    val amountToDeduct = if (trip.wasBooked) maxOf(0.0, trip.cost - 60.0) + trip.fine else trip.cost + trip.fine
                                                    val newBalance = (user.balance - amountToDeduct).coerceAtLeast(0.0)
                                                    val extraFine = if (user.balance - amountToDeduct < 0) kotlin.math.abs(user.balance - amountToDeduct) else 0.0
                                                    database.userProfileDao().updateProfile(user.copy(balance = newBalance, pendingFine = user.pendingFine + extraFine))
                                                }
                                                database.stationDao().getStationByName(trip.stationLocation)?.let { station -> database.stationDao().updateStation(station.copy(availableCycles = (station.availableCycles + 1).coerceAtMost(station.totalCycles))) }
                                            }
                                        }
                                    )
                                    Screen.History -> HistoryPage(
                                        userPhone = userProfile.phone,
                                        trips = tripHistory.map { Trip(it.id, it.userPhone, it.userName, it.startStation, it.endStation, it.stationLocation, it.durationSeconds, it.cost, it.date, it.startLat, it.startLon, it.endLat, it.endLon, it.distanceKm, it.routeJson, it.fine, it.wasBooked) },
                                        bookings = bookingHistory.map { Booking(it.id, it.userPhone, it.stationName, it.startTime, it.date, it.status, it.amountPaid, it.refundAmount) },
                                        reports = userReports,
                                        isActionOngoing = isRenting || isBooked,
                                        onClearHistory = { scope.launch { database.tripDao().deleteTripsByUser(userProfile.phone); database.bookingDao().deleteFinishedBookingsByUser(userProfile.phone) } },
                                        onReportTrip = { trip, title, desc -> scope.launch { database.reportDao().insertReport(com.example.sdp3.database.Report(id = "REP${System.currentTimeMillis()}", referenceId = trip.id, type = "TRIP", userName = userProfile.name, userPhone = userProfile.phone, title = title, description = desc, date = java.text.SimpleDateFormat("dd MMM, yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date()))) } },
                                        onReportBooking = { booking, title, desc -> scope.launch { database.reportDao().insertReport(com.example.sdp3.database.Report(id = "REP${System.currentTimeMillis()}", referenceId = booking.id, type = "BOOKING", userName = userProfile.name, userPhone = userProfile.phone, title = title, description = desc, date = java.text.SimpleDateFormat("dd MMM, yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date()))) } }
                                    )
                                    Screen.Profile -> ProfilePage(
                                        userProfile = userProfile,
                                        database = database,
                                        onUpdateProfile = { name, age, phone, hometown, upazila, detailedAddress -> scope.launch { database.userProfileDao().updateProfile(uiProfileToDb(userProfile.copy(name = name, age = age, phone = phone, hometown = hometown, upazila = upazila, detailedAddress = detailedAddress))) } },
                                        onSignOut = { scope.launch { dataStore.edit { it.clear() }; internalProfileState = ProfileState.Welcome; currentScreen = Screen.Map } },
                                        totalDistance = tripHistory.sumOf { it.distanceKm },
                                        totalTime = (tripHistory.sumOf { it.durationSeconds } / 60).toInt(),
                                        totalTrips = tripHistory.size,
                                        totalCancelled = bookingHistory.count { it.status == "CANCELLED" || it.status == "EXPIRED" }
                                    )
                                    Screen.Admin -> { }
                                }
                            }
                        }
                    }
                    is ProfileState.AdminLoaded -> {
                        val adminProfile = state.profile
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            bottomBar = {
                                NavigationBar {
                                    listOf(Screen.Map, Screen.Admin, Screen.Profile).forEach { screen ->
                                        NavigationBarItem(
                                            icon = { Icon(screen.icon, contentDescription = null) },
                                            label = { Text(if (screen == Screen.Map) "Live Map" else screen.label) },
                                            selected = currentScreen == screen,
                                            onClick = { currentScreen = screen }
                                        )
                                    }
                                }
                            }
                        ) { innerPadding ->
                            Surface(modifier = Modifier.fillMaxSize().padding(innerPadding), color = MaterialTheme.colorScheme.background) {
                                when (currentScreen) {
                                    Screen.Map -> AdminMapScreen(adminProfile = adminProfile, database = database)
                                    Screen.Admin -> AdminDashboard(database = database)
                                    Screen.Profile -> AdminProfilePage(adminProfile = adminProfile, onSignOut = { scope.launch { dataStore.edit { it.clear() }; internalProfileState = ProfileState.Welcome; currentScreen = Screen.Map } })
                                    else -> { }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun dbProfileToUi(dbProfile: DbUserProfile?): UserProfile? = dbProfile?.let { UserProfile(it.id, it.name, it.age, it.phone, it.hometown, it.upazila, it.detailedAddress, it.balance, it.pendingFine) }
fun dbAdminToUi(dbAdmin: DbAdminProfile?): AdminProfile? = dbAdmin?.let { AdminProfile(it.adminId, it.name, it.adminAddress, it.phone) }
fun uiProfileToDb(uiProfile: UserProfile): DbUserProfile = DbUserProfile(uiProfile.id, uiProfile.name, uiProfile.age, uiProfile.phone, uiProfile.hometown, uiProfile.upazila, uiProfile.detailedAddress, uiProfile.balance, uiProfile.pendingFine)
