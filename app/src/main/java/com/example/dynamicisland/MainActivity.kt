package com.example.dynamicisland

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val d = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * d).toInt(), (40 * d).toInt(), (20 * d).toInt(), (20 * d).toInt())
        }
        fun btn(t: String, f: () -> Unit) = Button(this).apply {
            text = t; setOnClickListener { f() }
        }.also { root.addView(it) }

        root.addView(TextView(this).apply { text = "Dinamik Ada"; textSize = 26f })
        root.addView(TextView(this).apply {
            text = "Sırayla 1, 2 ve 3. adımları yap, sonra test et."; setPadding(0, 0, 0, (12 * d).toInt())
        })
        btn("1. Ekran üstü gösterim izni") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        btn("2. Bildirim erişimi ver") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        btn("3. Pil kısıtlamasını kaldır") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        root.addView(TextView(this).apply { text = "Yukarıdan uzaklık (kamera deliğine hizala)"; setPadding(0, (16 * d).toInt(), 0, 0) })
        val prefs = getSharedPreferences("p", Context.MODE_PRIVATE)
        root.addView(SeekBar(this).apply {
            max = 120; progress = prefs.getInt("y", 8)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, u: Boolean) {
                    prefs.edit().putInt("y", p).apply(); IslandService.instance?.reposition()
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        })
        btn("Test: gelen arama göster") {
            val s = IslandService.instance
            if (s == null) Toast.makeText(this, "Önce bildirim erişimini ver", Toast.LENGTH_LONG).show()
            else s.demo()
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() { super.onResume(); IslandService.instance?.ensure() }
}
