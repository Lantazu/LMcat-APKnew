package com.lmcattabs.desktopcat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlin.math.hypot
import kotlin.math.roundToInt

class CatOverlayService : Service() {

    companion object {
        const val ACTION_START = "com.lmcattabs.START"
        const val ACTION_STOP = "com.lmcattabs.STOP"
        const val ACTION_RELOAD_PROFILE = "com.lmcattabs.RELOAD"
        const val ACTION_SPAWN_FISH = "com.lmcattabs.FISH"
        const val ACTION_TOGGLE_YARN = "com.lmcattabs.YARN"
        const val ACTION_TOGGLE_PILLOW = "com.lmcattabs.PILLOW"
        const val ACTION_CLIMB_WALL = "com.lmcattabs.CLIMB"
        private const val CHANNEL_ID = "lmcat_overlay_channel"
        private const val NOTIF_ID = 1090
        @Volatile
        var isRunning: Boolean = false
    }

    private lateinit var windowManager: WindowManager
    private lateinit var engine: CatPhysicsEngine
    private val renderer = CatSpriteRenderer()
    private val handler = Handler(Looper.getMainLooper())

    private var effectView: EffectOverlayView? = null
    private var catTouchView: CatTouchOverlayView? = null
    private var catLayoutParams: WindowManager.LayoutParams? = null
    private var pillowTouchView: PillowTouchOverlayView? = null
    private var pillowLayoutParams: WindowManager.LayoutParams? = null

    private var lastHitX: Int = Int.MIN_VALUE
    private var lastHitY: Int = Int.MIN_VALUE
    private var lastHitW: Int = -1
    private var lastHitH: Int = -1
    private var lastTickMs: Long = 0L

