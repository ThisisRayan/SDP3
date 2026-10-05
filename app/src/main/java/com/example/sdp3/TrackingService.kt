package com.example.sdp3

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import org.osmdroid.util.GeoPoint
import org.json.JSONArray
import org.json.JSONObject
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.flow.first
import android.content.pm.ServiceInfo

class TrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isTracking = false
    private var isBookingActive = false

    companion object {
        const val CHANNEL_ID = "tracking_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "ACTION_START"
        const val ACTION_START_BOOKING = "ACTION_START_BOOKING"
        const val ACTION_STOP = "ACTION_STOP"
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val newPoint = GeoPoint(location.latitude, location.longitude)
                    updateRouteInManager(newPoint)
                }
            }
        }
    }

    private fun updateRouteInManager(point: GeoPoint) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val prefs = applicationContext.dataStore.data.first()
                val isRenting = prefs[booleanPreferencesKey("is_renting")] ?: false
                if (!isRenting) return@launch

                val routeJson = prefs[stringPreferencesKey("active_route_json")] ?: "[]"
                val array = JSONArray(routeJson)
                
                if (array.length() > 0) {
                    val last = array.getJSONObject(array.length() - 1)
                    val lastPoint = GeoPoint(last.getDouble("lat"), last.getDouble("lon"))
                    if (lastPoint.distanceToAsDouble(point) < 5.0) return@launch
                }

                array.put(JSONObject().put("lat", point.latitude).put("lon", point.longitude))
                applicationContext.dataStore.edit { it[stringPreferencesKey("active_route_json")] = array.toString() }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_START_BOOKING -> startBookingNotification()
            ACTION_STOP -> stopService()
        }
        return START_STICKY
    }

    private fun startTracking() {
        if (isTracking) return
        isTracking = true
        isBookingActive = false

        serviceScope.launch {
            val prefs = applicationContext.dataStore.data.first()
            val startTime = prefs[longPreferencesKey("active_start_time")] ?: System.currentTimeMillis()
            
            val notification = createModernNotification(
                title = "Ride Ongoing",
                content = "Your cycle ride is active",
                startTime = startTime,
                isCountDown = false
            )
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateDistanceMeters(5f)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun startBookingNotification() {
        if (isBookingActive) return
        isBookingActive = true
        isTracking = false
        fusedLocationClient.removeLocationUpdates(locationCallback)

        serviceScope.launch {
            val prefs = applicationContext.dataStore.data.first()
            val startTime = prefs[longPreferencesKey("booking_time")] ?: System.currentTimeMillis()
            val expiryTime = startTime + (30 * 60 * 1000) // 30 minutes expiry
            
            val notification = createModernNotification(
                title = "Cycle Booked",
                content = "Pick up your cycle within 30 mins",
                startTime = expiryTime,
                isCountDown = true
            )
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopService() {
        isTracking = false
        isBookingActive = false
        fusedLocationClient.removeLocationUpdates(locationCallback)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun createModernNotification(
        title: String,
        content: String,
        startTime: Long,
        isCountDown: Boolean
    ): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(if (isCountDown) android.R.drawable.ic_menu_agenda else android.R.drawable.ic_menu_directions)
            .setColor(0xFF4CAF50.toInt())
            .setColorized(false) // Transparent look as requested
            .setOngoing(true)
            .setUsesChronometer(true)
            .setWhen(startTime)
            .setShowWhen(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setSubText("CycleRent")

        if (isCountDown && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setChronometerCountDown(true)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, 
                "Cycle Status", 
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active ride or booking status"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
