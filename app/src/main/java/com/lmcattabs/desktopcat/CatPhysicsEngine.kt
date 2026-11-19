package com.lmcattabs.desktopcat

import android.graphics.Color
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

data class OverlayParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val maxLife: Float,
    val kind: String,
    val color: Int,
    val scale: Float = 1f
)

data class FishItem(var x: Float, var y: Float, var vx: Float, var vy: Float, var age: Float = 0f)

class YarnItem(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var rotation: Float = 0f
) {
    val trailX = FloatArray(8) { x }
    val trailY = FloatArray(8) { y }
}

class CatPhysicsEngine(
    var screenW: Float,
    var screenH: Float,
    var statusBarY: Float,
    var floorY: Float,
    val dpScale: Float,
    var profile: CatProfile
) {
    val topPerchY: Float
        get() = statusBarY + 34f * dpScale

    val minCatX: Float
        get() = 34f * dpScale

    val maxCatX: Float
        get() = max(minCatX + 16f, screenW - 34f * dpScale)

    var catX: Float = screenW * 0.5f
    var catY: Float = floorY
    var vx: Float = 0f
    var vy: Float = 0f
    var facing: Int = 1
    var action: String = "IDLE_SIT"
    var perchZone: String = "bottom" // "bottom", "top", "wall_left", "wall_right", "air"
    var climbSide: Int = 0 // -1 left wall, 1 right wall, 0 none

    var animTime: Float = 0f
    var isDraggingCat: Boolean = false
    var isSleepingOnPillow: Boolean = false
    var blinkActive: Boolean = false

    private var targetX: Float? = null
    private var aiTimer: Float = 1.6f
    private var blinkTimer: Float = 0f
    private var nextBlink: Float = 2.8f
    private var zzzTimer: Float = 0.9f
    private var specialTimer: Float = 0f

    // Anti-stuck timers (fixes top-bar re-landing loop & edge stalls!)
    private var topDropIgnoreTimer: Float = 0f
    private var topStayTimer: Float = 0f
    private var moveWatchdogTimer: Float = 0f
    private var climbWatchdogTimer: Float = 0f

    // 8-sample smooth touch drag velocity history for gentle parabolic toss
    private val dragX = FloatArray(8)
    private val dragY = FloatArray(8)
    private val dragT = FloatArray(8)
    private var dragCount = 0

    // Interactive toys & particles
    val fishes = mutableListOf<FishItem>()
    var yarn: YarnItem? = null
    val particles = mutableListOf<OverlayParticle>()

    fun recommendedFrameDelayMs(): Long {
        val active = isDraggingCat ||
            action == "RUN" ||
            action == "WALK" ||
            action == "JUMP" ||
            action == "FALL" ||
            action == "CLIMB_WALL" ||
            fishes.isNotEmpty() ||
            yarn != null ||
            particles.isNotEmpty()
        return if (active) 33L else 84L // Smart Adaptive FPS: 30 FPS active, 12 FPS resting!
    }

    fun spawnFish() {
        val sx = (catX + facing * 105f * dpScale).coerceIn(48f * dpScale, screenW - 48f * dpScale)
        val sy = (floorY - 140f * dpScale).coerceAtLeast(topPerchY + 32f * dpScale)
        fishes.add(FishItem(sx, sy, facing * 85f * dpScale, -145f * dpScale))
        if (fishes.size > 4) fishes.removeAt(0)
        spawnParticles(sx, sy, "sparkle", Color.rgb(125, 211, 252), 4)
    }

    fun toggleYarn() {
        if (yarn != null) {
            yarn = null
            return
        }
        val startX = (screenW * 0.5f).coerceIn(48f * dpScale, screenW - 48f * dpScale)
        val startY = (floorY - 96f * dpScale).coerceAtLeast(topPerchY + 48f * dpScale)
        yarn = YarnItem(
            x = startX,
            y = startY,
            vx = facing * 125f * dpScale,
            vy = -110f * dpScale
        )
    }

    fun togglePillow(saveCallback: (CatProfile) -> Unit) {
        if (profile.hasPillow) {
            profile.hasPillow = false
            if (isSleepingOnPillow) {
                isSleepingOnPillow = false
                if (abs(catY - floorY) > 6f * dpScale) {
                    perchZone = "air"
                    action = "FALL"
                    vy = 20f * dpScale
                } else {
                    catY = floorY
                    perchZone = "bottom"
                    action = "IDLE_SIT"
                }
            }
        } else {
            profile.hasPillow = true
            if (profile.pillowX < 48f * dpScale || profile.pillowX > screenW - 48f * dpScale ||
                profile.pillowY < topPerchY + 54f * dpScale || profile.pillowY > floorY
            ) {
                profile.pillowX = screenW * 0.5f
                profile.pillowY = floorY
            }
            spawnParticles(profile.pillowX, profile.pillowY - 16f * dpScale, "sparkle", Color.rgb(165, 180, 252), 5)
        }
        saveCallback(profile)
    }

    fun triggerWallClimb() {
        // If already perched on top bar, hop down first instead of getting stuck at top!
        if (perchZone == "top" || catY <= topPerchY + 44f * dpScale) {
            dropFromTopBar()
            return
        }
        val left = catX < screenW * 0.5f
        climbSide = if (left) -1 else 1
        facing = climbSide
        catX = if (left) 16f * dpScale else screenW - 16f * dpScale
        catY = min(floorY - 10f * dpScale, max(topPerchY + 48f * dpScale, catY))
        perchZone = if (left) "wall_left" else "wall_right"
        action = "CLIMB_WALL"
        climbWatchdogTimer = 0f
        isSleepingOnPillow = false
        targetX = null
        vx = 0f
        vy = 0f
        spawnParticles(catX, catY - 16f * dpScale, "sparkle", Color.rgb(251, 191, 36), 4)
    }

    private fun dropFromTopBar(targetCenterBiasX: Float? = null) {
        val aimX = targetCenterBiasX ?: (screenW * 0.25f + Random.nextFloat() * screenW * 0.5f)
        vx = ((aimX - catX) * 0.85f).coerceIn(-190f * dpScale, 190f * dpScale)
        vy = -65f * dpScale
        facing = if (vx >= 0f) 1 else -1
        climbSide = 0
        topDropIgnoreTimer = 0.78f // Prevents re-catching on topPerchY while jumping down!
        topStayTimer = 0f
        targetX = null
        action = "JUMP"
        perchZone = "air"
    }

    fun onCatDragStart(rawX: Float, rawY: Float) {
        isDraggingCat = true
        isSleepingOnPillow = false
        action = "DANGLE"
        perchZone = "air"
        climbSide = 0
        targetX = null
        vx = 0f
        vy = 0f
        dragCount = 1
        dragX[0] = rawX
        dragY[0] = rawY
        dragT[0] = animTime
    }

    fun onCatDragMove(rawX: Float, rawY: Float) {
        if (!isDraggingCat) return
        catX = rawX.coerceIn(18f * dpScale, screenW - 18f * dpScale)
        catY = (rawY + 26f * dpScale).coerceIn(topPerchY, floorY)

        if (dragCount < dragX.size) {
            dragX[dragCount] = rawX
            dragY[dragCount] = rawY
            dragT[dragCount] = animTime
            dragCount++
        } else {
            for (i in 0 until dragX.size - 1) {
                dragX[i] = dragX[i + 1]
                dragY[i] = dragY[i + 1]
                dragT[i] = dragT[i + 1]
            }
            dragX[dragX.size - 1] = rawX
            dragY[dragY.size - 1] = rawY
            dragT[dragT.size - 1] = animTime
        }
    }

    fun onCatDragEnd() {
        if (!isDraggingCat) return
        isDraggingCat = false

        // Drop onto Sleep Pillow -> curl up right on top of the cushion!
        if (profile.hasPillow && abs(catX - profile.pillowX) < 54f * dpScale && abs(catY - profile.pillowY) < 48f * dpScale) {
            catX = profile.pillowX
            catY = profile.pillowY - 8f * dpScale
            isSleepingOnPillow = true
            action = "SLEEP_LOAF"
            perchZone = "bottom"
            vx = 0f
            vy = 0f
            spawnParticles(catX, catY - 24f * dpScale, "zzz", Color.rgb(56, 189, 248), 2)
            return
        }

        // Compute smooth drag release velocity
        var rawVx = 0f
        var rawVy = 0f
        if (dragCount >= 2) {
            val latest = dragCount - 1
            var oldest = 0
            for (i in latest - 1 downTo 0) {
                if (dragT[latest] - dragT[i] >= 0.025f) {
                    oldest = i
                    if (dragT[latest] - dragT[i] >= 0.09f) break
                }
            }
            val dt = dragT[latest] - dragT[oldest]
            if (dt > 0.015f) {
                rawVx = (dragX[latest] - dragX[oldest]) / dt
                rawVy = (dragY[latest] - dragY[oldest]) / dt
            }
        }
        val speed = hypot(rawVx, rawVy)
        val tossThreshold = 135f * dpScale

        if (speed >= tossThreshold) {
            // Gentle parabolic toss!
            val maxVx = 340f * dpScale
            val gentleVx = (rawVx * 0.42f).coerceIn(-maxVx, maxVx)
            var gentleVy = (rawVy * 0.35f).coerceIn(-370f * dpScale, 150f * dpScale)
            if (gentleVy > -115f * dpScale) {
                gentleVy = -(140f * dpScale + min(125f * dpScale, abs(gentleVx) * 0.34f))
            }
            vx = gentleVx
            vy = gentleVy
            if (abs(vx) > 20f) facing = if (vx > 0f) 1 else -1
            if (catY <= topPerchY + 24f * dpScale) topDropIgnoreTimer = 0.5f
            action = "JUMP"
            perchZone = "air"
            spawnParticles(catX, catY - 12f * dpScale, "sparkle", Color.rgb(125, 211, 252), 4)
            return
        }

        // Dropped close to top status-bar edge -> perch neatly on top edge!
        if (abs(catY - topPerchY) <= 38f * dpScale) {
            catX = catX.coerceIn(minCatX, maxCatX)
            catY = topPerchY
            perchZone = "top"
            topStayTimer = 0f
            action = "IDLE_SIT"
            vx = 0f
            vy = 0f
            aiTimer = 2.0f
            return
        }

        // Dropped close to left/right screen wall (and well below top bar) -> climb wall!
        if ((catX <= 30f * dpScale || catX >= screenW - 30f * dpScale) && catY > topPerchY + 52f * dpScale) {
            climbSide = if (catX <= 30f * dpScale) -1 else 1
            facing = climbSide
            catX = if (climbSide < 0) 16f * dpScale else screenW - 16f * dpScale
            perchZone = if (climbSide < 0) "wall_left" else "wall_right"
            action = "CLIMB_WALL"
            climbWatchdogTimer = 0f
            return
        }

        // Otherwise always fall cleanly under gravity to the floor!
        catX = catX.coerceIn(minCatX, maxCatX)
        action = "FALL"
        perchZone = "air"
        vy = 25f * dpScale
    }

    fun onCatTapPet() {
        // NEVER freeze an airborne/climbing cat in mid-air when tapped!
        val isGrounded = (perchZone == "bottom" || perchZone == "top" || isSleepingOnPillow) &&
            action != "JUMP" && action != "FALL" && action != "DANGLE" && action != "CLIMB_WALL"
        if (!isGrounded) {
            spawnParticles(catX, catY - 26f * dpScale, "heart", Color.rgb(251, 113, 133), 2)
            return
        }
        isSleepingOnPillow = false
        if (perchZone == "bottom") catY = floorY
        else if (perchZone == "top") catY = topPerchY
        action = "HAPPY_PURR"
        specialTimer = 1.6f
        spawnParticles(catX, catY - 30f * dpScale, "heart", Color.rgb(251, 113, 133), 3)
    }

    fun onPillowMoved(nx: Float, ny: Float) {
        profile.pillowX = nx
        profile.pillowY = ny
        if (isSleepingOnPillow) {
            catX = nx
            catY = ny - 8f * dpScale
        }
    }

    fun update(dtRaw: Float) {
        val dt = dtRaw.coerceIn(0.004f, 0.06f)
        animTime += dt
        if (topDropIgnoreTimer > 0f) topDropIgnoreTimer = max(0f, topDropIgnoreTimer - dt)

        // Blinking
        nextBlink -= dt
        if (nextBlink <= 0f) {
            blinkTimer = 0.14f
            nextBlink = 2.4f + Random.nextFloat() * 3.0f
        }
        if (blinkTimer > 0f) {
            blinkTimer -= dt
            blinkActive = true
        } else {
            blinkActive = false
        }

        updateParticles(dt)
        updateFishes(dt)
        updateYarn(dt)

        if (isDraggingCat) return

        // =========================================================================
        // HARD GROUND INVARIANT: Cat can NEVER sit, walk, purr, or sleep in mid-air!
        // =========================================================================
        val isPillowValid = isSleepingOnPillow && profile.hasPillow &&
            abs(catX - profile.pillowX) <= 56f * dpScale &&
            abs(catY - (profile.pillowY - 8f * dpScale)) <= 16f * dpScale
        val isOnBottom = (perchZone == "bottom") && abs(catY - floorY) <= 8f * dpScale
        val isOnTop = (perchZone == "top") && abs(catY - topPerchY) <= 8f * dpScale
        val needsGround = action != "JUMP" && action != "FALL" && action != "DANGLE" && action != "CLIMB_WALL"

        if (needsGround && !isPillowValid && !isOnBottom && !isOnTop) {
            isSleepingOnPillow = false
            climbSide = 0
            targetX = null
            perchZone = "air"
            action = "FALL"
            vy = max(vy, 25f * dpScale)
        } else if (isOnBottom && !isPillowValid && needsGround) {
            catY = floorY
        } else if (isOnTop && needsGround) {
            catY = topPerchY
            topStayTimer += dt
            // Anti-stuck watchdog: never stay trapped on top bar forever!
            if (topStayTimer >= 9.5f) {
                dropFromTopBar()
                return
            }
        }

        // Sleep Zzz emitter
        if (action == "SLEEP_LOAF") {
            zzzTimer -= dt
            if (zzzTimer <= 0f) {
                zzzTimer = 1.25f
                spawnParticles(catX + facing * 12f * dpScale, catY - 22f * dpScale, "zzz", Color.rgb(56, 189, 248), 1)
            }
        }

        // Wall Climbing -> climbs straight UP phone side wall (head up!) and vaults onto top status-bar edge!
        if (action == "CLIMB_WALL") {
            climbWatchdogTimer += dt
            if (climbSide == 0) climbSide = if (catX < screenW * 0.5f) -1 else 1
            facing = climbSide
            catX = if (climbSide < 0) 16f * dpScale else screenW - 16f * dpScale
            catY -= 118f * dpScale * dt
            if (catY <= topPerchY) {
                catY = topPerchY
                catX = if (climbSide < 0) minCatX + 12f * dpScale else maxCatX - 12f * dpScale
                facing = if (climbSide < 0) 1 else -1
                climbSide = 0
                perchZone = "top"
                topStayTimer = 0f
                action = "IDLE_SIT"
                aiTimer = 1.6f
                spawnParticles(catX, catY, "sparkle", Color.rgb(251, 191, 36), 4)
            } else if (climbWatchdogTimer > 8.5f) {
                // Safety escape if wall climb ever stalls
                climbSide = 0
                perchZone = "air"
                action = "FALL"
                vx = if (catX < screenW * 0.5f) 90f * dpScale else -90f * dpScale
                vy = 0f
            }
            return
        }

        // Timed happy/stretch actions
        if (action == "HAPPY_PURR" || action == "BIG_STRETCH") {
            specialTimer -= dt
            if (specialTimer <= 0f) {
                action = "IDLE_SIT"
                aiTimer = 1.6f
            }
            return
        }

        // Sleeping on Pillow
        if (isSleepingOnPillow && profile.hasPillow) {
            catX = profile.pillowX
            catY = profile.pillowY - 8f * dpScale
            action = "SLEEP_LOAF"
            if (fishes.isNotEmpty()) {
                isSleepingOnPillow = false
                if (abs(catY - floorY) > 10f * dpScale) {
                    perchZone = "air"
                    action = "FALL"
                    vy = 15f * dpScale
                } else {
                    catY = floorY
                    perchZone = "bottom"
                    action = "RUN"
                }
            }
            return
        }

        // Airborne parabolic flight (Jump / Toss / Fall)
        if (action == "JUMP" || action == "FALL") {
            val prevY = catY
            val gravity = 780f * dpScale
            vy += gravity * dt
            catX += vx * dt
            catY += vy * dt

            // Bounce gently off side walls
            if (catX < minCatX) {
                catX = minCatX
                vx = abs(vx) * 0.45f
                facing = 1
            } else if (catX > maxCatX) {
                catX = maxCatX
                vx = -abs(vx) * 0.45f
                facing = -1
            }

            // Soft ceiling clamp so cat never flies off-screen
            if (catY < statusBarY + 12f * dpScale) {
                catY = statusBarY + 12f * dpScale
                if (vy < 0f) vy = 20f * dpScale
            }

            // Land on Top Status-Bar Edge ONLY when descending AND not dropping down from top!
            if (topDropIgnoreTimer <= 0f && vy > 0f && prevY <= topPerchY + 8f * dpScale && catY >= topPerchY && catY <= topPerchY + 22f * dpScale) {
                catY = topPerchY
                vx = 0f
                vy = 0f
                perchZone = "top"
                topStayTimer = 0f
                action = "IDLE_SIT"
                aiTimer = 1.8f
                spawnParticles(catX, catY, "dust", Color.rgb(203, 213, 225), 3)
                return
            }

            // Land on Sleep Pillow if descending onto it
            if (vy > 0f && profile.hasPillow && abs(catX - profile.pillowX) <= 46f * dpScale) {
                val py = profile.pillowY - 8f * dpScale
                if (prevY <= py + 12f * dpScale && catY >= py) {
                    catX = profile.pillowX
                    catY = py
                    vx = 0f
                    vy = 0f
                    isSleepingOnPillow = true
                    action = "SLEEP_LOAF"
                    perchZone = "bottom"
                    spawnParticles(catX, catY - 20f * dpScale, "zzz", Color.rgb(56, 189, 248), 2)
                    return
                }
            }

            // Land on bottom floor
            if (catY >= floorY) {
                catY = floorY
                vx = 0f
                vy = 0f
                perchZone = "bottom"
                action = "IDLE_SIT"
                aiTimer = 1.4f
                spawnParticles(catX, catY, "dust", Color.rgb(203, 213, 225), 3)
            }
            return
        }

        // Chase fish treats if any exist!
        if (fishes.isNotEmpty()) {
            val f = fishes.first()
            if (perchZone == "top") {
                dropFromTopBar(f.x)
                return
            }
            val safeFishX = f.x.coerceIn(minCatX, maxCatX)
            val dx = safeFishX - catX
            facing = if (dx >= 0f) 1 else -1
            action = "RUN"
            val runSpeed = 235f * dpScale
            catX = (catX + facing * runSpeed * dt).coerceIn(minCatX, maxCatX)
            catY = floorY
            if (abs(f.x - catX) < 34f * dpScale && abs(f.y - catY) < 46f * dpScale) {
                fishes.removeAt(0)
                action = "HAPPY_PURR"
                specialTimer = 1.4f
                spawnParticles(catX, catY - 28f * dpScale, "heart", Color.rgb(251, 113, 133), 4)
            }
            return
        }

        // Bat yarn ball playfully!
        yarn?.let { yBall ->
            if (perchZone == "bottom" && abs(yBall.y - floorY) < 62f * dpScale) {
                val dx = yBall.x - catX
                if (abs(dx) < 38f * dpScale) {
                    yBall.vx = (if (dx >= 0f) 1f else -1f) * 255f * dpScale
                    yBall.vy = -165f * dpScale
                    spawnParticles(yBall.x, yBall.y, "sparkle", Color.rgb(251, 191, 36), 3)
                } else if (action != "RUN") {
                    targetX = yBall.x.coerceIn(minCatX + 8f * dpScale, maxCatX - 8f * dpScale)
                    moveWatchdogTimer = 0f
                    action = "RUN"
                }
            }
        }

        // Walking / Running toward clamped interior targetX (with anti-stuck watchdog!)
        val tx = targetX
        if ((action == "WALK" || action == "RUN") && tx != null) {
            moveWatchdogTimer += dt
            val clampedTx = tx.coerceIn(minCatX + 6f * dpScale, maxCatX - 6f * dpScale)
            val dx = clampedTx - catX
            if (abs(dx) <= 10f * dpScale || moveWatchdogTimer > 4.5f) {
                targetX = null
                moveWatchdogTimer = 0f
                action = "IDLE_SIT"
                aiTimer = 1.5f + Random.nextFloat() * 2.0f
            } else {
                facing = if (dx > 0f) 1 else -1
                val speed = (if (action == "RUN") 210f else 90f) * dpScale
                catX = (catX + facing * speed * dt).coerceIn(minCatX, maxCatX)
            }
            return
        }

        // Autonomous Mood AI
        aiTimer -= dt
        if (aiTimer > 0f) return

        val roll = Random.nextFloat()
        if (profile.mood == "SLEEPY") {
            if (profile.hasPillow && perchZone == "bottom" && abs(profile.pillowY - floorY) < 28f * dpScale && roll < 0.65f) {
                if (abs(catX - profile.pillowX) < 36f * dpScale) {
                    isSleepingOnPillow = true
                    catX = profile.pillowX
                    catY = profile.pillowY - 8f * dpScale
                    action = "SLEEP_LOAF"
                } else {
                    targetX = profile.pillowX.coerceIn(minCatX + 8f * dpScale, maxCatX - 8f * dpScale)
                    moveWatchdogTimer = 0f
                    action = "WALK"
                }
                aiTimer = 3.0f
                return
            }
            if (perchZone == "top" && roll > 0.6f) {
                dropFromTopBar()
                return
            }
            action = if (roll < 0.7f) "SLEEP_LOAF" else "BIG_STRETCH"
            specialTimer = 2.0f
            aiTimer = 4.2f
            return
        }

        if (perchZone == "top") {
            if (roll < 0.48f && topStayTimer < 6.5f) {
                targetX = (minCatX + 12f * dpScale) + Random.nextFloat() * max(20f, (maxCatX - minCatX - 24f * dpScale))
                moveWatchdogTimer = 0f
                action = "WALK"
            } else {
                dropFromTopBar()
            }
            aiTimer = 2.0f
            return
        }

        when {
            roll < 0.20f -> {
                triggerWallClimb()
            }
            roll < 0.54f -> {
                targetX = (minCatX + 10f * dpScale) + Random.nextFloat() * max(20f, (maxCatX - minCatX - 20f * dpScale))
                moveWatchdogTimer = 0f
                action = if (profile.mood == "CHAOTIC" || profile.mood == "HUNTER" || Random.nextFloat() < 0.4f) "RUN" else "WALK"
            }
            roll < 0.72f -> {
                action = "BIG_STRETCH"
                specialTimer = 1.9f
            }
            roll < 0.86f && profile.hasPillow && abs(profile.pillowY - floorY) < 28f * dpScale -> {
                targetX = profile.pillowX.coerceIn(minCatX + 8f * dpScale, maxCatX - 8f * dpScale)
                moveWatchdogTimer = 0f
                action = "WALK"
            }
            else -> {
                action = "IDLE_SIT"
                aiTimer = 1.8f + Random.nextFloat() * 1.8f
            }
        }
    }

    private fun updateFishes(dt: Float) {
        val iter = fishes.iterator()
        while (iter.hasNext()) {
            val f = iter.next()
            f.age += dt
            f.vy += 680f * dpScale * dt
            f.x += f.vx * dt
            f.y += f.vy * dt
            if (f.y >= floorY - 8f * dpScale) {
                f.y = floorY - 8f * dpScale
                f.vy = -f.vy * 0.35f
                f.vx *= 0.78f
            }
            val margin = 28f * dpScale
            if (f.x < margin) {
                f.x = margin
                f.vx = abs(f.vx)
            } else if (f.x > screenW - margin) {
                f.x = screenW - margin
                f.vx = -abs(f.vx)
            }
            if (f.age > 14f) iter.remove()
        }
    }

    private fun updateYarn(dt: Float) {
        val yBall = yarn ?: return
        yBall.vy += 650f * dpScale * dt
        yBall.x += yBall.vx * dt
        yBall.y += yBall.vy * dt
        yBall.rotation += (yBall.vx * dt) / (14f * dpScale)
        val r = 13f * dpScale
        if (yBall.y >= floorY - r) {
            yBall.y = floorY - r
            yBall.vy = -yBall.vy * 0.52f
            yBall.vx *= 0.92f
        }
        val minY = topPerchY + r
        if (yBall.y < minY) {
            yBall.y = minY
            yBall.vy = abs(yBall.vy) * 0.4f
        }
        if (yBall.x < r + 8f * dpScale) {
            yBall.x = r + 8f * dpScale
            yBall.vx = abs(yBall.vx) * 0.75f
        } else if (yBall.x > screenW - r - 8f * dpScale) {
            yBall.x = screenW - r - 8f * dpScale
            yBall.vx = -abs(yBall.vx) * 0.75f
        }

        // Simulate 8-node trailing wool string
        yBall.trailX[0] = yBall.x
        yBall.trailY[0] = yBall.y + r * 0.3f
        val segLen = 5.5f * dpScale
        for (i in 1 until yBall.trailX.size) {
            yBall.trailY[i] = min(floorY - 2f * dpScale, yBall.trailY[i] + 120f * dpScale * dt)
            val dx = yBall.trailX[i] - yBall.trailX[i - 1]
            val dy = yBall.trailY[i] - yBall.trailY[i - 1]
            val dist = hypot(dx, dy).coerceAtLeast(0.01f)
            if (dist > segLen) {
                val pull = (dist - segLen) / dist
                yBall.trailX[i] -= dx * pull
                yBall.trailY[i] -= dy * pull
            }
        }
    }

    private fun updateParticles(dt: Float) {
        val iter = particles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.life -= dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            if (p.life <= 0f) iter.remove()
        }
    }

    fun spawnParticles(x: Float, y: Float, kind: String, color: Int, count: Int) {
        for (i in 0 until count) {
            particles.add(
                OverlayParticle(
                    x = x + (Random.nextFloat() - 0.5f) * 20f * dpScale,
                    y = y + (Random.nextFloat() - 0.5f) * 10f * dpScale,
                    vx = (Random.nextFloat() - 0.5f) * 35f * dpScale,
                    vy = -(28f + Random.nextFloat() * 34f) * dpScale,
                    life = 0.9f + Random.nextFloat() * 0.5f,
                    maxLife = 1.4f,
                    kind = kind,
                    color = color,
                    scale = 0.9f + Random.nextFloat() * 0.35f
                )
            )
        }
    }
}
