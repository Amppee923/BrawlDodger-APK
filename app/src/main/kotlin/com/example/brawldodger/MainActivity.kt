package com.example.brawldodger

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityServiceInfo
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val OVERLAY_REQ = 1234
    private val ACCESSIBILITY_REQ = 5678

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Simple setup screen - or auto
        Button(this).apply {
            text = "Setup Permissions"
            setOnClickListener { setup() }
            // Add to layout or auto-call setup()
        }
        setup()
    }

    private fun setup() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Grant overlay", Toast.LENGTH_SHORT).show()
            startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")), OVERLAY_REQ)
            return
        }
        val am = getSystemService(ACCESSIBILITY_MANAGER) as AccessibilityManager
        val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == packageName }
        if (!enabled) {
            Toast.makeText(this, "Enable Accessibility: Brawl Dodger", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        Toast.makeText(this, "READY! Launch capture from overlay.", Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        setup()
    }
}