    private val frameLoop = object : Runnable {
        override fun run() {
            if (!isRunning) return
            val now = SystemClock.uptimeMillis()
            val dt = ((now - lastTickMs).coerceAtLeast(8L)) / 1000f
            lastTickMs = now

            engine.update(dt)
            syncCatWindowPosition()
            effectView?.invalidate()

            handler.postDelayed(this, engine.recommendedFrameDelayMs())
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun getRealScreenSizePx(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            return Pair(bounds.width(), bounds.height())
        }
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(dm)
        return Pair(dm.widthPixels, dm.heightPixels)
    }

    private data class CatHitboxRect(val x: Int, val y: Int, val w: Int, val h: Int)

    // Computes a razor-tight hitbox hugging ONLY the cat's actual rendered body + head
    // (excludes floating Zzz, tall hats, trailing tail tips, and empty space below feet so nearby keyboard keys always work!)
    private fun computeCatBodyHitbox(): CatHitboxRect {
        val px = (1.50f * engine.dpScale * engine.profile.scale).coerceAtLeast(2.2f)
        val minTouchPx = (26f * engine.dpScale).roundToInt()
        val drawFacing = if (engine.action == "CLIMB_WALL" && engine.climbSide != 0) engine.climbSide else engine.facing

        val gridW: Float
        val gridH: Float
        val centerOffsetX: Float
        val bottomOffsetY: Float

        when (engine.action) {
            "SLEEP_LOAF" -> {
                gridW = 27.0f
                gridH = 18.0f
                centerOffsetX = 0f
                bottomOffsetY = 0.5f
            }
            "CLIMB_WALL" -> {
                gridW = 19.5f
                gridH = 32.5f
                centerOffsetX = -0.5f * drawFacing
                bottomOffsetY = -2.0f
            }
            "DANGLE" -> {
                gridW = 19.5f
                gridH = 34.5f
                centerOffsetX = 0f
                bottomOffsetY = -1.0f
            }
            "WALK", "RUN", "JUMP", "FALL" -> {
                gridW = 25.0f
                gridH = 25.5f
                centerOffsetX = 2.5f * drawFacing
                bottomOffsetY = 0f
            }
            "BIG_STRETCH", "HAPPY_PURR" -> {
                gridW = 25.0f
                gridH = 24.0f
                centerOffsetX = 1.0f * drawFacing
                bottomOffsetY = 0f
            }
            else -> {
                // IDLE_SIT
                gridW = 20.5f
                gridH = 27.5f
                centerOffsetX = 0.5f * drawFacing
                bottomOffsetY = 0f
            }
        }

        val w = (gridW * px).roundToInt().coerceAtLeast(minTouchPx)
        val h = (gridH * px).roundToInt().coerceAtLeast(minTouchPx)
        val cx = engine.catX + centerOffsetX * px
        val bottomY = engine.catY + bottomOffsetY * px
        val left = (cx - w * 0.5f).roundToInt()
        val top = (bottomY - h).roundToInt()
        return CatHitboxRect(left, top, w, h)
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val dp = resources.displayMetrics.density
        val (realW, realH) = getRealScreenSizePx()
        val statusBarH = getStatusBarHeightPx().toFloat()
        val navBarH = getNavBarHeightPx().toFloat()

        val profile = CatProfileStore.load(this)

        engine = CatPhysicsEngine(
            screenW = realW.toFloat(),
            screenH = realH.toFloat(),
            statusBarY = statusBarH,
            floorY = realH.toFloat() - navBarH - 6f * dp,
            dpScale = dp,
            profile = profile
        )

        startAsForeground()
        attachOverlayWindows()
        lastTickMs = SystemClock.uptimeMillis()
        handler.post(frameLoop)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RELOAD_PROFILE -> {
                val updated = CatProfileStore.load(this)
                engine.profile = updated
                syncCatWindowPosition()
                syncPillowTouchWindow()
                effectView?.invalidate()
            }
            ACTION_SPAWN_FISH -> engine.spawnFish()
            ACTION_TOGGLE_YARN -> engine.toggleYarn()
            ACTION_TOGGLE_PILLOW -> {
                engine.togglePillow { CatProfileStore.save(this, it) }
                syncPillowTouchWindow()
            }
            ACTION_CLIMB_WALL -> engine.triggerWallClimb()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(frameLoop)
        removeViewSafe(effectView)
        removeViewSafe(catTouchView)
        removeViewSafe(pillowTouchView)
        super.onDestroy()
    }

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "LMcat.Tabs Masaüstü Kedisi",
            NotificationManager.IMPORTANCE_LOW
        )
        nm.createNotificationChannel(channel)

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, CatOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LMcat.Tabs Aktif")
            .setContentText("Piksel kediniz ekranınızda geziniyor. Ayarlar için dokunun.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, "Kapat", stopIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun applyUnifiedDisplayCutout(params: WindowManager.LayoutParams) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun attachOverlayWindows() {
        // 1. Full-screen 100% click-through layer for toys & particles (same physical 0,0 origin!)
        val effectParams = WindowManager.LayoutParams(
            engine.screenW.roundToInt(),
            engine.screenH.roundToInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            applyUnifiedDisplayCutout(this)
        }
        effectView = EffectOverlayView(this)
        windowManager.addView(effectView, effectParams)

        // 2. Invisible razor-tight body-only touch hitbox (cat sprite draws on click-through EffectOverlayView!)
        val hit = computeCatBodyHitbox()
        val cParams = WindowManager.LayoutParams(
            hit.w,
            hit.h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = hit.x
            y = hit.y
            applyUnifiedDisplayCutout(this)
        }
        catLayoutParams = cParams
        catTouchView = CatTouchOverlayView(this)
        syncCatWindowPosition(addIfNeeded = true)

        syncPillowTouchWindow()
    }

    private fun syncCatWindowPosition(addIfNeeded: Boolean = false) {
        val params = catLayoutParams ?: return
        val view = catTouchView ?: return
        val hit = computeCatBodyHitbox()
        if (!addIfNeeded && hit.x == lastHitX && hit.y == lastHitY && hit.w == lastHitW && hit.h == lastHitH) {
            return
        }
        params.x = hit.x
        params.y = hit.y
        params.width = hit.w
        params.height = hit.h
        lastHitX = hit.x
        lastHitY = hit.y
        lastHitW = hit.w
        lastHitH = hit.h
        try {
            if (addIfNeeded) {
                windowManager.addView(view, params)
            } else {
                windowManager.updateViewLayout(view, params)
            }
        } catch (_: Exception) {
        }
    }

