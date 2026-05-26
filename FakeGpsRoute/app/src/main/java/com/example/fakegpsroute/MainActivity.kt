package com.example.fakegpsroute

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import android.text.InputType
import android.graphics.Typeface
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.app.Activity

class MainActivity : Activity() {
    private lateinit var routeInput: EditText
    private lateinit var speedInput: EditText
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNeededPermissions()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 40, 32, 32)
        }

        root.addView(TextView(this).apply {
            text = "Fake GPS Route"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
        })

        root.addView(TextView(this).apply {
            text = "Írj be útvonalpontokat, soronként: latitude,longitude\nPélda: 47.4979,19.0402"
            textSize = 14f
            setPadding(0, 16, 0, 8)
        })

        routeInput = EditText(this).apply {
            setSingleLine(false)
            minLines = 8
            gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText("47.497912,19.040235\n47.498700,19.042200\n47.500200,19.045100")
        }
        root.addView(routeInput, LinearLayout.LayoutParams(-1, 0, 1f))

        root.addView(TextView(this).apply { text = "Sebesség (km/h)" })
        speedInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText("20")
        }
        root.addView(speedInput)

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val start = Button(this).apply { text = "Indítás" }
        val stop = Button(this).apply { text = "Leállítás" }
        val settings = Button(this).apply { text = "Mock app beállítás" }
        buttons.addView(start, LinearLayout.LayoutParams(0, -2, 1f))
        buttons.addView(stop, LinearLayout.LayoutParams(0, -2, 1f))
        buttons.addView(settings, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(buttons)

        statusText = TextView(this).apply {
            text = "Állapot: készen áll. A telefonon válaszd ezt az appot: Settings → Developer options → Select mock location app."
            setPadding(0, 20, 0, 0)
        }
        root.addView(statusText)
        setContentView(root)

        start.setOnClickListener { startMocking() }
        stop.setOnClickListener { stopService(Intent(this, MockRouteService::class.java)); statusText.text = "Állapot: leállítva." }
        settings.setOnClickListener { openDeveloperOptions() }
    }

    private fun requestNeededPermissions() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (android.os.Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
        val missing = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), 10)
    }

    private fun startMocking() {
        val points = routeInput.text.toString().lines()
            .mapNotNull { line ->
                val parts = line.trim().split(",")
                if (parts.size != 2) null else {
                    val lat = parts[0].trim().toDoubleOrNull()
                    val lon = parts[1].trim().toDoubleOrNull()
                    if (lat == null || lon == null) null else "$lat,$lon"
                }
            }
        if (points.size < 2) {
            AlertDialog.Builder(this).setTitle("Kevés pont").setMessage("Legalább két útvonalpont kell.").setPositiveButton("OK", null).show()
            return
        }
        val speed = speedInput.text.toString().toDoubleOrNull() ?: 20.0
        val intent = Intent(this, MockRouteService::class.java).apply {
            action = MockRouteService.ACTION_START
            putExtra(MockRouteService.EXTRA_ROUTE, points.joinToString(";"))
            putExtra(MockRouteService.EXTRA_SPEED_KMH, speed)
        }
        ContextCompat.startForegroundService(this, intent)
        statusText.text = "Állapot: fut. Sebesség: $speed km/h, pontok: ${points.size}."
    }

    private fun openDeveloperOptions() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}
