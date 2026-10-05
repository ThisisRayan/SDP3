package com.example.sdp3.ui.map

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Color as AndroidColor
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.activity.result.IntentSenderRequest
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import androidx.datastore.preferences.core.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.sdp3.MainActivity
import com.example.sdp3.TrackingService
import com.example.sdp3.Trip
import com.example.sdp3.UserProfile
import com.example.sdp3.database.AppDatabase
import com.example.sdp3.dataStore
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.util.*
import java.util.concurrent.Executors

@SuppressLint("MissingPermission")
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapScreen(userProfile: UserProfile, database: AppDatabase, onTripEnd: (Trip) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val IS_RENTING_KEY = booleanPreferencesKey("is_renting")
    val ACTIVE_START_TIME = longPreferencesKey("active_start_time")
    val ACTIVE_START_LAT = doublePreferencesKey("active_start_lat")
    val ACTIVE_START_LON = doublePreferencesKey("active_start_lon")
    val ACTIVE_ROUTE_JSON = stringPreferencesKey("active_route_json")
    val ACTIVE_CYCLE_NAME = stringPreferencesKey("active_cycle_name")
    val TOTAL_FINE_KEY = doublePreferencesKey("total_fine")
    val GPS_LOST_TIME_KEY = longPreferencesKey("gps_lost_time")
    val IS_BOOKED_KEY = booleanPreferencesKey("is_booked")
    val BOOKED_STATION_KEY = stringPreferencesKey("booked_station_name")
    val BOOKING_TIME_KEY = longPreferencesKey("booking_time")
    val ACTIVE_BOOKING_ID = stringPreferencesKey("active_booking_id")
    val WAS_BOOKED_RENTAL = booleanPreferencesKey("was_booked_rental")
    val GPS_LOST_COUNT_KEY = intPreferencesKey("gps_lost_count")
    val LAST_APPLIED_FINE_COUNT_KEY = intPreferencesKey("last_applied_fine_count")

    var isMapExpanded by rememberSaveable { mutableStateOf(false) }
    var isRenting by remember { mutableStateOf(false) }
    var rentedStationName by remember { mutableStateOf<String?>(null) }
    var activeRentalStartTime by remember { mutableLongStateOf(0L) }
    var wasBookedRental by remember { mutableStateOf(false) }
    var isBooked by remember { mutableStateOf(false) }
    var bookedStationName by remember { mutableStateOf<String?>(null) }
    var showBookingRuleDialog by remember { mutableStateOf(false) }
    var showRentRuleDialog by remember { mutableStateOf(false) }
    var pendingBookingCycle by remember { mutableStateOf<com.example.sdp3.database.Station?>(null) }
    var bookingStartTime by remember { mutableLongStateOf(0L) }
    var remainingBookingTime by remember { mutableLongStateOf(0L) }
    var showBookingCancelDialog by remember { mutableStateOf(false) }
    var startLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var elapsedTime by remember { mutableLongStateOf(0L) }
    var searchQuery by remember { mutableStateOf("") }
    val activeRoutePoints = remember { mutableStateListOf<GeoPoint>() }
    var isGpsLost by rememberSaveable { mutableStateOf(false) }
    var hasGpsFix by rememberSaveable { mutableStateOf(false) }
    var showGpsConnected by remember { mutableStateOf(false) }
    var selectedCycle by remember { mutableStateOf<com.example.sdp3.database.Station?>(null) }
    var showQrScanner by remember { mutableStateOf(false) }
    
    var showGpsWarning by remember { mutableStateOf(false) }
    var gpsWarningTimer by remember { mutableLongStateOf(30L) }
    var totalFine by rememberSaveable { mutableDoubleStateOf(0.0) }
    var gpsLostTimestamp by remember { mutableLongStateOf(0L) }
    var gpsLostCount by rememberSaveable { mutableIntStateOf(0) }
    var lastAppliedFineCount by rememberSaveable { mutableIntStateOf(0) }
    val GPS_FINE_AMOUNT = 20.0 // Base fine
    val FINE_PER_SECOND = 1.0 // Incremental fine per second after grace period

    val permissionsToRequest = mutableListOf(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
        android.Manifest.permission.CAMERA
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val locationPermissionsState = rememberMultiplePermissionsState(permissionsToRequest)

    val dbStations by database.stationDao().getAllStationsFlow().collectAsState(initial = emptyList())
    val filteredStations = dbStations.filter { it.status != "Permanently Closed" }
    
    val filteredLocations = remember(searchQuery, filteredStations) { 
        if (searchQuery.isBlank()) filteredStations else filteredStations.filter { it.name.contains(searchQuery, ignoreCase = true) }.take(100)
    }
    
    LaunchedEffect(filteredStations, selectedCycle) {
        selectedCycle?.let { current ->
            filteredStations.find { it.id == current.id }?.let { updated ->
                if (updated.availableCycles != current.availableCycles || updated.status != current.status) {
                    selectedCycle = updated
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val prefs = context.dataStore.data.first()
        isBooked = prefs[IS_BOOKED_KEY] ?: false
        bookedStationName = prefs[BOOKED_STATION_KEY]
        bookingStartTime = prefs[BOOKING_TIME_KEY] ?: 0L
        if (prefs[IS_RENTING_KEY] == true) {
            isRenting = true
            activeRentalStartTime = prefs[ACTIVE_START_TIME] ?: 0L
            startLocation = GeoPoint(prefs[ACTIVE_START_LAT] ?: 0.0, prefs[ACTIVE_START_LON] ?: 0.0)
            rentedStationName = prefs[ACTIVE_CYCLE_NAME]
            totalFine = prefs[TOTAL_FINE_KEY] ?: 0.0
            gpsLostTimestamp = prefs[GPS_LOST_TIME_KEY] ?: 0L
            gpsLostCount = prefs[GPS_LOST_COUNT_KEY] ?: 0
            lastAppliedFineCount = prefs[LAST_APPLIED_FINE_COUNT_KEY] ?: 0
            wasBookedRental = prefs[WAS_BOOKED_RENTAL] ?: false
            if (activeRentalStartTime != 0L) elapsedTime = ((System.currentTimeMillis() - activeRentalStartTime) / 1000).coerceAtLeast(0L)
            
            val intent = Intent(context, TrackingService::class.java).apply { action = TrackingService.ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        } else if (isBooked) {
            val intent = Intent(context, TrackingService::class.java).apply { action = TrackingService.ACTION_START_BOOKING }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }
    }

    val routeJsonFlow = remember { 
        context.dataStore.data
            .map { it[ACTIVE_ROUTE_JSON] ?: "[]" }
            .distinctUntilChanged()
    }
    val currentRouteJson by routeJsonFlow.collectAsState(initial = "[]")
    LaunchedEffect(currentRouteJson) {
        try {
            val arr = JSONArray(currentRouteJson)
            if (arr.length() != activeRoutePoints.size) {
                activeRoutePoints.clear()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    activeRoutePoints.add(GeoPoint(obj.getDouble("lat"), obj.getDouble("lon")))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    val startRenting = { cycle: com.example.sdp3.database.Station ->
        if (userProfile.balance < 10) {
            android.widget.Toast.makeText(context, "Insufficient balance! Min 10 BDT required.", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            val st = System.currentTimeMillis()
            val sl = startLocation ?: GeoPoint(cycle.latitude, cycle.longitude)
            val isStartingFromBooking = isBooked && cycle.name == bookedStationName
            isRenting = true
            rentedStationName = cycle.name
            activeRentalStartTime = st
            startLocation = sl
            totalFine = 0.0
            wasBookedRental = isStartingFromBooking
            activeRoutePoints.clear()
            activeRoutePoints.add(sl)
            
            val intent = Intent(context, TrackingService::class.java).apply { action = TrackingService.ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
            
            if (isStartingFromBooking) {
                isBooked = false
                bookedStationName = null
                scope.launch {
                    val prefs = context.dataStore.data.first()
                    val bTime = prefs[BOOKING_TIME_KEY] ?: 0L
                    val now = System.currentTimeMillis()
                    val diffSeconds = (now - bTime) / 1000
                    val shouldRefund = diffSeconds < 60

                    if (shouldRefund) {
                        val dbUser = database.userProfileDao().getUserByPhone(userProfile.phone)
                        dbUser?.let {
                            database.userProfileDao().updateProfile(it.copy(balance = it.balance + 60.0))
                        }
                        wasBookedRental = false
                        android.widget.Toast.makeText(context, "Refunded 60 BDT: Ride started within 1 min.", android.widget.Toast.LENGTH_SHORT).show()
                    }

                    prefs[ACTIVE_BOOKING_ID]?.let { bId ->
                        database.bookingDao().getBookingById(bId)?.let { booking ->
                            database.bookingDao().updateBooking(booking.copy(status = "COMPLETED"))
                        }
                    }
                    
                    context.dataStore.edit { p ->
                        p[IS_BOOKED_KEY] = false
                        p[BOOKED_STATION_KEY] = ""
                        p[BOOKING_TIME_KEY] = 0L
                        p[ACTIVE_BOOKING_ID] = ""
                        
                        p[IS_RENTING_KEY] = true
                        p[ACTIVE_START_TIME] = st
                        p[ACTIVE_START_LAT] = sl.latitude
                        p[ACTIVE_START_LON] = sl.longitude
                        p[ACTIVE_ROUTE_JSON] = JSONArray().put(JSONObject().put("lat", sl.latitude).put("lon", sl.longitude)).toString()
                        p[ACTIVE_CYCLE_NAME] = cycle.name
                        p[TOTAL_FINE_KEY] = 0.0
                        p[GPS_LOST_TIME_KEY] = 0L
                        p[WAS_BOOKED_RENTAL] = !shouldRefund
                    }
                }
            } else {
                scope.launch {
                    context.dataStore.edit { p ->
                        p[IS_RENTING_KEY] = true
                        p[ACTIVE_START_TIME] = st
                        p[ACTIVE_START_LAT] = sl.latitude
                        p[ACTIVE_START_LON] = sl.longitude
                        p[ACTIVE_ROUTE_JSON] = JSONArray().put(JSONObject().put("lat", sl.latitude).put("lon", sl.longitude)).toString()
                        p[ACTIVE_CYCLE_NAME] = cycle.name
                        p[TOTAL_FINE_KEY] = 0.0
                        p[GPS_LOST_TIME_KEY] = 0L
                        p[WAS_BOOKED_RENTAL] = false
                    }
                    database.stationDao().getStationByName(cycle.name)?.let { station ->
                        database.stationDao().updateStation(station.copy(availableCycles = (station.availableCycles - 1).coerceAtLeast(0)))
                    }
                }
            }
        }
    }

    val returnCycle = { cycle: com.example.sdp3.database.Station ->
        if (cycle.availableCycles >= cycle.totalCycles) {
            android.widget.Toast.makeText(context, "No slot available! Try nearby station to return", android.widget.Toast.LENGTH_SHORT).show()
            false
        } else {
            val totalSeconds = (System.currentTimeMillis() - activeRentalStartTime) / 1000
            val currentBalance = userProfile.balance
            val totalMinutes = totalSeconds / 60.0
            val minutesCoveredByBalance = (currentBalance / 2.0).coerceAtMost(totalMinutes)
            val cost = minutesCoveredByBalance * 2.0
            val extraMinutes = (totalMinutes - minutesCoveredByBalance).coerceAtLeast(0.0)
            val calculatedFine = (extraMinutes * 2.50) + totalFine
            val endLoc = startLocation ?: GeoPoint(cycle.latitude, cycle.longitude)
            val dist = if (activeRoutePoints.size > 1) {
                var td = 0.0
                for (i in 0 until activeRoutePoints.size - 1) td += activeRoutePoints[i].distanceToAsDouble(activeRoutePoints[i + 1])
                td / 1000.0
            } else 0.0

            onTripEnd(
                Trip(
                    id = "",
                    userPhone = userProfile.phone,
                    userName = userProfile.name,
                    startStation = rentedStationName ?: "Unknown",
                    endStation = cycle.name,
                    stationLocation = cycle.name,
                    durationSeconds = totalSeconds,
                    cost = cost,
                    date = java.text.SimpleDateFormat("dd MMM, yyyy HH:mm", Locale.getDefault()).format(Date(System.currentTimeMillis())),
                    startLat = startLocation?.latitude ?: 0.0,
                    startLon = startLocation?.longitude ?: 0.0,
                    endLat = endLoc.latitude,
                    endLon = endLoc.longitude,
                    distanceKm = dist,
                    routeJson = JSONArray().apply {
                        activeRoutePoints.forEach { p -> put(JSONObject().put("lat", p.latitude).put("lon", p.longitude)) }
                    }.toString(),
                    fine = calculatedFine,
                    wasBooked = wasBookedRental
                )
            )
            context.startService(Intent(context, TrackingService::class.java).apply { action = TrackingService.ACTION_STOP })

            scope.launch {
                context.dataStore.edit { prefs ->
                    prefs[IS_RENTING_KEY] = false
                    prefs[ACTIVE_START_TIME] = 0L
                    prefs[ACTIVE_START_LAT] = 0.0
                    prefs[ACTIVE_START_LON] = 0.0
                    prefs[ACTIVE_ROUTE_JSON] = ""
                    prefs[ACTIVE_CYCLE_NAME] = ""
                    prefs[TOTAL_FINE_KEY] = 0.0
                    prefs[GPS_LOST_TIME_KEY] = 0L
                    prefs[GPS_LOST_COUNT_KEY] = 0
                    prefs[LAST_APPLIED_FINE_COUNT_KEY] = 0
                    prefs[WAS_BOOKED_RENTAL] = false
                }
                isGpsLost = false
                showGpsWarning = false
                gpsWarningTimer = 30L
                gpsLostTimestamp = 0L
                gpsLostCount = 0
                lastAppliedFineCount = 0
                totalFine = 0.0
                isRenting = false
                rentedStationName = null
                activeRentalStartTime = 0L
                startLocation = null
                elapsedTime = 0L
                activeRoutePoints.clear()
                wasBookedRental = false
            }
            true
        }
    }

    LaunchedEffect(isRenting, rentedStationName, totalFine, gpsLostTimestamp) {
        if (isRenting) {
            context.dataStore.edit { prefs ->
                prefs[IS_RENTING_KEY] = true
                prefs[ACTIVE_START_TIME] = if (activeRentalStartTime != 0L) activeRentalStartTime else System.currentTimeMillis()
                prefs[ACTIVE_START_LAT] = startLocation?.latitude ?: 0.0
                prefs[ACTIVE_START_LON] = startLocation?.longitude ?: 0.0
                prefs[ACTIVE_CYCLE_NAME] = rentedStationName ?: "Unknown Cycle"
                prefs[TOTAL_FINE_KEY] = totalFine
                prefs[GPS_LOST_TIME_KEY] = gpsLostTimestamp
                prefs[GPS_LOST_COUNT_KEY] = gpsLostCount
                prefs[LAST_APPLIED_FINE_COUNT_KEY] = lastAppliedFineCount
                prefs[WAS_BOOKED_RENTAL] = wasBookedRental
            }
        }
    }

    LaunchedEffect(isRenting, activeRentalStartTime) { 
        if (isRenting && activeRentalStartTime != 0L) { 
            var lastThreshold = -1.0
            while (isRenting) { 
                val now = System.currentTimeMillis()
                elapsedTime = ((now - activeRentalStartTime) / 1000).coerceAtLeast(0L)
                val currentBalance = userProfile.balance
                val minutes = elapsedTime / 60.0
                val estCost = minutes * 2.0
                val remaining = currentBalance - estCost
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                fun sendLowBalanceNotif(msg: String) {
                    val intent = Intent(context, MainActivity::class.java)
                    val pi = android.app.PendingIntent.getActivity(context, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE)
                    val notif = androidx.core.app.NotificationCompat.Builder(context, "tracking_channel")
                        .setContentTitle("Low Balance Warning")
                        .setContentText(msg)
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentIntent(pi)
                        .setAutoCancel(true)
                        .build()
                    notificationManager.notify(2, notif)
                }
                if (remaining <= 40.0 && lastThreshold == -1.0) {
                    if (remaining > 20.0) sendLowBalanceNotif(String.format(Locale.US, "Balance low: %.1f BDT remaining.", remaining))
                    lastThreshold = remaining
                }
                val thresholds = listOf(20.0, 15.0, 10.0, 5.0, 0.0)
                for (t in thresholds) {
                    if (remaining <= t && lastThreshold > t) {
                        if (t == 0.0) sendLowBalanceNotif("Balance zero! Fines will apply at 2.50 BDT/min.")
                        else sendLowBalanceNotif("Critical balance: ${String.format("%.1f", remaining)} BDT remaining.")
                        lastThreshold = t
                        break
                    }
                }
                delay(1000) 
            } 
        } 
    }
    
    LaunchedEffect(isBooked, bookingStartTime) {
        if (isBooked && bookingStartTime != 0L) {
            while (isBooked) {
                val currentTime = System.currentTimeMillis()
                val elapsed = (currentTime - bookingStartTime) / 1000
                val totalSecs = 30 * 60
                val remaining = (totalSecs - (if (elapsed < 0) 0L else elapsed)).coerceAtLeast(0)
                remainingBookingTime = remaining
                if (remaining <= 0) {
                    scope.launch {
                        val prefsData = context.dataStore.data.first()
                        prefsData[ACTIVE_BOOKING_ID]?.let { bId ->
                            database.bookingDao().getBookingById(bId)?.let { booking ->
                                database.bookingDao().updateBooking(booking.copy(status = "EXPIRED"))
                            }
                        }
                        bookedStationName?.let { name ->
                            database.stationDao().getStationByName(name)?.let { station ->
                                database.stationDao().updateStation(station.copy(availableCycles = station.availableCycles + 1))
                            }
                        }
                        isBooked = false
                        bookedStationName = null
                        context.startService(Intent(context, TrackingService::class.java).apply { action = TrackingService.ACTION_STOP })
                        context.dataStore.edit { prefs ->
                            prefs[IS_BOOKED_KEY] = false
                            prefs[BOOKED_STATION_KEY] = ""
                            prefs[BOOKING_TIME_KEY] = 0L
                            prefs[ACTIVE_BOOKING_ID] = ""
                        }
                    }
                }
                delay(1000)
            }
        }
    }

    val mapPadding by animateDpAsState(targetValue = if (isMapExpanded) 12.dp else 24.dp, label = "mapPadding")
    val bottomPadding by animateDpAsState(targetValue = if (isMapExpanded) 12.dp else 8.dp, label = "bottomPadding")
    val mapCornerRadius by animateDpAsState(targetValue = if (isMapExpanded) 16.dp else 28.dp, label = "mapCornerRadius")
    val attributionPadding by animateDpAsState(
        targetValue = if (selectedCycle != null) 216.dp else if (isMapExpanded) 24.dp else 6.dp,
        label = "attributionPadding"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = mapPadding, start = mapPadding, end = mapPadding, bottom = bottomPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(
            visible = !isMapExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                            Icon(imageVector = Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = "Welcome back,", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                            Text(text = userProfile.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.padding(top = 4.dp)) {
                                Text(text = String.format(Locale.US, "Balance: %.2f BDT", userProfile.balance), modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                OutlinedTextField(
                    value = searchQuery, onValueChange = { searchQuery = it }, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search cycle stations...") }, leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, contentDescription = null) } },
                    singleLine = true, shape = MaterialTheme.shapes.large
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Nearby Cycles", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.align(Alignment.Start))
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(mapCornerRadius))) {
            val anyLocationPermissionGranted = locationPermissionsState.permissions.any { it.status.isGranted }
            if (anyLocationPermissionGranted) {
                val lifecycleOwner = LocalLifecycleOwner.current
                val mapView = remember { MapView(context) }
                val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
                val myLocationOverlay = remember {
                    val provider = GpsMyLocationProvider(context).apply {
                        addLocationSource(android.location.LocationManager.NETWORK_PROVIDER)
                        addLocationSource(android.location.LocationManager.GPS_PROVIDER)
                        locationUpdateMinDistance = 1f 
                        locationUpdateMinTime = 1000 
                    }
                    val density = context.resources.displayMetrics.density
                    val size = (24 * density).toInt()
                    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.WHITE }
                    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
                    paint.color = "#4285F4".toColorInt()
                    canvas.drawCircle(size / 2f, size / 2f, size * 0.4f, paint)
                    MyLocationNewOverlay(provider, mapView).apply {
                        enableMyLocation()
                        setPersonIcon(bitmap)
                        setDirectionIcon(bitmap)
                        setDrawAccuracyEnabled(true)
                        enableFollowLocation() 
                    }
                }
                
                LaunchedEffect(anyLocationPermissionGranted) {
                    if (anyLocationPermissionGranted) {
                        try {
                            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                                location?.let {
                                    val startPoint = GeoPoint(it.latitude, it.longitude)
                                    mapView.controller.setCenter(startPoint)
                                    mapView.controller.setZoom(17.0)
                                }
                            }
                        } catch (e: SecurityException) { }
                    }
                }

                val activeRouteOverlay = remember { org.osmdroid.views.overlay.Polyline().apply { outlinePaint.color = AndroidColor.parseColor("#FC4C02"); outlinePaint.strokeWidth = 10f; outlinePaint.strokeCap = Paint.Cap.ROUND; outlinePaint.isAntiAlias = true } }
                LaunchedEffect(activeRoutePoints.size) { activeRouteOverlay.setPoints(activeRoutePoints.toList()); mapView.invalidate() }

                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize()) { mv ->
                        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
                    }
                    Text(
                        text = "© OpenStreetMap contributors", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = Color.Black.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = attributionPadding)
                    )
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = isMapExpanded && selectedCycle == null,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 64.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery, onValueChange = { searchQuery = it }, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search cycle stations...") }, leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, contentDescription = null) } },
                        singleLine = true, colors = TextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), focusedContainerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.medium
                    )
                }

                val locationManager = remember { context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager }
                LaunchedEffect(Unit) {
                    while (true) {
                        val isLocationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) locationManager.isLocationEnabled else {
                            try { locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) } catch (e: Exception) { false }
                        }
                        val myLoc = myLocationOverlay.myLocation
                        if (isLocationEnabled && myLoc != null && !hasGpsFix) {
                            hasGpsFix = true
                            if (isGpsLost) { showGpsConnected = true; isGpsLost = false; showGpsWarning = false; gpsWarningTimer = 30L; gpsLostTimestamp = 0L }
                        } else if (!isLocationEnabled) {
                            if (!isGpsLost && isRenting) { gpsLostCount++; gpsLostTimestamp = System.currentTimeMillis() }
                            isGpsLost = true; hasGpsFix = false
                            if (isRenting) {
                                if (!showGpsWarning) showGpsWarning = true
                                val gracePeriod = when (gpsLostCount) { 1 -> 30L; 2 -> 15L; 3 -> 5L; else -> 0L }
                                val elapsedGpsLostSeconds = (System.currentTimeMillis() - gpsLostTimestamp) / 1000
                                gpsWarningTimer = (gracePeriod - elapsedGpsLostSeconds).coerceAtLeast(0L)
                                if (gpsWarningTimer == 0L) {
                                    if (lastAppliedFineCount < gpsLostCount) { totalFine += GPS_FINE_AMOUNT; lastAppliedFineCount = gpsLostCount }
                                    totalFine += FINE_PER_SECOND
                                }
                            }
                        }
                        if (isLocationEnabled && myLoc == null) {
                            if (!myLocationOverlay.isMyLocationEnabled) myLocationOverlay.enableMyLocation()
                            if (Random().nextInt(5) == 0) { try { fusedLocationClient.lastLocation.addOnSuccessListener { loc -> loc?.let { myLocationOverlay.onLocationChanged(it, null) } } } catch (e: Exception) {} }
                        }
                        if (showGpsConnected) { delay(3000); showGpsConnected = false }
                        delay(1000)
                    }
                }
                fun createMarkerIcon(status: String, isRented: Boolean, isBooked: Boolean = false): BitmapDrawable {
                    val density = context.resources.displayMetrics.density; val width = (38 * density).toInt(); val height = (52 * density).toInt(); val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888); val canvas = Canvas(bitmap); val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                    val pinPath = Path().apply { moveTo(width/2f, height.toFloat()); cubicTo(0f, height*0.5f, 0f, 0f, width/2f, 0f); cubicTo(width.toFloat(), 0f, width.toFloat(), height*0.5f, width/2f, height.toFloat()); close() }
                    if (isBooked) { paint.color = AndroidColor.WHITE; paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f * density; canvas.drawPath(pinPath, paint); paint.style = Paint.Style.FILL }
                    paint.color = AndroidColor.argb(40, 0, 0, 0); canvas.save(); canvas.translate(0f, 3 * density); canvas.drawPath(pinPath, paint); canvas.restore()
                    val primaryColor = when { status == "Maintenance" -> "#FF9800".toColorInt(); isRented -> "#9C27B0".toColorInt(); status == "Open" -> "#4CAF50".toColorInt(); status == "Closed" -> "#F44336".toColorInt(); else -> "#4CAF50".toColorInt() }
                    val secondaryColor = when { status == "Maintenance" -> "#E65100".toColorInt(); isRented -> "#7B1FA2".toColorInt(); status == "Open" -> "#2E7D32".toColorInt(); status == "Closed" -> "#D32F2F".toColorInt(); else -> "#2E7D32".toColorInt() }
                    paint.color = primaryColor; canvas.drawPath(pinPath, paint)
                    paint.color = AndroidColor.WHITE; val circleCenterY = width/2f; canvas.drawCircle(width/2f, circleCenterY, width*0.38f, paint)
                    paint.color = secondaryColor; paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.2f * density; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
                    val wheelR = 3.5f * density; val wheelY = circleCenterY + (3f * density); val wheelOffset = 6.5f * density
                    canvas.drawCircle(width/2f - wheelOffset + wheelR, wheelY, wheelR, paint); canvas.drawCircle(width/2f + wheelOffset - wheelR, wheelY, wheelR, paint)
                    canvas.drawPath(Path().apply { moveTo(width/2f - wheelOffset + wheelR, wheelY); lineTo(width/2f - 1f * density, circleCenterY - 3f * density); lineTo(width/2f + 3f * density, circleCenterY - 3f * density); lineTo(width/2f + wheelOffset - wheelR, wheelY); moveTo(width/2f - 3f * density, wheelY); lineTo(width/2f + 1f * density, circleCenterY - 1f * density) }, paint)
                    paint.strokeWidth = 2.8f * density; canvas.drawLine(width/2f - 2.5f * density, circleCenterY - 3.5f * density, width/2f + 0.5f * density, circleCenterY - 3.5f * density, paint); canvas.drawLine(width/2f + 2.5f * density, circleCenterY - 3.5f * density, width/2f + 5f * density, circleCenterY - 5f * density, paint)
                    return bitmap.toDrawable(context.resources)
                }
                val markerIconCache = remember { mutableMapOf<Int, BitmapDrawable>() }
                fun getCachedMarkerIcon(status: String, isRented: Boolean, isBooked: Boolean = false): BitmapDrawable {
                    val key = status.hashCode() + (if (isRented) 1 else 0) * 31 + (if (isBooked) 1 else 0) * 31 * 31
                    return markerIconCache.getOrPut(key) { createMarkerIcon(status, isRented, isBooked) }
                }
                val stationNames = remember(filteredLocations) { filteredLocations.map { it.name }.toSet() }
                LaunchedEffect(isRenting, rentedStationName, isBooked, bookedStationName, filteredLocations) { 
                    val currentMarkers = mapView.overlays.filterIsInstance<Marker>()
                    currentMarkers.forEach { marker ->
                        val station = filteredLocations.find { it.name == marker.title }
                        if (station != null) marker.icon = getCachedMarkerIcon(station.status, isRenting && station.name == rentedStationName, isBooked && station.name == bookedStationName)
                    }
                    mapView.invalidate() 
                }
                LaunchedEffect(mapView, filteredLocations) {
                    mapView.apply {
                        setTileSource(TileSourceFactory.MAPNIK); setMultiTouchControls(true)
                        if (overlays.isEmpty()) { controller.setZoom(12.5); controller.setCenter(GeoPoint(23.7771, 90.3994)) }
                        zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                        overlays.removeAll { it is Marker && it.title !in stationNames }
                        val existingMarkerNames = overlays.filterIsInstance<Marker>().map { it.title }.toSet()
                        filteredLocations.forEach { station ->
                            if (station.name !in existingMarkerNames) {
                                overlays.add(Marker(this).apply {
                                    position = GeoPoint(station.latitude, station.longitude); setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM); title = station.name
                                    icon = getCachedMarkerIcon(station.status, isRenting && station.name == rentedStationName, isBooked && station.name == bookedStationName); infoWindow = null
                                    setOnMarkerClickListener { m, _ -> selectedCycle = station; controller.animateTo(m.position); true }
                                })
                            }
                        }
                        if (overlays.none { it is org.osmdroid.views.overlay.MapEventsOverlay }) {
                            overlays.add(0, org.osmdroid.views.overlay.MapEventsOverlay(object : org.osmdroid.events.MapEventsReceiver {
                                override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean { if (!isMapExpanded) isMapExpanded = true else selectedCycle = null; return true }
                                override fun longPressHelper(p: GeoPoint?): Boolean = false
                            }))
                        }
                        if (!overlays.contains(activeRouteOverlay)) overlays.add(activeRouteOverlay)
                        if (!overlays.contains(myLocationOverlay)) overlays.add(myLocationOverlay)
                        invalidate()
                    }
                }
                DisposableEffect(lifecycleOwner) { val observer = LifecycleEventObserver { _, event -> when (event) { Lifecycle.Event.ON_RESUME -> { mapView.onResume(); myLocationOverlay.enableMyLocation() }; Lifecycle.Event.ON_PAUSE -> { myLocationOverlay.disableMyLocation(); mapView.onPause() }; Lifecycle.Event.ON_DESTROY -> mapView.onDetach(); else -> {} } }; lifecycleOwner.lifecycle.addObserver(observer); onDispose { lifecycleOwner.lifecycle.removeObserver(observer) } }

                if (showGpsWarning && isRenting) {
                    AlertDialog(
                        onDismissRequest = { }, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                        icon = { Icon(imageVector = Icons.Default.LocationOff, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp)) },
                        title = { Text(text = when (gpsLostCount) { 2 -> "GPS SIGNAL LOST (2nd Warning)"; 3 -> "GPS SIGNAL LOST (Final Warning!)"; 4 -> "GPS SIGNAL LOST (Immediate Penalty)"; else -> if (gpsLostCount > 4) "GPS SIGNAL LOST (Multiple Violations)" else "GPS SIGNAL LOST" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.error) },
                        text = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(text = if (gpsWarningTimer > 0) {
                                    val staticTime = when(gpsLostCount) { 1 -> "30 seconds"; 2 -> "15 seconds"; 3 -> "5 seconds"; else -> "immediately" }
                                    "GPS signal is required for tracking. Please restore GPS within $staticTime to avoid a penalty."
                                } else if (gpsLostCount >= 4) "Immediate penalty applied for multiple GPS violations. Fine is increasing every second until GPS is restored."
                                else "Grace period expired. A fine has been applied and is increasing. Please restore GPS immediately to continue your ride.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                                if (gpsWarningTimer > 0) Surface(color = MaterialTheme.colorScheme.errorContainer, shape = CircleShape) { Text(text = "${gpsWarningTimer}s", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.error) }
                                else Text(text = "FINE APPLIED: ${String.format("%.2f", totalFine)} BDT", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).build()
                                    val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest); val client: SettingsClient = LocationServices.getSettingsClient(context); val task = client.checkLocationSettings(builder.build())
                                    task.addOnFailureListener { exception -> if (exception is ResolvableApiException) { try { val intentSenderRequest = IntentSenderRequest.Builder(exception.resolution.intentSender).build(); (context as? MainActivity)?.locationRequestLauncher?.launch(intentSenderRequest) } catch (sendEx: Exception) { } } }
                                    myLocationOverlay.enableMyLocation(); myLocationOverlay.enableFollowLocation(); myLocationOverlay.myLocation?.let { mapView.controller.animateTo(it) }
                                },
                                modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) { Icon(Icons.Default.MyLocation, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("RESUME GPS") }
                        }
                    )
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = selectedCycle != null, enter = slideInVertically(initialOffsetY = { it }) + fadeIn(), exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(), modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                ) {
                    selectedCycle?.let { cycle ->
                        ElevatedCard(modifier = Modifier.fillMaxWidth().height(200.dp), shape = MaterialTheme.shapes.extraLarge) {
                            Column(modifier = Modifier.padding(20.dp).fillMaxSize()) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        if (isRenting && cycle.name == rentedStationName) Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.padding(bottom = 4.dp)) { Text(text = "ACTIVE RENTAL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
                                        Text(text = cycle.name, style = MaterialTheme.typography.titleLarge, fontWeight = if (isRenting && cycle.name == rentedStationName) FontWeight.Black else FontWeight.Bold, color = if (isRenting && cycle.name == rentedStationName) MaterialTheme.colorScheme.primary else Color.Unspecified)
                                        Surface(color = when(cycle.status) { "Open" -> Color(0xFF4CAF50).copy(alpha = 0.15f); "Maintenance" -> Color(0xFFFF9800).copy(alpha = 0.15f); "Closed" -> Color(0xFFF44336).copy(alpha = 0.15f); "Permanently Closed" -> Color.Gray.copy(alpha = 0.15f); else -> Color.Gray.copy(alpha = 0.15f) }, shape = MaterialTheme.shapes.small, modifier = Modifier.padding(bottom = 4.dp)) {
                                            Text(text = cycle.status.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = when(cycle.status) { "Open" -> Color(0xFF2E7D32); "Maintenance" -> Color(0xFFE65100); "Closed" -> Color(0xFFD32F2F); "Permanently Closed" -> Color.DarkGray; else -> Color.DarkGray }, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) { Text(text = "2 BDT/min", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold) }
                                        Text(text = "${cycle.availableCycles}/${cycle.totalCycles} (Available/Total)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp, end = 4.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                val distKm = myLocationOverlay.myLocation?.let { GeoPoint(cycle.latitude, cycle.longitude).distanceToAsDouble(it) / 1000.0 }; Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) { InfoChip(Icons.Default.Navigation, if (distKm != null) String.format(Locale.getDefault(), "%.1f km", distKm) else "-- km"); InfoChip(Icons.Default.AccessTime, if (distKm != null) "${(distKm * 12).toInt()} mins walk" else "-- mins"); InfoChip(Icons.Default.ElectricBike, "Standard") }
                                Spacer(modifier = Modifier.weight(1f))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val isRentingElsewhere = isRenting && cycle.name != rentedStationName
                                    val isBookedHere = isBooked && cycle.name == bookedStationName
                                    val isBookedElsewhere = isBooked && cycle.name != bookedStationName
                                    val canRent = (cycle.status == "Open" && !isRentingElsewhere && !isBookedElsewhere)
                                    val canBook = (cycle.status == "Open" && !isRenting && !isBooked)
                                    if (isRenting) {
                                        Button(onClick = { showQrScanner = true }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.large, contentPadding = PaddingValues(horizontal = 4.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant, disabledContentColor = MaterialTheme.colorScheme.outline)) {
                                            Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(4.dp))
                                            Text(text = run { val mins = elapsedTime / 60; val secs = elapsedTime % 60; String.format(Locale.getDefault(), "Return at %s (%02d:%02d)", cycle.name, mins, secs) }, style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), maxLines = 1, softWrap = false)
                                        }
                                    } else {
                                        Button(onClick = { showRentRuleDialog = true }, enabled = canRent && (cycle.availableCycles > 0 || isBookedHere), modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.large, contentPadding = PaddingValues(horizontal = 4.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant, disabledContentColor = MaterialTheme.colorScheme.outline)) {
                                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(4.dp))
                                            Text(text = if (isRentingElsewhere) "At $rentedStationName" else if (isBookedElsewhere) "At $bookedStationName" else if (isBookedHere) "Scan to Unlock" else "Scan to Rent", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), maxLines = 1, softWrap = false)
                                        }
                                        Button(onClick = { if (isBookedHere) showBookingCancelDialog = true else { if (userProfile.balance < 60.0) android.widget.Toast.makeText(context, "Min 60 BDT required to book (30 min charge).", android.widget.Toast.LENGTH_SHORT).show() else { pendingBookingCycle = cycle; showBookingRuleDialog = true } } }, enabled = (isBookedHere || (canBook && cycle.availableCycles > 0)) && !isRenting, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.large, contentPadding = PaddingValues(horizontal = 4.dp), colors = ButtonDefaults.buttonColors(containerColor = if (isBookedHere) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary, disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant, disabledContentColor = MaterialTheme.colorScheme.outline)) {
                                            Icon(if (isBookedHere) Icons.Default.BookmarkRemove else Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(4.dp))
                                            Text(text = if (isBookedHere) { val mins = remainingBookingTime / 60; val secs = remainingBookingTime % 60; String.format(Locale.getDefault(), "Cancel (%02d:%02d)", mins, secs) } else if (isBookedElsewhere) "At $bookedStationName" else "Book", style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), maxLines = 1, softWrap = false)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                if (showBookingCancelDialog) {
                    val currentTime = System.currentTimeMillis(); val elapsedTotalSecs = if (bookingStartTime != 0L) (currentTime - bookingStartTime) / 1000 else 0L
                    val dialogText = when { elapsedTotalSecs < 0 -> "Are you sure? A 50% refund (30 BDT) will be issued due to system clock reset."; elapsedTotalSecs < 60 -> "Are you sure? Full refund will be issued."; elapsedTotalSecs < 15 * 60 -> "Are you sure? 50% (30 BDT) of the booking fee will be refunded."; else -> "Are you sure? Balance is not refundable as you exceeded the 15-minute limit." }
                    AlertDialog(onDismissRequest = { showBookingCancelDialog = false }, title = { Text("Cancel Booking?") }, text = { Text(dialogText) }, confirmButton = { Button(onClick = {
                                    showBookingCancelDialog = false; val finalTime = System.currentTimeMillis(); val finalElapsed = if (bookingStartTime != 0L) (finalTime - bookingStartTime) / 1000 else 0L
                                    val finalRefund = when { finalElapsed < 0 -> 30.0; finalElapsed < 60 -> 60.0; finalElapsed < 15 * 60 -> 30.0; else -> 0.0 }
                                    val stationToUpdate = bookedStationName; isBooked = false; bookedStationName = null
                                    context.startService(Intent(context, TrackingService::class.java).apply { action = TrackingService.ACTION_STOP })
                                    scope.launch {
                                        val prefsData = context.dataStore.data.first()
                                        prefsData[stringPreferencesKey("active_booking_id")]?.let { bId -> database.bookingDao().getBookingById(bId)?.let { booking -> database.bookingDao().updateBooking(booking.copy(status = "CANCELLED", refundAmount = finalRefund)) } }
                                        if (finalRefund > 0.0) { val dbUser = database.userProfileDao().getUserByPhone(userProfile.phone); dbUser?.let { database.userProfileDao().updateProfile(it.copy(balance = it.balance + finalRefund)) } }
                                        context.dataStore.edit { prefs -> prefs[booleanPreferencesKey("is_booked")] = false; prefs[stringPreferencesKey("booked_station_name")] = ""; prefs[longPreferencesKey("booking_time")] = 0L; prefs[stringPreferencesKey("active_booking_id")] = "" }
                                        stationToUpdate?.let { name -> database.stationDao().getStationByName(name)?.let { station -> database.stationDao().updateStation(station.copy(availableCycles = station.availableCycles + 1)) } }
                                    }
                                    android.widget.Toast.makeText(context, when { finalElapsed < 0 -> "Booking cancelled. 30 BDT refunded (Clock Reset)."; finalElapsed < 60 -> "Booking cancelled. Full refund issued."; finalElapsed < 15 * 60 -> "Booking cancelled. 30 BDT refunded."; else -> "Booking cancelled. No refund." }, android.widget.Toast.LENGTH_SHORT).show()
                                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Yes! i want cancel") } }, dismissButton = { TextButton(onClick = { showBookingCancelDialog = false }) { Text("No") } })
                }

                Column(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).animateContentSize(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnimatedVisibility(visible = (isGpsLost && !showGpsConnected) || showGpsConnected, enter = fadeIn() + expandVertically(expandFrom = Alignment.Top), exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)) {
                        val isConnected = showGpsConnected
                        Surface(onClick = { if (isGpsLost && !isConnected) { val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).build(); val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest); val client: SettingsClient = LocationServices.getSettingsClient(context); val task = client.checkLocationSettings(builder.build())
                                    task.addOnFailureListener { exception -> if (exception is ResolvableApiException) { try { val intentSenderRequest = IntentSenderRequest.Builder(exception.resolution.intentSender).build(); (context as? MainActivity)?.locationRequestLauncher?.launch(intentSenderRequest) } catch (sendEx: Exception) { } } }
                                    myLocationOverlay.enableMyLocation(); myLocationOverlay.enableFollowLocation(); myLocationOverlay.myLocation?.let { mapView.controller.animateTo(it) } } }, shape = CircleShape, color = if (isGpsLost) MaterialTheme.colorScheme.errorContainer else Color(0xFF4CAF50), contentColor = if (isGpsLost) MaterialTheme.colorScheme.error else Color.White, shadowElevation = 4.dp) { Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(imageVector = if (isGpsLost) Icons.Default.LocationOff else Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp)); Text(text = if (isGpsLost) "GPS Reconnecting..." else "GPS Connected!", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) } }
                    }
                    FloatingActionButton(onClick = { val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).build(); val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest); val client: SettingsClient = LocationServices.getSettingsClient(context); val task = client.checkLocationSettings(builder.build())
                            task.addOnFailureListener { exception -> if (exception is ResolvableApiException) { try { val intentSenderRequest = IntentSenderRequest.Builder(exception.resolution.intentSender).build(); (context as? MainActivity)?.locationRequestLauncher?.launch(intentSenderRequest) } catch (sendEx: Exception) { } } }
                            myLocationOverlay.enableMyLocation(); myLocationOverlay.enableFollowLocation(); myLocationOverlay.myLocation?.let { mapView.controller.animateTo(it) }; selectedCycle = null }, containerColor = if (isGpsLost) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface, contentColor = if (isGpsLost) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) { Icon(if (isGpsLost) Icons.Default.LocationOff else Icons.Default.MyLocation, contentDescription = "Center on my location") }
                    AnimatedVisibility(visible = !isMapExpanded) { FloatingActionButton(onClick = { isMapExpanded = true }, containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.primary) { Icon(Icons.Default.Fullscreen, contentDescription = "Expand map") } }
                    AnimatedVisibility(visible = isMapExpanded) { FloatingActionButton(onClick = { isMapExpanded = false }, containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.primary) { Icon(Icons.Default.FullscreenExit, contentDescription = "Exit full screen") } }
                    AnimatedVisibility(visible = isBooked) { Surface(onClick = { bookedStationName?.let { name -> filteredLocations.find { it.name == name }?.let { station -> selectedCycle = station; mapView.controller.animateTo(GeoPoint(station.latitude, station.longitude)) } } }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f), tonalElevation = 4.dp, shadowElevation = 4.dp) { Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Column(horizontalAlignment = Alignment.End) { Text(text = bookedStationName ?: "", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { Icon(Icons.Default.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp)); Text(text = String.format(Locale.US, "%02d:%02d", remainingBookingTime / 60, remainingBookingTime % 60), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp)) } } }
                }
            } else { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize().padding(16.dp)) { Text("Location permission required to show the map."); Spacer(modifier = Modifier.height(8.dp)); Button(onClick = { locationPermissionsState.launchMultiplePermissionRequest() }) { Text("Request permissions") } } }
        }

        if (showRentRuleDialog) {
            AlertDialog(onDismissRequest = { showRentRuleDialog = false }, title = { Text("Rental Rules & Safety") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { val rules = listOf("Ride safely.", "Avoid stunts.", "Return the cycle without damaging.", "If the balance reaches 0 then you will be charged 2.50Tk/min as a fine during ride.")
                        rules.forEach { rule -> Row(verticalAlignment = Alignment.Top) { Text("• ", fontWeight = FontWeight.Black); Text(rule, style = MaterialTheme.typography.bodyMedium) } } } }, confirmButton = { Button(onClick = { val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager; val isGpsEnabled = try { locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) } catch (e: Exception) { false }
                        if (!isGpsEnabled) { val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000).build(); val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest); val client: SettingsClient = LocationServices.getSettingsClient(context); val task = client.checkLocationSettings(builder.build())
                            task.addOnFailureListener { exception -> if (exception is ResolvableApiException) { try { val intentSenderRequest = IntentSenderRequest.Builder(exception.resolution.intentSender).build(); (context as? MainActivity)?.locationRequestLauncher?.launch(intentSenderRequest) } catch (sendEx: Exception) { } } }
                            android.widget.Toast.makeText(context, "Please turn on GPS to start renting", android.widget.Toast.LENGTH_LONG).show() } else { showRentRuleDialog = false; showQrScanner = true } }) { Text("I Understand") } }, dismissButton = { TextButton(onClick = { showRentRuleDialog = false }) { Text("Cancel") } })
        }

        if (showBookingRuleDialog && pendingBookingCycle != null) {
            AlertDialog(onDismissRequest = { showBookingRuleDialog = false }, title = { Text("Booking Rules & Confirmation") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("You are booking a cycle at ${pendingBookingCycle!!.name}.", fontWeight = FontWeight.Bold)
                        val bulletPoints = listOf("Booking Fee: 60 BDT (Covers first 30 mins).", "Cancellation within 1 min: 100% Refund.", "Cancellation within 15 mins: 50% Refund (30 BDT).", "Cancellation after 15 mins: No Refund.", "After 30 mins: Charged at 2 BDT/min (Standard rate).")
                        bulletPoints.forEach { point -> Row(verticalAlignment = Alignment.Top) { Text("• ", fontWeight = FontWeight.Black); Text(point, style = MaterialTheme.typography.bodyMedium) } } } }, confirmButton = { Button(onClick = { showBookingRuleDialog = false; val cycle = pendingBookingCycle!!; val bt = System.currentTimeMillis(); val bId = "BOK$bt"; isBooked = true; bookedStationName = cycle.name; bookingStartTime = bt
                            scope.launch { val dbUser = database.userProfileDao().getUserByPhone(userProfile.phone); dbUser?.let { database.userProfileDao().updateProfile(it.copy(balance = it.balance - 60.0)) }
                                database.bookingDao().insertBooking(com.example.sdp3.database.Booking(id = bId, userPhone = userProfile.phone, stationName = cycle.name, startTime = bt, date = java.text.SimpleDateFormat("dd MMM, yyyy HH:mm", Locale.getDefault()).format(java.util.Date(bt)), status = "PENDING", amountPaid = 60.0))
                                context.dataStore.edit { prefs -> prefs[booleanPreferencesKey("is_booked")] = true; prefs[stringPreferencesKey("booked_station_name")] = cycle.name; prefs[longPreferencesKey("booking_time")] = bt; prefs[stringPreferencesKey("active_booking_id")] = bId }
                                database.stationDao().getStationByName(cycle.name)?.let { station -> database.stationDao().updateStation(station.copy(availableCycles = (station.availableCycles - 1).coerceAtLeast(0))) }
                                val intent = Intent(context, TrackingService::class.java).apply { action = TrackingService.ACTION_START_BOOKING }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent) }
                            android.widget.Toast.makeText(context, "Booked successfully!", android.widget.Toast.LENGTH_SHORT).show() }) { Text("I Agree & Book") } }, dismissButton = { TextButton(onClick = { showBookingRuleDialog = false }) { Text("Cancel") } })
        }

        AnimatedVisibility(visible = showQrScanner, enter = fadeIn(), exit = fadeOut()) {
            val cameraPermission = locationPermissionsState.permissions.find { it.permission == android.Manifest.permission.CAMERA }
            if (cameraPermission?.status?.isGranted == true) {
                var isProcessingScan by remember { mutableStateOf(false) }
                QRScannerView(onCodeScanned = { _ -> if (showQrScanner && !isProcessingScan) { isProcessingScan = true; selectedCycle?.let { cycle -> if (isRenting) { if (returnCycle(cycle)) { showQrScanner = false; android.widget.Toast.makeText(context, "Cycle Returned & Locked!", android.widget.Toast.LENGTH_SHORT).show() } else { showQrScanner = false } } else { showQrScanner = false; startRenting(cycle); android.widget.Toast.makeText(context, "Cycle Unlocked!", android.widget.Toast.LENGTH_SHORT).show() } } } }, onClose = { showQrScanner = false })
            } else { SideEffect { locationPermissionsState.launchMultiplePermissionRequest() }; Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("Camera permission is required", color = Color.White); Spacer(modifier = Modifier.height(16.dp)); Button(onClick = { locationPermissionsState.launchMultiplePermissionRequest() }) { Text("Request Permission") } } } }
        }
    }
}

@Composable
fun InfoChip(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline); Spacer(modifier = Modifier.width(4.dp)); Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
}

@OptIn(ExperimentalGetImage::class)
@Composable
fun QRScannerView(
    onCodeScanned: (String) -> Unit,
    onClose: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val barcodeScanner = BarcodeScanning.getClient()
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(cameraExecutor) { imageProxy ->
                                @OptIn(androidx.camera.core.ExperimentalGetImage::class)
                                val mediaImage = imageProxy.image
                                if (mediaImage != null) {
                                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                    barcodeScanner.process(image)
                                        .addOnSuccessListener { barcodes ->
                                            if (barcodes.isNotEmpty()) {
                                                onCodeScanned(barcodes[0].rawValue ?: "")
                                            }
                                        }
                                        .addOnCompleteListener { imageProxy.close() }
                                } else {
                                    imageProxy.close()
                                }
                            }
                        }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Scan Cycle QR", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            Box(
                modifier = Modifier
                    .size(250.dp)
                    .border(2.dp, Color.White, MaterialTheme.shapes.medium)
                    .background(Color.Transparent)
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Position the QR code inside the frame", color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Manual Entry / Close")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