    private fun syncPillowTouchWindow() {
        if (!engine.profile.hasPillow) {
            removeViewSafe(pillowTouchView)
            pillowTouchView = null
            pillowLayoutParams = null
            return
        }
        if (pillowTouchView == null) {
            // Tight hitbox matching the rendered quilted pillow (~69dp x 22dp)
            val pw = (72f * engine.dpScale).roundToInt()
            val ph = (22f * engine.dpScale).roundToInt()
            val pParams = WindowManager.LayoutParams(
                pw,
                ph,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (engine.profile.pillowX - pw * 0.5f).roundToInt()
                y = (engine.profile.pillowY - ph).roundToInt()
                applyUnifiedDisplayCutout(this)
            }
            pillowLayoutParams = pParams
            pillowTouchView = PillowTouchOverlayView(this)
            try {
                windowManager.addView(pillowTouchView, pParams)
            } catch (_: Exception) {
            }
        }
    }

    private fun removeViewSafe(v: View?) {
        if (v == null) return
        try {
            windowManager.removeView(v)
        } catch (_: Exception) {
        }
    }

    private fun getStatusBarHeightPx(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else (28f * resources.displayMetrics.density).roundToInt()
    }

    private fun getNavBarHeightPx(): Int {
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else (24f * resources.displayMetrics.density).roundToInt()
    }

    private inner class EffectOverlayView(context: Context) : View(context) {
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (engine.profile.hasPillow) {
                renderer.drawPillow(
                    canvas,
                    engine.profile.pillowX,
                    engine.profile.pillowY,
                    engine.dpScale,
                    engine.isSleepingOnPillow,
                    engine.animTime
                )
            }
            engine.yarn?.let { y -> renderer.drawYarn(canvas, y, engine.dpScale) }
            for (f in engine.fishes) {
                renderer.drawFish(canvas, f.x, f.y, engine.dpScale)
            }
            // Render the full-detail chibi pixel cat on the 100% click-through canvas (zero clipping for hats/tails!)
            renderer.drawCat(
                canvas = canvas,
                centerX = engine.catX,
                footY = engine.catY,
                dpScale = engine.dpScale,
                profile = engine.profile,
                action = engine.action,
                facing = engine.facing,
                animTime = engine.animTime,
                blinkActive = engine.blinkActive,
                climbSide = engine.climbSide
            )
            for (p in engine.particles) {
                renderer.drawParticle(canvas, p, engine.dpScale)
            }
        }
    }

    // Pure transparent touch-only hitbox tightly sized to the cat's body silhouette
    private inner class CatTouchOverlayView(context: Context) : View(context) {
        private var downRawX = 0f
        private var downRawY = 0f
        private var dragging = false

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    dragging = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging && hypot(event.rawX - downRawX, event.rawY - downRawY) > 10f * engine.dpScale) {
                        dragging = true
                        engine.onCatDragStart(event.rawX, event.rawY)
                    }
                    if (dragging) {
                        engine.onCatDragMove(event.rawX, event.rawY)
                        syncCatWindowPosition()
                        effectView?.invalidate()
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        dragging = false
                        engine.onCatDragEnd()
                    } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                        engine.onCatTapPet()
                    }
                    effectView?.invalidate()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }

    private inner class PillowTouchOverlayView(context: Context) : View(context) {
        override fun onTouchEvent(event: MotionEvent): Boolean {
            val params = pillowLayoutParams ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val nx = event.rawX.coerceIn(52f * engine.dpScale, engine.screenW - 52f * engine.dpScale)
                    val ny = event.rawY.coerceIn(engine.topPerchY + 52f * engine.dpScale, engine.floorY)
                    engine.onPillowMoved(nx, ny)
                    params.x = (nx - width * 0.5f).roundToInt()
                    params.y = (ny - height).roundToInt()
                    try {
                        windowManager.updateViewLayout(this, params)
                    } catch (_: Exception) {
                    }
                    syncCatWindowPosition()
                    effectView?.invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    CatProfileStore.save(this@CatOverlayService, engine.profile)
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }
}
