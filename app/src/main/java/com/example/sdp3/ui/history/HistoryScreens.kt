package com.example.sdp3.ui.history

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color as AndroidColor
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.sdp3.Booking
import com.example.sdp3.Trip
import com.example.sdp3.database.AppDatabase
import com.example.sdp3.database.Report
import com.example.sdp3.database.TopUpRequest
import org.json.JSONArray
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryPage(
    userPhone: String,
    trips: List<Trip>, 
    bookings: List<Booking>, 
    reports: List<Report>,
    isActionOngoing: Boolean,
    onClearHistory: () -> Unit,
    onReportTrip: (Trip, String, String) -> Unit,
    onReportBooking: (Booking, String, String) -> Unit
) {
    var selectedTab by remember { mutableStateOf("Trips") }
    var sortOrder by remember { mutableStateOf("Latest") }
    var bookingFilter by remember { mutableStateOf("All") }
    
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)
    val userTopUps by database.topUpDao().getRequestsByUser(userPhone).collectAsState(initial = emptyList())
    
    val sortedTrips = remember(trips, sortOrder) {
        when (sortOrder) {
            "Cost: Low to High" -> trips.sortedBy { it.cost + it.fine }
            "Cost: High to Low" -> trips.sortedByDescending { it.cost + it.fine }
            "Alphabetical" -> trips.sortedBy { it.stationLocation }
            "Oldest" -> trips.sortedBy { it.id }
            else -> trips.sortedByDescending { it.id } // "Latest"
        }
    }

    val sortedBookings = remember(bookings, sortOrder, bookingFilter) {
        val filtered = when (bookingFilter) {
            "Rented Successfully" -> bookings.filter { it.status == "COMPLETED" }
            "Cancelled" -> bookings.filter { it.status == "CANCELLED" }
            "Expired" -> bookings.filter { it.status == "EXPIRED" }
            else -> bookings
        }
        when (sortOrder) {
            "Oldest" -> filtered.sortedBy { it.id }
            else -> filtered.sortedByDescending { it.id } // "Latest"
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = "History", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            if (trips.isNotEmpty() || bookings.isNotEmpty()) { TextButton(onClick = onClearHistory) { Text("Clear All", color = MaterialTheme.colorScheme.error) } }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TabRow(
            selectedTabIndex = when(selectedTab) { "Trips" -> 0; "Bookings" -> 1; else -> 2 },
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = {}
        ) {
            Tab(selected = selectedTab == "Trips", onClick = { selectedTab = "Trips" }) {
                Text("Trips", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall)
            }
            Tab(selected = selectedTab == "Bookings", onClick = { selectedTab = "Bookings" }) {
                Text("Bookings", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall)
            }
            Tab(selected = selectedTab == "Payments", onClick = { selectedTab = "Payments" }) {
                Text("Payments", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall)
            }
        }

        if (selectedTab == "Trips" && trips.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Latest", "Oldest", "Cost: Low to High", "Cost: High to Low", "Alphabetical").forEach { option ->
                    FilterChip(
                        selected = sortOrder == option,
                        onClick = { sortOrder = option },
                        label = { Text(option) },
                        leadingIcon = if (sortOrder == option) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }
        } else if (selectedTab == "Bookings" && bookings.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Rented Successfully", "Cancelled", "Expired").forEach { option ->
                    FilterChip(
                        selected = bookingFilter == option,
                        onClick = { bookingFilter = option },
                        label = { Text(option) },
                        leadingIcon = if (bookingFilter == option) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        if (selectedTab == "Trips") {
            if (trips.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text = "No trips yet. Start your first ride!", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline) }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
                    items(sortedTrips, key = { it.id }) { trip -> 
                        val report = reports.find { it.referenceId == trip.id }
                        TripHistoryCard(
                            trip = trip, 
                            reportStatus = report?.status,
                            isActionOngoing = isActionOngoing,
                            onReport = { title, desc -> onReportTrip(trip, title, desc) }
                        ) 
                    }
                }
            }
        } else if (selectedTab == "Bookings") {
            if (bookings.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text = "No bookings recorded.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline) }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
                    items(sortedBookings, key = { it.id }) { booking -> 
                        val report = reports.find { it.referenceId == booking.id }
                        BookingHistoryCard(
                            booking = booking, 
                            reportStatus = report?.status,
                            isActionOngoing = isActionOngoing,
                            onReport = { title, desc -> onReportBooking(booking, title, desc) }
                        ) 
                    }
                }
            }
        } else if (selectedTab == "Payments") {
            if (userTopUps.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text = "No payment history found.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline) }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
                    items(userTopUps, key = { it.id }) { request ->
                        PaymentHistoryCard(request)
                    }
                }
            }
        }
    }
}

