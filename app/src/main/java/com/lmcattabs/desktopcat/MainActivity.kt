package com.lmcattabs.desktopcat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var profile: CatProfile
    private lateinit var toggleBtn: Button
    private lateinit var permNote: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        profile = CatProfileStore.load(this)
        requestNotificationPermissionIfNeeded()
        setContentView(buildUi())
        refreshButtons()
    }

    override fun onResume() {
        super.onResume()
        refreshButtons()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun cardBg(fill: Int, stroke: Int): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        setStroke(dp(2), stroke)
        cornerRadius = dp(8).toFloat()
    }

    private fun sectionTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(Color.rgb(148, 163, 184))
        textSize = 12f
        setPadding(0, dp(14), 0, dp(6))
    }

    private fun makeChip(label: String, selected: Boolean, accent: Int, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 12f
            setTextColor(if (selected) Color.WHITE else Color.rgb(203, 213, 225))
            background = cardBg(
                if (selected) Color.rgb(42, 54, 90) else Color.rgb(26, 32, 52),
                if (selected) accent else Color.rgb(14, 17, 29)
            )
            minHeight = dp(44)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { onClick() }
        }
    }

    private fun buildUi(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(24, 29, 48))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(28))
        }
        scroll.addView(root)

        val title = TextView(this).apply {
            text = "LMcat.Tabs — Android Overlay"
            setTextColor(Color.WHITE)
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        root.addView(title)

        permNote = TextView(this).apply {
            setTextColor(Color.rgb(251, 191, 36))
            textSize = 12f
            setPadding(0, dp(6), 0, dp(14))
        }
        root.addView(permNote)

        toggleBtn = Button(this).apply {
            isAllCaps = false
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            minHeight = dp(52)
            setOnClickListener { handleToggleOverlay() }
        }
        root.addView(toggleBtn)

        // Quick Toy Row
        root.addView(sectionTitle("Hızlı Oyuncaklar & Hareket"))
        root.addView(makeGridRow(listOf(
            "Balık At" to { sendServiceAction(CatOverlayService.ACTION_SPAWN_FISH) },
            "İp Yumağı" to { sendServiceAction(CatOverlayService.ACTION_TOGGLE_YARN) },
            "Uyku Yastığı" to { sendServiceAction(CatOverlayService.ACTION_TOGGLE_PILLOW) },
            "Duvara Tırman" to { sendServiceAction(CatOverlayService.ACTION_CLIMB_WALL) }
        ), 2, Color.rgb(56, 189, 248)))

        // Coats
        root.addView(sectionTitle("Kürk Deseni"))
        root.addView(makeSelectGrid(
            CatPalettes.ALL.map { it.id to it.label },
            profile.coat,
            3,
            Color.rgb(251, 191, 36)
        ) { id ->
            profile.coat = id
            saveAndNotify()
        })

        // Moods
        root.addView(sectionTitle("Duygu Durumu (Sabit)"))
        root.addView(makeSelectGrid(
            listOf(
                "CURIOUS" to "Meraklı",
                "AFFECTIONATE" to "Sevecen",
                "CHILL" to "Sakin",
                "SLEEPY" to "Uykulu",
                "HUNTER" to "Avcı",
                "CHAOTIC" to "Kaotik"
            ),
            profile.mood,
            3,
            Color.rgb(56, 189, 248)
        ) { id ->
            profile.mood = id
            saveAndNotify()
        })

        // Headwear
        root.addView(sectionTitle("Taç & Şapka"))
        root.addView(makeSelectGrid(
            listOf("none" to "Yok", "crown" to "Taç", "ribbon" to "Fiyonk", "beanie" to "Bere", "wizard" to "Büyücü"),
            profile.headwear,
            3,
            Color.rgb(251, 191, 36)
        ) { id ->
            profile.headwear = id
            saveAndNotify()
        })

        // Collar
        root.addView(sectionTitle("Tasma"))
        root.addView(makeSelectGrid(
            listOf("none" to "Yok", "bell_red" to "Kırmızı Zil", "star_teal" to "Turkuaz", "gold_royal" to "Altın"),
            profile.collar,
            2,
            Color.rgb(244, 63, 94)
        ) { id ->
            profile.collar = id
            saveAndNotify()
        })

        // Scale
        root.addView(sectionTitle("Kedi Boyutu"))
        root.addView(makeSelectGrid(
            listOf("1.0" to "1.0x Normal", "1.25" to "1.25x Orta", "1.5" to "1.5x Büyük"),
            if (profile.scale < 1.12f) "1.0" else if (profile.scale > 1.38f) "1.5" else "1.25",
            3,
            Color.rgb(52, 211, 153)
        ) { s ->
            profile.scale = s.toFloatOrNull() ?: 1.25f
            saveAndNotify()
        })

        // Auto-start on Boot
        val bootCheck = CheckBox(this).apply {
            text = "Telefon açıldığında LMcat.Tabs otomatik başlasın"
            setTextColor(Color.rgb(226, 232, 240))
            isChecked = profile.autoStartOnBoot
            setPadding(dp(6), dp(14), dp(6), dp(8))
            setOnCheckedChangeListener { _, checked ->
                profile.autoStartOnBoot = checked
                CatProfileStore.save(this@MainActivity, profile)
            }
        }
        root.addView(bootCheck)

        return scroll
    }

    private fun makeGridRow(items: List<Pair<String, () -> Unit>>, cols: Int, accent: Int): LinearLayout {
        val wrapper = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        items.chunked(cols).forEach { chunk ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            chunk.forEach { (label, action) ->
                val btn = makeChip(label, false, accent, action)
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                }
                row.addView(btn, lp)
            }
            wrapper.addView(row)
        }
        return wrapper
    }

    private fun makeSelectGrid(
        items: List<Pair<String, String>>,
        currentId: String,
        cols: Int,
        accent: Int,
        onSelect: (String) -> Unit
    ): LinearLayout {
        val wrapper = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        items.chunked(cols).forEach { chunk ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            chunk.forEach { (id, label) ->
                val btn = makeChip(label, id == currentId, accent) {
                    onSelect(id)
                    recreate()
                }
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                }
                row.addView(btn, lp)
            }
            wrapper.addView(row)
        }
        return wrapper
    }

    private fun saveAndNotify() {
        CatProfileStore.save(this, profile)
        if (CatOverlayService.isRunning) {
            sendServiceAction(CatOverlayService.ACTION_RELOAD_PROFILE)
        }
    }

    private fun sendServiceAction(action: String) {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
            return
        }
        val intent = Intent(this, CatOverlayService::class.java).setAction(action)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun handleToggleOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
            return
        }
        if (CatOverlayService.isRunning) {
            stopService(Intent(this, CatOverlayService::class.java))
        } else {
            val intent = Intent(this, CatOverlayService::class.java).setAction(CatOverlayService.ACTION_START)
            ContextCompat.startForegroundService(this, intent)
        }
        toggleBtn.postDelayed({ refreshButtons() }, 200)
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun refreshButtons() {
        val hasPerm = Settings.canDrawOverlays(this)
        if (!hasPerm) {
            permNote.text = "Adım 1: Kedinin WhatsApp/Instagram üzerinde gezebilmesi için 'Diğer uygulamaların üzerinde göster' iznini açın."
            toggleBtn.text = "Overlay İznini Ver"
            toggleBtn.setTextColor(Color.rgb(15, 19, 32))
            toggleBtn.background = cardBg(Color.rgb(251, 191, 36), Color.rgb(11, 13, 22))
        } else if (CatOverlayService.isRunning) {
            permNote.text = "Kedi şu an aktif! Parmağınızla sürükleyip fırlatabilir, yan duvarlara tırmandırabilirsiniz."
            toggleBtn.text = "Kediyi Durdur"
            toggleBtn.setTextColor(Color.WHITE)
            toggleBtn.background = cardBg(Color.rgb(190, 18, 60), Color.rgb(11, 13, 22))
        } else {
            permNote.text = "İzin hazır! Başlattığınızda LMcat.Tabs tüm uygulamalarınızın üstünde şeffaf olarak yaşar."
            toggleBtn.text = "Kediyi Ekranda Başlat"
            toggleBtn.setTextColor(Color.rgb(15, 19, 32))
            toggleBtn.background = cardBg(Color.rgb(52, 211, 153), Color.rgb(11, 13, 22))
        }
    }
}
