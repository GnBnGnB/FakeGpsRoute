package com.example.fakegpsroute

import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import kotlin.concurrent.thread
import kotlin.math.*

class MockRouteService : Service() {
    companion object {
        const val ACTION_START = "com.example.fakegpsroute.START"
        const val EXTRA_ROUTE = "route"
        const val EXTRA_SPEED_KMH = "speed_kmh"
        private const val CHANNEL_ID = "mock_route"
        private const val NOTIFICATION_ID = 1001
    }

    private lateinit var locationManager: LocationManager
    @Volatile private var running = false
    private var worker: Thread? = null

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification("Mock GPS útvonal fut"))
        if (intent?.action == ACTION_START) {
            val route = parseRoute(intent.getStringExtra(EXTRA_ROUTE).orEmpty())
            val speedMps = ((intent.getDoubleExtra(EXTRA_SPEED_KMH, 20.0)).coerceAtLeast(0.5)) / 3.6
            startRoute(route, speedMps)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        worker?.interrupt()
        cleanupProvider(LocationManager.GPS_PROVIDER)
        cleanupProvider(LocationManager.NETWORK_PROVIDER)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startRoute(route: List<Point>, speedMps: Double) {
        if (route.size < 2) return
        running = false
        worker?.interrupt()
        running = true
        worker = thread(name = "MockRouteWorker") {
            try {
                setupProvider(LocationManager.GPS_PROVIDER)
                setupProvider(LocationManager.NETWORK_PROVIDER)
                var segment = 0
                var distanceInSegment = 0.0
                val tickSeconds = 1.0

                while (running) {
                    val start = route[segment]
                    val end = route[(segment + 1) % route.size]
                    val segmentDistance = distanceMeters(start, end).coerceAtLeast(1.0)
                    distanceInSegment += speedMps * tickSeconds
                    while (distanceInSegment >= segmentDistance) {
                        distanceInSegment -= segmentDistance
                        segment = (segment + 1) % route.size
                    }
                    val a = route[segment]
                    val b = route[(segment + 1) % route.size]
                    val fraction = (distanceInSegment / distanceMeters(a, b).coerceAtLeast(1.0)).coerceIn(0.0, 1.0)
                    val current = interpolate(a, b, fraction)
                    val bearing = bearingDegrees(a, b).toFloat()
                    pushLocation(LocationManager.GPS_PROVIDER, current, speedMps.toFloat(), bearing)
                    pushLocation(LocationManager.NETWORK_PROVIDER, current, speedMps.toFloat(), bearing)
                    Thread.sleep(1000)
                }
            } catch (se: SecurityException) {
                startForeground(NOTIFICATION_ID, notification("Nincs mock-location jogosultság. Válaszd ki az appot a Developer options alatt."))
            } catch (_: InterruptedException) {
            } catch (e: Exception) {
                startForeground(NOTIFICATION_ID, notification("Hiba: ${e.message ?: "ismeretlen"}"))
            }
        }
    }

    private fun setupProvider(provider: String) {
        try { locationManager.removeTestProvider(provider) } catch (_: Exception) {}
        locationManager.addTestProvider(provider, false, provider == LocationManager.GPS_PROVIDER, false, false, true, true, true, Criteria.POWER_LOW, Criteria.ACCURACY_FINE)
        locationManager.setTestProviderEnabled(provider, true)
    }

    private fun cleanupProvider(provider: String) {
        try { locationManager.removeTestProvider(provider) } catch (_: Exception) {}
    }

    private fun pushLocation(provider: String, p: Point, speed: Float, bearing: Float) {
        val loc = Location(provider).apply {
            latitude = p.lat
            longitude = p.lon
            altitude = 0.0
            accuracy = 3f
            this.speed = speed
            this.bearing = bearing
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        locationManager.setTestProviderLocation(provider, loc)
    }

    private fun parseRoute(raw: String): List<Point> = raw.split(";").mapNotNull { item ->
        val parts = item.split(",")
        if (parts.size != 2) null else {
            val lat = parts[0].toDoubleOrNull()
            val lon = parts[1].toDoubleOrNull()
            if (lat != null && lon != null) Point(lat, lon) else null
        }
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Mock GPS Route", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(text: String): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Fake GPS Route")
        .setContentText(text)
        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
        .setOngoing(true)
        .build()

    data class Point(val lat: Double, val lon: Double)

    private fun distanceMeters(a: Point, b: Point): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }

    private fun interpolate(a: Point, b: Point, f: Double): Point = Point(
        a.lat + (b.lat - a.lat) * f,
        a.lon + (b.lon - a.lon) * f
    )

    private fun bearingDegrees(a: Point, b: Point): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }
}