@Composable
fun PaymentHistoryCard(request: TopUpRequest) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Balance Request",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "TXN: ${request.transactionId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = request.date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                }
                Surface(
                    color = when(request.status) {
                        "APPROVED" -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                        "REJECTED" -> Color(0xFFF44336).copy(alpha = 0.15f)
                        else -> MaterialTheme.colorScheme.tertiaryContainer
                    },
                    shape = CircleShape
                ) {
                    Text(
                        text = request.status,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = when(request.status) {
                            "APPROVED" -> Color(0xFF2E7D32)
                            "REJECTED" -> Color(0xFFD32F2F)
                            else -> MaterialTheme.colorScheme.onTertiaryContainer
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = String.format(Locale.US, "Amount: %.2f BDT", request.amount),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            if (request.status == "REJECTED" && !request.rejectionReason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Rejection Reason:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = request.rejectionReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BookingHistoryCard(
    booking: Booking, 
    reportStatus: String? = null,
    isActionOngoing: Boolean = false,
    onReport: ((String, String) -> Unit)? = null
) {
    var showReportDialog by remember { mutableStateOf(false) }
    val statusColor = when (booking.status) {
        "COMPLETED" -> Color(0xFF4CAF50) // Green
        "CANCELLED", "EXPIRED" -> Color(0xFFF44336) // Red
        else -> MaterialTheme.colorScheme.primary
    }

    val statusText = when(booking.status) {
        "COMPLETED" -> "Rented Successfully"
        "CANCELLED" -> "Cancelled"
        "EXPIRED" -> "Expired"
        else -> booking.status
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = booking.stationName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Booking ID: ${booking.id}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = booking.date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                    }
                }
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = CircleShape
                ) {
                    Text(
                        text = statusText.uppercase(),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = String.format(Locale.US, "Paid: %.2f BDT", booking.amountPaid), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (booking.refundAmount > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF4CAF50))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = String.format(Locale.US, "Refunded: %.2f BDT", booking.refundAmount), style = MaterialTheme.typography.bodySmall, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    if (reportStatus != null) {
                        Surface(
                            color = if (reportStatus == "PENDING") MaterialTheme.colorScheme.errorContainer else Color(0xFF4CAF50).copy(alpha = 0.1f),
                            shape = CircleShape,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(
                                text = "REPORT: $reportStatus",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (reportStatus == "PENDING") MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                            )
                        }
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (reportStatus == null && onReport != null) {
                        FilledTonalButton(
                            onClick = { showReportDialog = true },
                            enabled = !isActionOngoing,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Default.Report, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Report Issue", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (booking.status == "COMPLETED") Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
    if (showReportDialog && onReport != null) {
        ReportIssueDialog(
            id = booking.id,
            type = "BOOKING",
            onDismiss = { showReportDialog = false },
            onSubmit = { title, desc ->
                onReport(title, desc)
                showReportDialog = false
            }
        )
    }
}


@Composable
fun TripHistoryCard(
    trip: Trip, 
    reportStatus: String? = null,
    isActionOngoing: Boolean = false,
    onReport: ((String, String) -> Unit)? = null
) {
    var showReceipt by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = trip.stationLocation,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Rent ID: ${trip.id}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = trip.date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                    }
                    if (trip.wasBooked) {
                        Surface(
                            color = Color(0xFF4CAF50).copy(alpha = 0.15f),
                            shape = MaterialTheme.shapes.extraSmall,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = "BOOKED RIDE (60 BDT CREDIT)",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = String.format(Locale.US, "%.2f BDT", trip.cost + trip.fine),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black
                    )
                    Text(text = "Total Paid", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.DirectionsBike,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = String.format(Locale.US, "%.2f km", trip.distanceKm),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (reportStatus != null) {
                        Surface(
                            color = if (reportStatus == "PENDING") MaterialTheme.colorScheme.errorContainer else Color(0xFF4CAF50).copy(alpha = 0.1f),
                            shape = CircleShape
                        ) {
                            Text(
                                text = "REPORT: $reportStatus",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (reportStatus == "PENDING") MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                            )
                        }
                    } else if (onReport != null) {
                        FilledTonalButton(
                            onClick = { showReportDialog = true },
                            enabled = !isActionOngoing,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Default.Report, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Report Issue", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedButton(
                        onClick = { showReceipt = true },
                        shape = MaterialTheme.shapes.medium,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("More Info", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showReceipt) {
        TripReceiptDialog(trip = trip, onDismiss = { showReceipt = false })
    }
    if (showReportDialog && onReport != null) {
        ReportIssueDialog(
            id = trip.id,
            type = "TRIP",
            onDismiss = { showReportDialog = false },
            onSubmit = { title, desc ->
                onReport(title, desc)
                showReportDialog = false
            }
        )
    }
}

@Composable
fun ReportIssueDialog(
    id: String,
    type: String,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val maxWords = 250
    val wordCount = description.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report Issue - $type ID: $id") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title of Report") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { 
                        val newWordCount = it.trim().split(Regex("\\s+")).filter { s -> s.isNotEmpty() }.size
                        if (newWordCount <= maxWords) description = it 
                    },
                    label = { Text("Description (Max $maxWords words)") },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    maxLines = 10,
                    supportingText = { Text("$wordCount / $maxWords words") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(title, description) },
                enabled = title.isNotBlank() && description.isNotBlank()
            ) {
                Text("Submit Complain")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripReceiptDialog(trip: Trip, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
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

    // Initialize map content only once or when trip changes
    LaunchedEffect(trip) {
        mapView.overlays.clear()
        val startPoint = GeoPoint(trip.startLat, trip.startLon)
        val endPoint = GeoPoint(trip.endLat, trip.endLon)
        val line = Polyline()
        val allPoints = mutableListOf<GeoPoint>()

        if (trip.routeJson.isNotEmpty()) {
            try {
                val pointsArr = JSONArray(trip.routeJson)
                for (i in 0 until pointsArr.length()) {
                    val p = pointsArr.getJSONObject(i)
                    allPoints.add(GeoPoint(p.getDouble("lat"), p.getDouble("lon")))
                }
                line.setPoints(allPoints)
            } catch (e: Exception) {
                allPoints.add(startPoint); allPoints.add(endPoint); line.setPoints(allPoints)
            }
        } else {
            allPoints.add(startPoint); allPoints.add(endPoint); line.setPoints(allPoints)
        }

        line.outlinePaint.color = AndroidColor.parseColor("#FC4C02")
        line.outlinePaint.strokeWidth = 12f
        line.outlinePaint.strokeCap = Paint.Cap.ROUND
        line.outlinePaint.isAntiAlias = true
        mapView.overlays.add(line)

        val density = context.resources.displayMetrics.density
        fun createModernMarker(color: Int): android.graphics.drawable.BitmapDrawable {
            val size = (16 * density).toInt()
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = AndroidColor.argb(60, 0, 0, 0)
            canvas.drawCircle(size / 2f, size / 2f + 1 * density, size / 2.2f, paint)
            paint.color = AndroidColor.WHITE
            canvas.drawCircle(size / 2f, size / 2f, size / 2.2f, paint)
            paint.color = color
            canvas.drawCircle(size / 2f, size / 2f, size / 3.2f, paint)
            return bitmap.toDrawable(context.resources)
        }

        mapView.overlays.add(Marker(mapView).apply {
            position = startPoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = createModernMarker(AndroidColor.parseColor("#4CAF50"))
            infoWindow = null
        })

        mapView.overlays.add(Marker(mapView).apply {
            position = endPoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = createModernMarker(AndroidColor.parseColor("#FF5252"))
            infoWindow = null
        })

        mapView.post {
            if (allPoints.isNotEmpty()) {
                val bounds = org.osmdroid.util.BoundingBox.fromGeoPoints(allPoints)
                val latDiff = (bounds.latNorth - bounds.latSouth).coerceAtLeast(0.002)
                val lonDiff = (bounds.lonEast - bounds.lonWest).coerceAtLeast(0.002)
                mapView.zoomToBoundingBox(org.osmdroid.util.BoundingBox(
                    bounds.latNorth + latDiff * 0.4,
                    bounds.lonEast + lonDiff * 0.4,
                    bounds.latSouth - latDiff * 0.4,
                    bounds.lonWest - lonDiff * 0.4
                ), false)
            }
        }
        mapView.invalidate()
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxSize().padding(16.dp),
        content = {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Trip Receipt",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Map Preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        AndroidView(
                            factory = { mapView },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Ride Summary",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = Color.Black
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = Color.White.copy(alpha = 0.5f),
                                shape = MaterialTheme.shapes.large
                            )
                            .padding(16.dp)
                    ) {
                        ReceiptRow("Rent ID", trip.id, Icons.Default.ConfirmationNumber)
                        ReceiptRow("Start Station", trip.startStation, Icons.Default.TripOrigin, color = Color(0xFF4CAF50))
                        ReceiptRow("End Station", trip.endStation, Icons.Default.LocationOn, color = Color(0xFFFF5252))
                        ReceiptRow("Date", trip.date, Icons.Default.DateRange)
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        val hours = trip.durationSeconds / 3600
                        val minutes = (trip.durationSeconds % 3600) / 60
                        val seconds = trip.durationSeconds % 60
                        val timeStr = if (hours > 0) String.format(Locale.US, "%dh %dm %ds", hours, minutes, seconds)
                                      else String.format(Locale.US, "%dm %ds", minutes, seconds)

                        ReceiptRow("Duration", timeStr, Icons.Default.AccessTime)
                        ReceiptRow("Distance", String.format(Locale.US, "%.2f km", trip.distanceKm), Icons.AutoMirrored.Filled.DirectionsBike)

                        val avgSpeed = if (trip.durationSeconds > 0) (trip.distanceKm / (trip.durationSeconds / 3600.0)) else 0.0
                        ReceiptRow("Avg Speed", String.format(Locale.US, "%.1f km/h", avgSpeed), Icons.Default.Speed)

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        ReceiptRow("Base Fare", String.format(Locale.US, "%.2f BDT", trip.cost), Icons.Default.Payments)
                        if (trip.wasBooked) {
                            ReceiptRow("Booking Credit", "-60.00 BDT", Icons.Default.CreditCard, color = Color(0xFF4CAF50))
                        }
                        if (trip.fine > 0) {
                            ReceiptRow("Fines (GPS/Other)", String.format(Locale.US, "%.2f BDT", trip.fine), Icons.Default.Warning, color = MaterialTheme.colorScheme.error)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (trip.wasBooked) "Additional Paid" else "Total Paid",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                val totalDisplay = if (trip.wasBooked) {
                                    maxOf(0.0, trip.cost - 60.0) + trip.fine
                                } else {
                                    trip.cost + trip.fine
                                }
                                Text(
                                    text = String.format(Locale.US, "%.2f BDT", totalDisplay),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text("Close Receipt")
                    }
                }
            }
        }
    )
}

@Composable
fun ReceiptRow(label: String, value: String, icon: ImageVector? = null, color: Color = Color.Unspecified) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        }
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun AdminReportCard(report: Report, onSolve: () -> Unit) {
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
                        text = report.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${report.type} ID: ${report.referenceId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Surface(
                    color = if (report.status == "PENDING") MaterialTheme.colorScheme.errorContainer else Color(0xFF4CAF50).copy(alpha = 0.1f),
                    shape = CircleShape
                ) {
                    Text(
                        text = report.status,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (report.status == "PENDING") MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "From: ${report.userName} (${report.userPhone})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = report.date,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = report.description,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            if (report.status == "PENDING") {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onSolve,
                    modifier = Modifier.align(Alignment.End),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Mark as Solved")
                }
            }
        }
    }
}
