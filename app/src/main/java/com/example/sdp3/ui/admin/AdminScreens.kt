package com.example.sdp3.ui.admin

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color as AndroidColor
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.sdp3.AdminProfile
import com.example.sdp3.Trip
import com.example.sdp3.Booking
import com.example.sdp3.database.AppDatabase
import com.example.sdp3.database.Station
import com.example.sdp3.database.UserProfile
import com.example.sdp3.ui.history.AdminReportCard
import com.example.sdp3.ui.history.BookingHistoryCard
import com.example.sdp3.ui.history.TripHistoryCard
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.util.*

@Composable
fun AdminDashboard(database: AppDatabase) {
    val allUsers by database.userProfileDao().getAllProfilesFlow().collectAsState(initial = emptyList())
    val allStations by database.stationDao().getAllStationsFlow().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showTripLog by remember { mutableStateOf(false) }
    val allTripsFlow = remember { database.tripDao().getAllTrips() }
    val allTrips by allTripsFlow.collectAsState(initial = emptyList())
    val allBookings by database.bookingDao().getAllBookings().collectAsState(initial = emptyList())

    val totalRevenue = remember(allBookings, allTrips) {
        allBookings.sumOf { it.amountPaid - it.refundAmount } + 
        allTrips.sumOf { (if (it.wasBooked) maxOf(0.0, it.cost - 60.0) else it.cost) + it.fine }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 24.dp, start = 20.dp, end = 20.dp, bottom = 8.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {


        // System Stats Grid
        Text(
            text = "System Overview",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Total Users",
                value = allUsers.size.toString(),
                icon = Icons.Default.People,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Total Stations",
                value = allStations.size.toString(),
                icon = Icons.Default.LocationOn,
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Total Revenue",
                value = String.format(Locale.US, "%.1f BDT", totalRevenue),
                icon = Icons.Default.Payments,
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Total cycles rented",
                value = allTrips.size.toString(),
                icon = Icons.AutoMirrored.Filled.DirectionsBike,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // User Management Section
        SectionHeader(title = "User Management")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            if (allUsers.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No users found", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                var showAllUsersDialog by remember { mutableStateOf(false) }
                allUsers.take(3).forEach { user ->
                    UserListItem(user, database, scope)
                }
                if (allUsers.size > 3) {
                    TextButton(
                        onClick = { showAllUsersDialog = true },
                        modifier = Modifier.align(Alignment.End).padding(horizontal = 8.dp)
                    ) {
                        Text("View All")
                    }
                }

                if (showAllUsersDialog) {
                    AlertDialog(
                        onDismissRequest = { showAllUsersDialog = false },
                        title = { Text("All Registered Users") },
                        text = {
                            Box(modifier = Modifier.fillMaxWidth().height(450.dp)) {
                                LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(allUsers, key = { it.phone }) { user ->
                                    UserListItem(user, database, scope)
                                }
                                }
                            }
                        },
                        confirmButton = {
                            Button(onClick = { showAllUsersDialog = false }) { Text("Close") }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Station Management Section
        SectionHeader(title = "Station Management")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            var showAllStationsDialog by remember { mutableStateOf(false) }
            allStations.take(3).forEach { station ->
                StationAdminItem(station, database, scope)
            }
            if (allStations.size > 3) {
                TextButton(
                    onClick = { showAllStationsDialog = true },
                    modifier = Modifier.align(Alignment.End).padding(horizontal = 8.dp)
                ) {
                    Text("Manage All")
                }
            }

            if (showAllStationsDialog) {
                AlertDialog(
                    onDismissRequest = { showAllStationsDialog = false },
                    title = { Text("All Stations Management") },
                    text = {
                        Box(modifier = Modifier.fillMaxWidth().height(450.dp)) {
                            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(allStations, key = { it.id }) { station ->
                                    StationAdminItem(station, database, scope)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = { showAllStationsDialog = false }) { Text("Close") }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Spacer(modifier = Modifier.height(32.dp))

        // Trip Logs Button
        Button(
            onClick = { showTripLog = true },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("System Analytics & Logs", style = MaterialTheme.typography.titleSmall)
        }

        Spacer(modifier = Modifier.height(12.dp))

        var showBalanceRequests by remember { mutableStateOf(false) }
        val pendingTopUps by database.topUpDao().getPendingRequests().collectAsState(initial = emptyList())

        // Balance Requests Button
        Button(
            onClick = { showBalanceRequests = true },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Balance Requests", style = MaterialTheme.typography.titleSmall)
            if (pendingTopUps.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Badge(containerColor = MaterialTheme.colorScheme.error) {
                    Text(pendingTopUps.size.toString())
                }
            }
        }

        if (showBalanceRequests) {
            AlertDialog(
                onDismissRequest = { showBalanceRequests = false },
                title = { Text("Pending Balance Requests") },
                text = {
                    Box(modifier = Modifier.fillMaxWidth().height(500.dp)) {
                        if (pendingTopUps.isEmpty()) {
                            Text("No pending balance requests", modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.outline)
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(pendingTopUps, key = { it.id }) { request ->
                                    AdminTopUpCard(
                                        request = request,
                                        onApprove = { adjustedAmount ->
                                            scope.launch {
                                                val user = database.userProfileDao().getUserByPhone(request.userPhone)
                                                user?.let { u ->
                                                    // Charge fine before adding balance
                                                    val remainingAmount = adjustedAmount - u.pendingFine
                                                    val finalBalance = (u.balance + maxOf(0.0, remainingAmount))
                                                    val newPendingFine = if (remainingAmount < 0) kotlin.math.abs(remainingAmount) else 0.0
                                                    
                                                    database.userProfileDao().updateProfile(u.copy(
                                                        balance = finalBalance,
                                                        pendingFine = newPendingFine
                                                    ))
                                                    database.topUpDao().updateRequest(request.copy(status = "APPROVED", amount = adjustedAmount))
                                                }
                                            }
                                        },
                                        onReject = { reason ->
                                            scope.launch {
                                                database.topUpDao().updateRequest(request.copy(status = "REJECTED", rejectionReason = reason))
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { showBalanceRequests = false }) { Text("Close") }
                }
            )
        }

        if (showTripLog) {
            var selectedLogTab by remember { mutableStateOf("Trips") }
            val allBookings by database.bookingDao().getAllBookings().collectAsState(initial = emptyList())
            val allReports by database.reportDao().getAllReports().collectAsState(initial = emptyList())

            AlertDialog(
                onDismissRequest = { showTripLog = false },
                title = {
                    Column {
                        Text("System Activity Logs")
                        Spacer(modifier = Modifier.height(8.dp))
                        TabRow(
                            selectedTabIndex = when(selectedLogTab) { "Trips" -> 0; "Bookings" -> 1; "Reports" -> 2; else -> 3 },
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.primary,
                            divider = {}
                        ) {
                            Tab(selected = selectedLogTab == "Trips", onClick = { selectedLogTab = "Trips" }) {
                                Text("Trips", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelLarge)
                            }
                            Tab(selected = selectedLogTab == "Bookings", onClick = { selectedLogTab = "Bookings" }) {
                                Text("Bookings", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelLarge)
                            }
                            Tab(selected = selectedLogTab == "Reports", onClick = { selectedLogTab = "Reports" }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Reports", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelLarge)
                                    val pendingCount = allReports.count { it.status == "PENDING" }
                                    if (pendingCount > 0) {
                                        Badge { Text(pendingCount.toString()) }
                                    }
                                }
                            }
                        }
                    }
                },
                text = {
                    Box(modifier = Modifier.fillMaxWidth().height(500.dp)) {
                        when (selectedLogTab) {
                            "Trips" -> {
                                if (allTrips.isEmpty()) {
                                    Text("No trips recorded yet.", modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.outline)
                                } else {
                                    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(allTrips, key = { it.id }) { trip ->
                                            TripHistoryCard(
                                                Trip(
                                                    id = trip.id,
                                                    userPhone = trip.userPhone,
                                                    userName = trip.userName,
                                                    startStation = trip.startStation,
                                                    endStation = trip.endStation,
                                                    stationLocation = trip.stationLocation,
                                                    durationSeconds = trip.durationSeconds,
                                                    cost = trip.cost,
                                                    date = trip.date,
                                                    startLat = trip.startLat,
                                                    startLon = trip.startLon,
                                                    endLat = trip.endLat,
                                                    endLon = trip.endLon,
                                                    distanceKm = trip.distanceKm,
                                                    routeJson = trip.routeJson,
                                                    fine = trip.fine,
                                                    wasBooked = trip.wasBooked
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            "Bookings" -> {
                                if (allBookings.isEmpty()) {
                                    Text("No bookings recorded yet.", modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.outline)
                                } else {
                                    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(allBookings, key = { it.id }) { booking ->
                                            BookingHistoryCard(
                                                Booking(
                                                    id = booking.id,
                                                    userPhone = booking.userPhone,
                                                    stationName = booking.stationName,
                                                    startTime = booking.startTime,
                                                    date = booking.date,
                                                    status = booking.status,
                                                    amountPaid = booking.amountPaid,
                                                    refundAmount = booking.refundAmount
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            "Reports" -> {
                                if (allReports.isEmpty()) {
                                    Text("No reports submitted yet.", modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.outline)
                                } else {
                                    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        items(allReports, key = { it.id }) { report ->
                                            AdminReportCard(report, onSolve = {
                                                scope.launch {
                                                    database.reportDao().updateReport(report.copy(status = "SOLVED"))
                                                }
                                            })
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { showTripLog = false }) { Text("Done") }
                }
            )
        }
        
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun StatCard(modifier: Modifier = Modifier, title: String, value: String, icon: ImageVector, containerColor: Color) {
    val contentColor = contentColorFor(containerColor)
    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = contentColor)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(text = title, style = MaterialTheme.typography.labelMedium, color = contentColor.copy(alpha = 0.8f))
        }
    }
}

@Composable
fun SectionHeader(title: String, actionText: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (actionText != null) {
            Text(text = actionText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun UserListItem(user: UserProfile, database: AppDatabase, scope: kotlinx.coroutines.CoroutineScope) {
    var showBalanceDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    val userTrips by database.tripDao().getTripsByUser(user.phone).collectAsState(initial = emptyList())
    val userBookings by database.bookingDao().getBookingsByUser(user.phone).collectAsState(initial = emptyList())
    
    val totalSuccessful = userTrips.size
    val totalCancelledCount = userBookings.count { it.status == "CANCELLED" || it.status == "EXPIRED" }
    val totalAttempts = totalSuccessful + totalCancelledCount
    val isThresholdReached = totalAttempts >= 5
    val userSuccessRate = if (isThresholdReached) (totalSuccessful.toDouble() / totalAttempts) * 100.0 else 0.0
    val successColor = when {
        !isThresholdReached -> MaterialTheme.colorScheme.outline
        userSuccessRate >= 80 -> Color(0xFF4CAF50)
        userSuccessRate >= 50 -> Color(0xFFFFB300)
        else -> Color(0xFFF44336)
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(user.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Text(user.phone, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Text(
                            text = "Success Rate: " + if (isThresholdReached) String.format(Locale.US, "%.0f%%", userSuccessRate) else "New",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = successColor
                        )
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = CircleShape
                ) {
                    Text(
                        text = String.format(Locale.US, "%.2f BDT", user.balance),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                FilledTonalIconButton(onClick = { showBalanceDialog = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Add Balance", modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.size(32.dp),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete User", modifier = Modifier.size(16.dp))
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete User") },
            text = { Text("Are you sure you want to delete user ${user.name}? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch { database.userProfileDao().deleteProfile(user) }
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showBalanceDialog) {
        var amountText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showBalanceDialog = false },
            title = { Text("Update Balance for ${user.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Enter amount to add or subtract (use negative for subtraction):")
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { if (it.isEmpty() || it == "-" || it.toDoubleOrNull() != null) amountText = it },
                        label = { Text("Amount (BDT)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        listOf(50, 100, 200, 500).forEach { amount ->
                            SuggestionChip(
                                onClick = { amountText = amount.toString() },
                                label = { Text("+$amount") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = amountText.toDoubleOrNull() ?: 0.0
                        scope.launch {
                            val newBalance = (user.balance + amount).coerceAtLeast(0.0)
                            val extraFine = if (user.balance + amount < 0) {
                                kotlin.math.abs(user.balance + amount)
                            } else 0.0
                            
                            val updatedUser = user.copy(
                                balance = newBalance,
                                pendingFine = user.pendingFine + extraFine
                            )
                            database.userProfileDao().insertProfile(updatedUser)
                        }
                        showBalanceDialog = false
                    }
                ) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showBalanceDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StationAdminItem(station: Station, database: AppDatabase, scope: kotlinx.coroutines.CoroutineScope) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    StationAdminCard(
        station = station,
        onEdit = { showEditDialog = true },
        onDelete = { showDeleteConfirm = true }
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Station") },
            text = { Text("Are you sure you want to delete station '${station.name}'? This will remove it from the system entirely.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch { database.stationDao().deleteStation(station.id) }
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showEditDialog) {
        var editName by remember { mutableStateOf(station.name) }
        var editTotal by remember { mutableStateOf(station.totalCycles.toString()) }
        var editAvailable by remember { mutableStateOf(station.availableCycles.toString()) }
        var editStatus by remember { mutableStateOf(station.status) }
        val statusOptions = listOf("Open", "Closed", "Maintenance", "Permanently Closed")

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Station Details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("Station Name") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = editTotal, onValueChange = { editTotal = it }, label = { Text("Total") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(value = editAvailable, onValueChange = { editAvailable = it }, label = { Text("Available") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                    Text("Operating Status", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        statusOptions.forEach { status ->
                            FilterChip(
                                selected = editStatus == status,
                                onClick = { editStatus = status },
                                label = { Text(status, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        database.stationDao().updateStation(station.copy(
                            name = editName,
                            totalCycles = editTotal.toIntOrNull() ?: station.totalCycles,
                            availableCycles = editAvailable.toIntOrNull() ?: station.availableCycles,
                            status = editStatus
                        ))
                        showEditDialog = false
                    }
                }) { Text("Save Changes") }
            },
            dismissButton = { TextButton(onClick = { showEditDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun StationAdminCard(station: Station, onEdit: () -> Unit, onDelete: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = station.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(text = "ID: ${station.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                Surface(
                    color = when (station.status) {
                        "Open" -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                        "Maintenance" -> Color(0xFFFF9800).copy(alpha = 0.15f)
                        "Closed" -> Color(0xFFF44336).copy(alpha = 0.15f)
                        "Permanently Closed" -> Color.Gray.copy(alpha = 0.15f)
                        else -> Color.Gray.copy(alpha = 0.15f)
                    },
                    shape = CircleShape
                ) {
                    Text(
                        text = station.status.uppercase(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = when (station.status) {
                            "Open" -> Color(0xFF2E7D32)
                            "Maintenance" -> Color(0xFFE65100)
                            "Closed" -> Color(0xFFD32F2F)
                            "Permanently Closed" -> Color.DarkGray
                            else -> Color.DarkGray
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.DirectionsBike, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Text(text = " ${station.availableCycles}/${station.totalCycles} Cycles", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp))
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                FilledTonalIconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Station", modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp), colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Station", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun AdminMapScreen(adminProfile: AdminProfile, database: AppDatabase) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val allStations by database.stationDao().getAllStationsFlow().collectAsState(initial = emptyList())
    
    var selectedPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var showAddStationDialog by remember { mutableStateOf(false) }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(13.0)
            controller.setCenter(GeoPoint(23.7771, 90.3994))
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY -> mapView.onDetach()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = 24.dp, start = 24.dp, end = 24.dp, bottom = 8.dp)) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AdminPanelSettings,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = "Administrator", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                    Text(text = adminProfile.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.padding(top = 4.dp)) {
                        Text(
                            text = "System Monitor Active",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Live Station Map", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(text = "Long press to add station", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        var selectedStation by remember { mutableStateOf<Station?>(null) }

    val adminMarkerIconCache = remember { mutableMapOf<String, BitmapDrawable>() }
    fun getAdminMarkerIcon(status: String, view: MapView): BitmapDrawable {
        return adminMarkerIconCache.getOrPut(status) {
            val colorStr = when {
                status == "Maintenance" -> "#FF9800"
                status == "Open" -> "#4CAF50"
                status == "Permanently Closed" -> "#9E9E9E"
                else -> "#F44336"
            }

            val density = view.context.resources.displayMetrics.density
            val size = (20 * density).toInt()
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.parseColor(colorStr) }
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
            paint.color = AndroidColor.WHITE
            canvas.drawCircle(size / 2f, size / 2f, size * 0.3f, paint)

            bitmap.toDrawable(view.context.resources)
        }
    }

    val adminStationNames = remember(allStations) { allStations.map { it.name }.toSet() }

    Box(modifier = Modifier.fillMaxWidth().weight(1f).clip(MaterialTheme.shapes.extraLarge)) {
        AndroidView(
            factory = { mapView },
            update = { view ->
                if (view.overlays.none { it is org.osmdroid.views.overlay.MapEventsOverlay }) {
                    view.overlays.add(0, org.osmdroid.views.overlay.MapEventsOverlay(object : org.osmdroid.events.MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                            selectedStation = null
                            return true
                        }
                        override fun longPressHelper(p: GeoPoint?): Boolean {
                            p?.let {
                                selectedPoint = it
                                showAddStationDialog = true
                            }
                            return true
                        }
                    }))
                }

                view.overlays.removeAll { it is Marker && it.title !in adminStationNames }

                allStations.forEach { station ->
                    val existingMarker = view.overlays.filterIsInstance<Marker>().find { it.title == station.name }
                    if (existingMarker != null) {
                        existingMarker.icon = getAdminMarkerIcon(station.status, view)
                        existingMarker.alpha = if (station.status == "Permanently Closed") 0.5f else 1.0f
                    } else {
                        view.overlays.add(Marker(view).apply {
                            position = GeoPoint(station.latitude, station.longitude)
                            title = station.name
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            infoWindow = null

                            icon = getAdminMarkerIcon(station.status, view)

                            if (station.status == "Permanently Closed") {
                                alpha = 0.5f
                            }

                            setOnMarkerClickListener { m, _ ->
                                selectedStation = station
                                view.controller.animateTo(m.position)
                                true
                            }
                        })
                    }
                }
                view.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )
            val adminAttributionPadding by animateDpAsState(
                targetValue = if (selectedStation != null) 216.dp else 6.dp,
                label = "adminAttributionPadding"
            )
            Text(
                text = "© OpenStreetMap contributors",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 6.dp, bottom = adminAttributionPadding)
            )

            androidx.compose.animation.AnimatedVisibility(
                visible = selectedStation != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
            ) {
                selectedStation?.let { station ->
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Column(modifier = Modifier.padding(20.dp).fillMaxSize()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Surface(
                                        color = when(station.status) {
                                            "Open" -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                                            "Maintenance" -> Color(0xFFFF9800).copy(alpha = 0.15f)
                                            "Closed" -> Color(0xFFF44336).copy(alpha = 0.15f)
                                            "Permanently Closed" -> Color.Gray.copy(alpha = 0.15f)
                                            else -> Color.Gray.copy(alpha = 0.15f)
                                        },
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    ) {
                                        Text(
                                            text = station.status.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = when(station.status) {
                                                "Open" -> Color(0xFF2E7D32)
                                                "Maintenance" -> Color(0xFFE65100)
                                                "Closed" -> Color(0xFFD32F2F)
                                                "Permanently Closed" -> Color.DarkGray
                                                else -> Color.DarkGray
                                            },
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(text = station.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "Location: ${String.format(Locale.US, "%.4f, %.4f", station.latitude, station.longitude)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "${station.availableCycles}/${station.totalCycles}",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Cycles Available",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.weight(1f))
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            val newStatus = when(station.status) {
                                                "Open" -> "Maintenance"
                                                "Maintenance" -> "Closed"
                                                "Closed" -> "Open"
                                                else -> "Open"
                                            }
                                            database.stationDao().updateStation(station.copy(status = newStatus))
                                            selectedStation = station.copy(status = newStatus)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.large,
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Toggle", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }

                                Button(
                                    onClick = {
                                        scope.launch {
                                            val newStatus = if (station.status == "Permanently Closed") "Open" else "Permanently Closed"
                                            database.stationDao().updateStation(station.copy(status = newStatus))
                                            selectedStation = station.copy(status = newStatus)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.large,
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (station.status == "Permanently Closed") Color(0xFF4CAF50) else Color.Gray
                                    )
                                ) {
                                    Icon(if (station.status == "Permanently Closed") Icons.Default.Refresh else Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (station.status == "Permanently Closed") "Reopen" else "Close", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddStationDialog && selectedPoint != null) {
        var name by remember { mutableStateOf("") }
        var totalCycles by remember { mutableStateOf("10") }
        var selectedStatus by remember { mutableStateOf("Open") }
        val statusOptions = listOf("Open", "Closed", "Maintenance", "Permanently Closed")
        
        AlertDialog(
            onDismissRequest = { showAddStationDialog = false },
            title = { Text("Add New Station") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Station Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = totalCycles, onValueChange = { totalCycles = it }, label = { Text("Total Cycles") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    
                    Text("Initial Status:", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        statusOptions.forEach { status ->
                            FilterChip(
                                selected = selectedStatus == status,
                                onClick = { selectedStatus = status },
                                label = { Text(status, maxLines = 1, softWrap = false) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        database.stationDao().insertStation(
                            Station(
                                name = name.ifBlank { "New Station" },
                                latitude = selectedPoint!!.latitude,
                                longitude = selectedPoint!!.longitude,
                                totalCycles = totalCycles.toIntOrNull() ?: 10,
                                availableCycles = totalCycles.toIntOrNull() ?: 10,
                                status = selectedStatus
                            )
                        )
                        showAddStationDialog = false
                    }
                }) { Text("Create Station") }
            },
            dismissButton = { TextButton(onClick = { showAddStationDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun AdminTopUpCard(
    request: com.example.sdp3.database.TopUpRequest,
    onApprove: (Double) -> Unit,
    onReject: (String) -> Unit
) {
    var showAdjustDialog by remember { mutableStateOf(false) }
    var showRejectDialog by remember { mutableStateOf(false) }
    var adjustedAmount by remember { mutableStateOf(request.amount.toString()) }
    var rejectReason by remember { mutableStateOf("") }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "User: ${request.userName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Phone: ${request.userPhone}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Surface(
                    color = when (request.status) {
                        "PENDING" -> MaterialTheme.colorScheme.tertiaryContainer
                        "APPROVED" -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                        else -> MaterialTheme.colorScheme.errorContainer
                    },
                    shape = CircleShape
                ) {
                    Text(
                        text = request.status,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when (request.status) {
                            "PENDING" -> MaterialTheme.colorScheme.onTertiaryContainer
                            "APPROVED" -> Color(0xFF2E7D32)
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), MaterialTheme.shapes.small)
                    .padding(12.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Transaction ID:", style = MaterialTheme.typography.labelMedium)
                    Text(request.transactionId, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Amount Requested:", style = MaterialTheme.typography.labelMedium)
                    Text("${request.amount} BDT", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                }
                Text(text = request.date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }

            if (request.status == "PENDING") {
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showRejectDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Text("Reject")
                    }
                    Button(
                        onClick = { showAdjustDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Approve / Adjust")
                    }
                }
            }
        }
    }

    if (showRejectDialog) {
        AlertDialog(
            onDismissRequest = { showRejectDialog = false },
            title = { Text("Reject Balance Request") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Provide a reason for rejection (e.g., Invalid Transaction ID):")
                    OutlinedTextField(
                        value = rejectReason,
                        onValueChange = { rejectReason = it },
                        label = { Text("Rejection Reason") },
                        placeholder = { Text("e.g. Fake transaction ID provided") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (rejectReason.isNotBlank()) {
                            onReject(rejectReason)
                            showRejectDialog = false
                        }
                    },
                    enabled = rejectReason.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Confirm Reject") }
            },
            dismissButton = {
                TextButton(onClick = { showRejectDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showAdjustDialog) {
        AlertDialog(
            onDismissRequest = { showAdjustDialog = false },
            title = { Text("Approve Top Up") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Verify the Transaction ID: ${request.transactionId}")
                    Text("Set amount to add to user's balance:")
                    OutlinedTextField(
                        value = adjustedAmount,
                        onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null) adjustedAmount = it },
                        label = { Text("Final Amount (BDT)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalAmt = adjustedAmount.toDoubleOrNull() ?: 0.0
                        if (finalAmt >= 0) {
                            onApprove(finalAmt)
                            showAdjustDialog = false
                        }
                    }
                ) { Text("Confirm Approval") }
            },
            dismissButton = {
                TextButton(onClick = { showAdjustDialog = false }) { Text("Cancel") }
            }
        )
    }
}
