package com.lmcattabs.desktopcat

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class CoatPalette(
    val id: String,
    val label: String,
    val pattern: String,
    val outline: Int,
    val primary: Int,
    val highlight: Int,
    val midShadow: Int,
    val secondary: Int,
    val belly: Int,
    val bellyShadow: Int,
    val earInner: Int,
    val nose: Int,
    val pawPad: Int,
    val eyeOuter: Int,
    val eyeColor: Int,
    val eyeHighlight: Int
)

object CatPalettes {
    private fun hex(s: String): Int = Color.parseColor(s)

    val ALL: List<CoatPalette> = listOf(
        CoatPalette(
            id = "pamuk", label = "Pamuk", pattern = "solid_white",
            outline = hex("#0d152c"), primary = hex("#f5f9ff"), highlight = hex("#ffffff"),
            midShadow = hex("#d0e4f7"), secondary = hex("#a9c8ea"), belly = hex("#ffffff"),
            bellyShadow = hex("#dceaf8"), earInner = hex("#b8d4f0"), nose = hex("#ff6b98"),
            pawPad = hex("#ff8fb3"), eyeOuter = hex("#0b2c7a"), eyeColor = hex("#1d6cf5"), eyeHighlight = hex("#56d2ff")
        ),
        CoatPalette(
            id = "marmalade", label = "Sarı Tekir", pattern = "tabby",
            outline = hex("#1d130f"), primary = hex("#f7a44a"), highlight = hex("#ffcb85"),
            midShadow = hex("#df812e"), secondary = hex("#bd5c17"), belly = hex("#fff4dc"),
            bellyShadow = hex("#e9d2b0"), earInner = hex("#f6a6b2"), nose = hex("#f46d86"),
            pawPad = hex("#f792a6"), eyeOuter = hex("#155e2f"), eyeColor = hex("#22c55e"), eyeHighlight = hex("#a3e635")
        ),
        CoatPalette(
            id = "domino", label = "Smokin", pattern = "tuxedo",
            outline = hex("#090b12"), primary = hex("#313a52"), highlight = hex("#515e80"),
            midShadow = hex("#1f2638"), secondary = hex("#171c2b"), belly = hex("#f4f7fc"),
            bellyShadow = hex("#cbd5e6"), earInner = hex("#ea99ad"), nose = hex("#fb7185"),
            pawPad = hex("#f472b6"), eyeOuter = hex("#8a4b08"), eyeColor = hex("#f59e0b"), eyeHighlight = hex("#fde047")
        ),
        CoatPalette(
            id = "mochi", label = "Mochi", pattern = "calico",
            outline = hex("#1b1312"), primary = hex("#fff7ed"), highlight = hex("#ffffff"),
            midShadow = hex("#e7d8c9"), secondary = hex("#f28b39"), belly = hex("#2f354a"),
            bellyShadow = hex("#1e2333"), earInner = hex("#f7a8b8"), nose = hex("#fb7185"),
            pawPad = hex("#fda4af"), eyeOuter = hex("#0e5a7a"), eyeColor = hex("#0ea5e9"), eyeHighlight = hex("#7dd3fc")
        ),
        CoatPalette(
            id = "siamese", label = "Siyam", pattern = "siamese",
            outline = hex("#1b1412"), primary = hex("#f5e8d7"), highlight = hex("#fffaf2"),
            midShadow = hex("#d7c3ab"), secondary = hex("#5a4337"), belly = hex("#fdf4e8"),
            bellyShadow = hex("#dfd0bc"), earInner = hex("#b98a7c"), nose = hex("#5a4337"),
            pawPad = hex("#735749"), eyeOuter = hex("#0c348c"), eyeColor = hex("#2563eb"), eyeHighlight = hex("#60d5ff")
        ),
        CoatPalette(
            id = "silver", label = "Gümüş", pattern = "silver_tabby",
            outline = hex("#101622"), primary = hex("#cdd7e3"), highlight = hex("#eef4fa"),
            midShadow = hex("#9eaec2"), secondary = hex("#64748b"), belly = hex("#f8fafc"),
            bellyShadow = hex("#cbd5e1"), earInner = hex("#f2a4b6"), nose = hex("#f4728d"),
            pawPad = hex("#f9a8b8"), eyeOuter = hex("#0f5e42"), eyeColor = hex("#10b981"), eyeHighlight = hex("#6ee7b7")
        )
    )

    fun byId(id: String): CoatPalette = ALL.firstOrNull { it.id == id } ?: ALL.first()
}

private data class ArticulatedCatLeg(
    val hipX: Float,
    val hipY: Float,
    val jointX: Float,
    val jointY: Float,
    val footX: Float,
    val footY: Float,
    val isHind: Boolean,
    val lift: Float
)

/* ============================================================================
 * 1:1 PORT OF PC HIGH-DETAIL CHIBI PIXEL CAT & 3 PIXEL TOYS (PILLOW/FISH/YARN)
 * ========================================================================== */
class CatSpriteRenderer {
    private val paint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
        style = Paint.Style.FILL
    }
    private val ropePaint = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun plot(canvas: Canvas, px: Float, gx: Float, gy: Float, color: Int, w: Float = 1f, h: Float = 1f) {
        paint.color = color
        val x0 = (gx * px).roundToInt().toFloat()
        val y0 = (gy * px).roundToInt().toFloat()
        val x1 = max(x0 + 1f, ((gx + w) * px).roundToInt().toFloat())
        val y1 = max(y0 + 1f, ((gy + h) * px).roundToInt().toFloat())
        canvas.drawRect(x0, y0, x1, y1, paint)
    }

    private fun plotPixelOval(canvas: Canvas, px: Float, cx: Float, cy: Float, rx: Float, ry: Float, color: Int) {
        if (rx <= 0.1f || ry <= 0.1f) return
        val minY = ceil(cy - ry).toInt()
        val maxY = floor(cy + ry).toInt()
        for (y in minY..maxY) {
            val dy = (y - cy) / ry
            val inside = max(0f, 1f - dy * dy)
            val span = sqrt(inside) * rx
            val x0 = (cx - span).roundToInt()
            val x1 = (cx + span).roundToInt()
            if (x1 > x0) {
                plot(canvas, px, x0.toFloat(), y.toFloat(), color, (x1 - x0).toFloat(), 1f)
            }
        }
    }

    private fun plotShadedBlob(
        canvas: Canvas,
        px: Float,
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
        pal: CoatPalette,
        shadowSide: String,
        isBelly: Boolean
    ) {
        plotPixelOval(canvas, px, cx, cy, rx + 1.15f, ry + 1.15f, pal.outline)
        plotPixelOval(canvas, px, cx, cy, rx, ry, if (isBelly) pal.bellyShadow else pal.midShadow)
        val ox = if (shadowSide == "left") 0.9f else -0.9f
        val oy = if (shadowSide == "bottom") -1.1f else -0.8f
        plotPixelOval(
            canvas, px, cx + ox, cy + oy,
            max(1f, rx - 1.0f), max(1f, ry - 1.0f),
            if (isBelly) pal.belly else pal.primary
        )
        if (!isBelly && rx >= 4f) {
            plotPixelOval(
                canvas, px, cx - rx * 0.28f, cy - ry * 0.42f,
                max(1f, rx * 0.45f), max(1f, ry * 0.28f),
                pal.highlight
            )
        }
    }

    private fun plotTaperedSegment(
        canvas: Canvas,
        px: Float,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        r0: Float,
        r1: Float,
        color: Int,
        steps: Int
    ) {
        for (i in 0..steps) {
            val t = i.toFloat() / steps.toFloat()
            val cx = x0 + (x1 - x0) * t
            val cy = y0 + (y1 - y0) * t
            val r = r0 + (r1 - r0) * t
            plotPixelOval(canvas, px, cx, cy, r, r * 0.92f, color)
        }
    }

    private fun sampleCatWalkLeg(hipX: Float, hipY: Float, cycleU: Float, stride: Float, isHind: Boolean): ArticulatedCatLeg {
        val u = ((cycleU % 1f) + 1f) % 1f
        val swingFrac = 0.40f
        val offsetX: Float
        val lift: Float
        val swingBend: Float
        if (u < swingFrac) {
            val s = u / swingFrac
            val smoothS = s * s * (3f - 2f * s)
            offsetX = (-1f + 2f * smoothS) * stride
            lift = sin(s * PI.toFloat()) * (if (isHind) 2.5f else 2.8f)
            swingBend = sin(s * PI.toFloat())
        } else {
            val p = (u - swingFrac) / (1f - swingFrac)
            offsetX = (1f - 2f * p) * stride
            lift = 0f
            swingBend = 0f
        }
        val footX = hipX + offsetX + (if (isHind) -0.3f else 0.4f)
        val footY = -1.2f - lift
        val jointX: Float
        val jointY: Float
        if (isHind) {
            jointX = max(hipX - 1.15f, hipX * 0.52f + footX * 0.48f - 0.55f + swingBend * 0.6f)
            jointY = hipY * 0.48f + footY * 0.52f + 0.30f - swingBend * 0.55f
        } else {
            jointX = hipX * 0.50f + footX * 0.50f - 0.55f - swingBend * 0.90f
            jointY = hipY * 0.52f + footY * 0.48f + 0.10f - swingBend * 0.55f
        }
        return ArticulatedCatLeg(hipX, hipY, jointX, jointY, footX, footY, isHind, lift)
    }

    private fun sampleAgileRunLeg(
        hipX: Float,
        hipY: Float,
        cycle: Float,
        phaseOffset: Float,
        isHind: Boolean,
        strideAmp: Float,
        loftLift: Float
    ): ArticulatedCatLeg {
        val u = (((cycle + phaseOffset) % 1f) + 1f) % 1f
        val a = u * PI.toFloat() * 2f
        val sinA = sin(a)
        val cosA = cos(a)
        val sweep = cosA + sin(2f * a) * (if (isHind) -0.14f else 0.12f)
        val swingLift = max(0f, -sinA)
        val pushTail = max(0f, -cosA) * max(0f, sinA) * 0.45f

        val centerX = if (isHind) hipX - 1.4f else hipX + 2.3f
        val footX = centerX + sweep * strideAmp
        val groundY = -1.2f - max(0f, loftLift) * 1.15f
        val liftAmt = swingLift * (if (isHind) 4.8f else 4.5f) + pushTail * 2.1f
        val footY = min(-1.1f, groundY - liftAmt)

        val midX = (hipX + footX) * 0.5f
        val midY = (hipY + footY) * 0.5f
        val bendDir = if (isHind) -1f else 1f
        val foldCurve = if (isHind) {
            2.25f + swingLift * 1.15f - max(0f, -cosA) * 0.55f
        } else {
            1.55f + swingLift * 1.45f - max(0f, cosA) * 0.45f
        }
        val jointX = midX + bendDir * foldCurve
        val jointY = midY + (if (isHind) (-0.45f - swingLift * 0.75f) else (0.55f - swingLift * 1.15f))
        return ArticulatedCatLeg(hipX, hipY, jointX, jointY, footX, footY, isHind, liftAmt)
    }

    private fun plotArticulatedLegOutline(canvas: Canvas, px: Float, leg: ArticulatedCatLeg, outline: Int) {
        val rHip = if (leg.isHind) 3.15f else 2.95f
        val rJoint = if (leg.isHind) 2.05f else 2.20f
        val rAnkle = 2.00f
        plotTaperedSegment(canvas, px, leg.hipX, leg.hipY, leg.jointX, leg.jointY, rHip, rJoint, outline, 5)
        plotTaperedSegment(canvas, px, leg.jointX, leg.jointY, leg.footX, leg.footY, rJoint, rAnkle, outline, 5)
        plotPixelOval(canvas, px, leg.footX + 0.95f, leg.footY + 0.05f, 2.6f, 1.90f, outline)
    }

    private fun plotArticulatedLegFill(canvas: Canvas, px: Float, leg: ArticulatedCatLeg, pal: CoatPalette, isFar: Boolean) {
        val rHip = if (leg.isHind) 2.25f else 2.05f
        val rJoint = if (leg.isHind) 1.22f else 1.32f
        val rAnkle = 1.12f
        if (isFar) {
            plotTaperedSegment(canvas, px, leg.hipX, leg.hipY, leg.jointX, leg.jointY, rHip, rJoint, pal.midShadow, 5)
            plotTaperedSegment(canvas, px, leg.jointX, leg.jointY, leg.footX, leg.footY, rJoint, rAnkle, pal.midShadow, 5)
            plotPixelOval(canvas, px, leg.footX + 0.95f, leg.footY + 0.05f, 1.70f, 1.10f, pal.bellyShadow)
            return
        }
        plotTaperedSegment(canvas, px, leg.hipX, leg.hipY, leg.jointX, leg.jointY, rHip, rJoint, pal.midShadow, 5)
        plotTaperedSegment(canvas, px, leg.jointX, leg.jointY, leg.footX, leg.footY, rJoint, rAnkle, pal.midShadow, 5)
        plotTaperedSegment(canvas, px, leg.hipX - 0.30f, leg.hipY - 0.3f, leg.jointX - 0.20f, leg.jointY - 0.2f, max(0.95f, rHip - 0.45f), max(0.80f, rJoint - 0.28f), pal.primary, 5)
        plotTaperedSegment(canvas, px, leg.jointX - 0.18f, leg.jointY - 0.15f, leg.footX - 0.15f, leg.footY - 0.15f, max(0.80f, rJoint - 0.28f), max(0.72f, rAnkle - 0.24f), pal.primary, 5)
        plotPixelOval(canvas, px, leg.footX + 0.95f, leg.footY + 0.05f, 1.80f, 1.15f, pal.belly)
        plot(canvas, px, (leg.footX + 1.8f).roundToInt().toFloat(), (leg.footY + 0.3f).roundToInt().toFloat(), pal.outline, 1f, 1f)
    }

    private fun drawSmoothSTail(
        canvas: Canvas,
        px: Float,
        pal: CoatPalette,
        mood: String,
        action: String,
        tAnim: Float,
        bx: Float,
        by: Float
    ) {
        val speed = when (mood) {
            "CHAOTIC", "HUNTER" -> 8.8f
            "AFFECTIONATE" -> 4.4f
            "CHILL" -> 2.4f
            else -> 3.4f
        }
        val segs = 10
        val xs = FloatArray(segs)
        val ys = FloatArray(segs)
        val rs = FloatArray(segs)

        for (i in 0 until segs) {
            val t = i.toFloat() / (segs - 1).toFloat()
            val wave = sin(tAnim * speed - i * 0.45f) * (0.6f + t * 1.9f)
            when (action) {
                "RUN" -> {
                    val gallopW = tAnim * 3.05f * PI.toFloat() * 2f
                    val tailWave = sin(gallopW - t * 3.6f) * (0.7f + t * 2.3f)
                    val liftArc = sin(t * PI.toFloat()) * 1.8f + t * t * 1.6f
                    xs[i] = bx - t * 14.2f
                    ys[i] = by - 0.6f - liftArc + tailWave
                }
                "JUMP", "FALL" -> {
                    xs[i] = bx - t * 13f
                    ys[i] = by + (if (action == "JUMP") t * 6f else -t * 7f) + wave * 0.4f
                }
                else -> {
                    val sCurve = sin(t * PI.toFloat() * 1.35f) * 3.2f
                    xs[i] = bx - t * 7.6f + sCurve + wave * 0.55f
                    ys[i] = by - t * 16.5f
                }
            }
            rs[i] = 1.95f - t * 0.25f + (if (i == segs - 1) 0.2f else 0f)
        }

        for (i in 0 until segs) plotPixelOval(canvas, px, xs[i], ys[i], rs[i] + 1.05f, rs[i] + 1.05f, pal.outline)
        for (i in 0 until segs) {
            val isStripe = (pal.pattern == "tabby" || pal.pattern == "silver_tabby") && (i % 2 == 1)
            val c = when {
                pal.pattern == "siamese" && i >= 3 -> pal.secondary
                isStripe -> pal.secondary
                else -> pal.midShadow
            }
            plotPixelOval(canvas, px, xs[i], ys[i], rs[i], rs[i], c)
        }
        for (i in 0 until segs) {
            val isStripe = (pal.pattern == "tabby" || pal.pattern == "silver_tabby") && (i % 2 == 1)
            if (!isStripe && !(pal.pattern == "siamese" && i >= 4)) {
                plotPixelOval(
                    canvas, px, xs[i] - 0.45f, ys[i] - 0.45f,
                    max(0.8f, rs[i] - 0.55f), max(0.8f, rs[i] - 0.55f),
                    if (i >= segs - 3) pal.highlight else pal.primary
                )
            }
        }
    }

    private fun drawEar(canvas: Canvas, px: Float, pal: CoatPalette, ex: Float, ey: Float, lean: Float, calicoSpot: Boolean) {
        val earMain = if (pal.pattern == "siamese") pal.secondary else pal.primary
        val earShadow = if (pal.pattern == "siamese") pal.outline else pal.midShadow
        for (row in 0 until 7) {
            val t = row / 6f
            val halfW = 0.8f + t * 2.9f
            val rx = ex + lean * (1f - t)
            val ry = ey + row
            plot(canvas, px, (rx - halfW - 1f).roundToInt().toFloat(), ry.roundToInt().toFloat(), pal.outline, (halfW * 2f + 2f).roundToInt().toFloat(), 1f)
        }
        plot(canvas, px, (ex + lean - 1f).roundToInt().toFloat(), (ey - 1f).roundToInt().toFloat(), pal.outline, 2f, 1f)

        for (row in 0 until 7) {
            val t = row / 6f
            val halfW = 0.6f + t * 2.5f
            val rx = ex + lean * (1f - t)
            val ry = ey + row
            val fill = if (calicoSpot) pal.secondary else if (row < 2) pal.highlight else earMain
            plot(canvas, px, (rx - halfW).roundToInt().toFloat(), ry.roundToInt().toFloat(), fill, max(1f, (halfW * 2f).roundToInt().toFloat()), 1f)
            if (row in 2..5) {
                plot(canvas, px, (rx - halfW * 0.45f).roundToInt().toFloat(), ry.roundToInt().toFloat(), pal.earInner, max(1f, halfW.roundToInt().toFloat()), 1f)
            } else if (row == 6) {
                plot(canvas, px, (rx - halfW * 0.6f).roundToInt().toFloat(), ry.roundToInt().toFloat(), earShadow, max(1f, (halfW * 1.2f).roundToInt().toFloat()), 1f)
            }
        }
    }

    private fun drawGlossyEye(canvas: Canvas, px: Float, pal: CoatPalette, ex: Int, ey: Int, isDilated: Boolean) {
        plot(canvas, px, ex.toFloat(), (ey - 1).toFloat(), pal.outline, 4f, 6f)
        plot(canvas, px, (ex - 1).toFloat(), ey.toFloat(), pal.outline, 6f, 4f)
        plot(canvas, px, ex.toFloat(), ey.toFloat(), pal.eyeOuter, 4f, 4f)
        plot(canvas, px, ex.toFloat(), (ey + 1).toFloat(), pal.eyeColor, 4f, 3f)
        plot(canvas, px, (ex + 1).toFloat(), (ey + 3).toFloat(), pal.eyeHighlight, 2f, 1f)

        plot(canvas, px, (ex + 1).toFloat(), (ey + 1).toFloat(), pal.outline, 2f, 2f)
        plot(canvas, px, ex.toFloat(), ey.toFloat(), Color.WHITE, 2f, 2f)
        if (!isDilated) {
            plot(canvas, px, (ex + 3).toFloat(), (ey + 3).toFloat(), Color.WHITE, 1f, 1f)
        } else {
            plot(canvas, px, (ex + 3).toFloat(), (ey + 3).toFloat(), Color.WHITE, 1f, 1f)
        }
    }

    private fun drawCollar(canvas: Canvas, px: Float, profile: CatProfile, tAnim: Float, nx: Float, ny: Float) {
        if (profile.collar == "none") return
        val band = when (profile.collar) {
            "bell_red" -> Color.rgb(239, 68, 68)
            "star_teal" -> Color.rgb(20, 184, 166)
            else -> Color.rgb(217, 119, 6)
        }
        val hi = when (profile.collar) {
            "bell_red" -> Color.rgb(252, 165, 165)
            "star_teal" -> Color.rgb(94, 234, 212)
            else -> Color.rgb(253, 224, 71)
        }
        val swing = if (sin(tAnim * 7.5f) > 0.65f) 1f else 0f
        val dark = Color.rgb(13, 21, 44)
        plot(canvas, px, nx - 6f, ny, dark, 12f, 3f)
        plot(canvas, px, nx - 5f, ny + 1f, band, 10f, 1f)
        plot(canvas, px, nx - 3f, ny + 1f, hi, 4f, 1f)
        if (profile.collar == "bell_red" || profile.collar == "gold_royal") {
            plot(canvas, px, nx - 1f + swing, ny + 2f, dark, 4f, 4f)
            plot(canvas, px, nx + swing, ny + 3f, Color.rgb(251, 191, 36), 2f, 2f)
            plot(canvas, px, nx + swing, ny + 3f, Color.rgb(254, 249, 195), 1f, 1f)
        } else if (profile.collar == "star_teal") {
            plot(canvas, px, nx - 1f + swing, ny + 2f, dark, 4f, 4f)
            plot(canvas, px, nx + swing, ny + 2f, Color.rgb(253, 224, 71), 2f, 3f)
            plot(canvas, px, nx - 1f + swing, ny + 3f, Color.rgb(253, 224, 71), 4f, 1f)
        }
    }

    private fun drawHeadwear(canvas: Canvas, px: Float, profile: CatProfile, tAnim: Float, cx: Float, topY: Float) {
        if (profile.headwear == "none") return
        val hx = cx.roundToInt().toFloat()
        val hy = topY.roundToInt().toFloat()
        when (profile.headwear) {
            "ribbon" -> {
                plot(canvas, px, hx - 6f, hy - 3f, Color.rgb(26, 17, 32), 8f, 5f)
                plot(canvas, px, hx - 5f, hy - 2f, Color.rgb(244, 114, 182), 3f, 3f)
                plot(canvas, px, hx - 1f, hy - 2f, Color.rgb(244, 114, 182), 3f, 3f)
                plot(canvas, px, hx - 2f, hy - 2f, Color.rgb(251, 207, 232), 1f, 3f)
                plot(canvas, px, hx - 5f, hy - 2f, Color.WHITE, 1f, 1f)
            }
            "crown" -> {
                plot(canvas, px, hx - 5f, hy - 5f, Color.rgb(30, 22, 13), 10f, 6f)
                plot(canvas, px, hx - 4f, hy - 4f, Color.rgb(251, 191, 36), 8f, 4f)
                plot(canvas, px, hx - 4f, hy - 6f, Color.rgb(253, 224, 71), 2f, 3f)
                plot(canvas, px, hx - 1f, hy - 7f, Color.rgb(253, 224, 71), 2f, 4f)
                plot(canvas, px, hx + 2f, hy - 6f, Color.rgb(253, 224, 71), 2f, 3f)
                plot(canvas, px, hx - 3f, hy - 2f, Color.rgb(239, 68, 68), 1f, 1f)
                plot(canvas, px, hx, hy - 2f, Color.rgb(56, 189, 248), 1f, 1f)
                plot(canvas, px, hx + 3f, hy - 2f, Color.rgb(239, 68, 68), 1f, 1f)
            }
            "beanie" -> {
                plot(canvas, px, hx - 6f, hy - 6f, Color.rgb(26, 18, 24), 12f, 6f)
                plot(canvas, px, hx - 5f, hy - 5f, Color.rgb(225, 29, 72), 10f, 4f)
                plot(canvas, px, hx - 4f, hy - 5f, Color.rgb(251, 113, 133), 2f, 3f)
                plot(canvas, px, hx + 2f, hy - 5f, Color.rgb(251, 113, 133), 2f, 3f)
                plot(canvas, px, hx - 6f, hy - 1f, Color.rgb(255, 241, 242), 12f, 2f)
                val bob = if (sin(tAnim * 6f) > 0.5f) 1f else 0f
                plot(canvas, px, hx - 2f, hy - 9f - bob, Color.rgb(26, 18, 24), 4f, 4f)
                plot(canvas, px, hx - 1f, hy - 8f - bob, Color.WHITE, 2f, 2f)
            }
            "wizard" -> {
                val d = Color.rgb(13, 16, 36)
                plot(canvas, px, hx - 8f, hy - 1f, d, 16f, 3f)
                plot(canvas, px, hx - 5f, hy - 4f, d, 10f, 3f)
                plot(canvas, px, hx - 3f, hy - 8f, d, 6f, 4f)
                plot(canvas, px, hx - 1f, hy - 11f, d, 4f, 3f)
                plot(canvas, px, hx - 7f, hy, Color.rgb(49, 46, 129), 14f, 1f)
                plot(canvas, px, hx - 4f, hy - 3f, Color.rgb(67, 56, 202), 8f, 3f)
                plot(canvas, px, hx - 2f, hy - 7f, Color.rgb(79, 70, 229), 4f, 4f)
                plot(canvas, px, hx, hy - 10f, Color.rgb(99, 102, 241), 2f, 3f)
                plot(canvas, px, hx - 4f, hy - 1f, Color.rgb(251, 191, 36), 8f, 1f)
                plot(canvas, px, hx - 1f, hy - 5f, Color.rgb(254, 240, 138), 2f, 2f)
            }
        }
    }

    private fun drawEyewear(canvas: Canvas, px: Float, profile: CatProfile, lx: Int, rx: Int, ey: Int) {
        if (profile.eyewear == "none") return
        when (profile.eyewear) {
            "shades" -> {
                val d = Color.rgb(11, 14, 24)
                plot(canvas, px, (lx - 1).toFloat(), ey.toFloat(), d, 6f, 4f)
                plot(canvas, px, (rx - 1).toFloat(), ey.toFloat(), d, 6f, 4f)
                plot(canvas, px, (lx + 5).toFloat(), (ey + 1).toFloat(), d, max(1, rx - lx - 6).toFloat(), 1f)
                plot(canvas, px, lx.toFloat(), (ey + 1).toFloat(), Color.rgb(30, 41, 59), 4f, 2f)
                plot(canvas, px, rx.toFloat(), (ey + 1).toFloat(), Color.rgb(30, 41, 59), 4f, 2f)
                plot(canvas, px, lx.toFloat(), ey.toFloat(), Color.WHITE, 2f, 1f)
                plot(canvas, px, rx.toFloat(), ey.toFloat(), Color.WHITE, 2f, 1f)
            }
            "round" -> {
                val gold = Color.rgb(251, 191, 36)
                for (ex in intArrayOf(lx, rx)) {
                    plot(canvas, px, (ex - 1).toFloat(), (ey - 1).toFloat(), gold, 6f, 1f)
                    plot(canvas, px, (ex - 1).toFloat(), (ey + 4).toFloat(), gold, 6f, 1f)
                    plot(canvas, px, (ex - 1).toFloat(), (ey - 1).toFloat(), gold, 1f, 6f)
                    plot(canvas, px, (ex + 4).toFloat(), (ey - 1).toFloat(), gold, 1f, 6f)
                }
                plot(canvas, px, (lx + 5).toFloat(), (ey + 1).toFloat(), gold, max(1, rx - lx - 6).toFloat(), 1f)
            }
            "heart" -> {
                val p1 = Color.rgb(236, 72, 153)
                val p2 = Color.rgb(244, 114, 182)
                for (ex in intArrayOf(lx, rx)) {
                    plot(canvas, px, (ex - 1).toFloat(), ey.toFloat(), p1, 2f, 2f)
                    plot(canvas, px, (ex + 2).toFloat(), ey.toFloat(), p1, 2f, 2f)
                    plot(canvas, px, (ex - 1).toFloat(), (ey + 1).toFloat(), p2, 5f, 2f)
                    plot(canvas, px, ex.toFloat(), (ey + 3).toFloat(), p1, 3f, 1f)
                    plot(canvas, px, (ex + 1).toFloat(), (ey + 4).toFloat(), p1, 1f, 1f)
                }
            }
        }
    }

    private fun drawChibiHead(
        canvas: Canvas,
        px: Float,
        pal: CoatPalette,
        profile: CatProfile,
        tAnim: Float,
        blinkActive: Boolean,
        cx: Float,
        cy: Float,
        forceClosed: Boolean,
        forceHappy: Boolean,
        surprised: Boolean,
        earFlat: Float
    ) {
        drawEar(canvas, px, pal, cx - 5.8f, cy - 11.5f + earFlat, -1.5f, false)
        drawEar(canvas, px, pal, cx + 5.6f, cy - 11.2f + earFlat, 1.8f, pal.pattern == "calico")

        plotShadedBlob(canvas, px, cx, cy, 8.6f, 7.1f, pal, "right", false)

        when (pal.pattern) {
            "tabby", "silver_tabby" -> {
                plot(canvas, px, cx - 2f, cy - 6f, pal.secondary, 1f, 2f)
                plot(canvas, px, cx, cy - 6f, pal.secondary, 1f, 3f)
                plot(canvas, px, cx + 2f, cy - 6f, pal.secondary, 1f, 2f)
                plot(canvas, px, cx - 7f, cy + 1f, pal.secondary, 2f, 1f)
                plot(canvas, px, cx + 6f, cy + 1f, pal.secondary, 2f, 1f)
            }
            "calico" -> {
                plotPixelOval(canvas, px, cx + 4.5f, cy - 3.5f, 3.6f, 2.8f, pal.secondary)
                plotPixelOval(canvas, px, cx - 5f, cy - 3f, 2.8f, 2.4f, pal.belly)
            }
            "siamese" -> {
                plotPixelOval(canvas, px, cx + 0.5f, cy + 1.2f, 5.4f, 4.2f, pal.secondary)
            }
            "tuxedo" -> {
                plotPixelOval(canvas, px, cx + 0.6f, cy + 3.2f, 4.6f, 3.0f, pal.belly)
            }
        }

        // 3-strand delicate whiskers
        val wFlick = if (sin(tAnim * 4.5f) > 0.88f) 1f else 0f
        plot(canvas, px, cx - 10f, cy + 0.5f + wFlick, pal.outline, 2f, 1f)
        plot(canvas, px, cx - 10.5f, cy + 2.3f, pal.outline, 2.5f, 1f)
        plot(canvas, px, cx - 9.5f, cy + 4.0f, pal.outline, 2f, 1f)
        plot(canvas, px, cx + 8.5f, cy + 0.5f + wFlick, pal.outline, 2f, 1f)
        plot(canvas, px, cx + 8.5f, cy + 2.3f, pal.outline, 2.5f, 1f)
        plot(canvas, px, cx + 8.0f, cy + 4.0f, pal.outline, 2f, 1f)

        val lx = (cx - 4.4f).roundToInt()
        val rxEye = (cx + 2.2f).roundToInt()
        val ey = (cy - 1.8f).roundToInt()
        val mood = profile.mood

        when {
            forceHappy || mood == "AFFECTIONATE" -> {
                plot(canvas, px, (lx - 1).toFloat(), (ey + 2).toFloat(), pal.outline, 2f, 1f)
                plot(canvas, px, (lx + 1).toFloat(), (ey + 1).toFloat(), pal.outline, 2f, 1f)
                plot(canvas, px, (lx + 3).toFloat(), (ey + 2).toFloat(), pal.outline, 1f, 1f)
                plot(canvas, px, rxEye.toFloat(), (ey + 2).toFloat(), pal.outline, 1f, 1f)
                plot(canvas, px, (rxEye + 1).toFloat(), (ey + 1).toFloat(), pal.outline, 2f, 1f)
                plot(canvas, px, (rxEye + 3).toFloat(), (ey + 2).toFloat(), pal.outline, 2f, 1f)
                val blush = Color.rgb(255, 155, 184)
                plot(canvas, px, (lx - 1).toFloat(), (ey + 4).toFloat(), blush, 2f, 1f)
                plot(canvas, px, (rxEye + 3).toFloat(), (ey + 4).toFloat(), blush, 2f, 1f)
            }
            forceClosed || blinkActive || mood == "SLEEPY" -> {
                plot(canvas, px, (lx - 1).toFloat(), (ey + 2).toFloat(), pal.outline, 1f, 1f)
                plot(canvas, px, lx.toFloat(), (ey + 3).toFloat(), pal.outline, 3f, 1f)
                plot(canvas, px, (lx + 3).toFloat(), (ey + 2).toFloat(), pal.outline, 1f, 1f)
                plot(canvas, px, rxEye.toFloat(), (ey + 2).toFloat(), pal.outline, 1f, 1f)
                plot(canvas, px, (rxEye + 1).toFloat(), (ey + 3).toFloat(), pal.outline, 3f, 1f)
                plot(canvas, px, (rxEye + 4).toFloat(), (ey + 2).toFloat(), pal.outline, 1f, 1f)
            }
            surprised -> {
                plot(canvas, px, lx.toFloat(), ey.toFloat(), Color.WHITE, 4f, 4f)
                plot(canvas, px, rxEye.toFloat(), ey.toFloat(), Color.WHITE, 4f, 4f)
                plot(canvas, px, (lx + 1).toFloat(), (ey + 1).toFloat(), pal.outline, 2f, 2f)
                plot(canvas, px, (rxEye + 1).toFloat(), (ey + 1).toFloat(), pal.outline, 2f, 2f)
            }
            mood == "CHILL" -> {
                drawGlossyEye(canvas, px, pal, lx, ey, false)
                drawGlossyEye(canvas, px, pal, rxEye, ey, false)
                plot(canvas, px, (lx - 1).toFloat(), (ey - 1).toFloat(), pal.primary, 6f, 2f)
                plot(canvas, px, (lx - 1).toFloat(), (ey + 1).toFloat(), pal.outline, 6f, 1f)
                plot(canvas, px, (rxEye - 1).toFloat(), (ey - 1).toFloat(), pal.primary, 6f, 2f)
                plot(canvas, px, (rxEye - 1).toFloat(), (ey + 1).toFloat(), pal.outline, 6f, 1f)
            }
            else -> {
                drawGlossyEye(canvas, px, pal, lx, ey, mood == "HUNTER")
                drawGlossyEye(canvas, px, pal, rxEye, ey, mood == "HUNTER")
                if (mood == "CHAOTIC") {
                    plot(canvas, px, (lx + 2).toFloat(), (ey - 1).toFloat(), pal.primary, 2f, 2f)
                    plot(canvas, px, rxEye.toFloat(), (ey - 1).toFloat(), pal.primary, 2f, 2f)
                }
            }
        }

        // Cute pink nose & :3 smile
        val nx = (cx + 0.2f).roundToInt()
        val ny = (cy + 2.2f).roundToInt()
        plot(canvas, px, (nx - 1).toFloat(), ny.toFloat(), pal.nose, 3f, 1f)
        plot(canvas, px, nx.toFloat(), (ny + 1).toFloat(), pal.nose, 1f, 1f)
        plot(canvas, px, (nx - 1).toFloat(), ny.toFloat(), Color.rgb(255, 158, 192), 1f, 1f)

        plot(canvas, px, (nx - 2).toFloat(), (ny + 2).toFloat(), pal.outline, 1f, 1f)
        plot(canvas, px, (nx - 1).toFloat(), (ny + 3).toFloat(), pal.outline, 1f, 1f)
        plot(canvas, px, nx.toFloat(), (ny + 2).toFloat(), pal.outline, 1f, 1f)
        plot(canvas, px, (nx + 1).toFloat(), (ny + 3).toFloat(), pal.outline, 1f, 1f)
        plot(canvas, px, (nx + 2).toFloat(), (ny + 2).toFloat(), pal.outline, 1f, 1f)

        drawEyewear(canvas, px, profile, lx, rxEye, ey)
        drawHeadwear(canvas, px, profile, tAnim, cx, cy - 7.5f)
    }

    fun drawCat(
        canvas: Canvas,
        centerX: Float,
        footY: Float,
        dpScale: Float,
        profile: CatProfile,
        action: String,
        facing: Int,
        animTime: Float,
        blinkActive: Boolean,
        climbSide: Int // -1 left wall, 1 right wall, 0 normal
    ) {
        val pal = CatPalettes.byId(profile.coat)
        val px = (1.50f * dpScale * profile.scale).coerceAtLeast(2.2f)

        canvas.save()
        canvas.translate(centerX.roundToInt().toFloat(), footY.roundToInt().toFloat())

        // Ground shadow only when actually resting/moving on a horizontal surface
        if (action != "DANGLE" && action != "JUMP" && action != "FALL" && action != "CLIMB_WALL") {
            val sw = when (action) {
                "BIG_STRETCH" -> 16f
                "SLEEP_LOAF", "RUN" -> 14.5f
                "WALK" -> 13.5f
                else -> 11.5f
            }
            plotPixelOval(canvas, px, 0f, -0.5f, sw, 2.2f, Color.argb(72, 12, 18, 38))
        }

        // For CLIMB_WALL: dedicated vertical PC climb pose!
        // Paws reach +X in local space -> scaleX = -1 on LEFT wall (-1) grips LEFT wall, scaleX = +1 on RIGHT wall (+1) grips RIGHT wall!
        // Head ALWAYS points UP (-Y) and tail ALWAYS hangs DOWN (+Y)!
        val drawFacing = if (action == "CLIMB_WALL" && climbSide != 0) climbSide else facing
        canvas.scale(drawFacing.toFloat(), 1f)

        when (action) {
            "SLEEP_LOAF" -> {
                val br = sin(animTime * 2.0f) * 0.65f
                plotShadedBlob(canvas, px, -3.2f, -7.2f - br * 0.5f, 10.8f, 7.2f + br * 0.4f, pal, "right", false)
                plotPixelOval(canvas, px, -4.2f, -5.8f - br * 0.3f, 4.4f, 3.6f, pal.midShadow)
                plotPixelOval(canvas, px, -4.6f, -6.4f - br * 0.3f, 3.6f, 3.0f, pal.primary)
                if (pal.pattern == "tabby" || pal.pattern == "silver_tabby") {
                    plot(canvas, px, -6f, -12f, pal.secondary, 2f, 4f)
                    plot(canvas, px, -2f, -12f, pal.secondary, 2f, 4f)
                } else if (pal.pattern == "calico") {
                    plotPixelOval(canvas, px, -5f, -9.2f, 4.2f, 3.2f, pal.secondary)
                }
                plotShadedBlob(canvas, px, 7.6f, -2.1f, 3.3f, 2.0f, pal, "right", true)
                plot(canvas, px, 8f, -1f, pal.outline, 1f, 1f)
                plotShadedBlob(canvas, px, -1.2f, -2.2f, 10.6f, 2.5f, pal, "bottom", false)
                plotPixelOval(canvas, px, -3.5f, -2.6f, 6.2f, 1.3f, pal.primary)
                drawCollar(canvas, px, profile, animTime, 2.0f, -4.0f - br * 0.25f)
                drawChibiHead(canvas, px, pal, profile, animTime, blinkActive, 5.0f, -7.2f - br * 0.25f, true, false, false, 1.5f)

                // Crisp pixel-art bright blue floating "z z Z"
                val bob1 = sin(animTime * 2.4f) * 1.1f
                val bob2 = sin(animTime * 2.4f - 0.8f) * 1.1f
                val bob3 = sin(animTime * 2.4f - 1.6f) * 1.1f
                val zDark = Color.rgb(29, 78, 216)
                val zBright = Color.rgb(56, 189, 248)
                val z1y = (-17.5f + bob1).roundToInt().toFloat()
                plot(canvas, px, -1f, z1y, zDark, 3f, 1f)
                plot(canvas, px, 0f, z1y + 1f, zBright, 1f, 1f)
                plot(canvas, px, -1f, z1y + 2f, zDark, 3f, 1f)
                val z2y = (-21.5f + bob2).roundToInt().toFloat()
                plot(canvas, px, -5f, z2y, zDark, 4f, 1f)
                plot(canvas, px, -3f, z2y + 1f, zBright, 1f, 1f)
                plot(canvas, px, -4f, z2y + 2f, zBright, 1f, 1f)
                plot(canvas, px, -5f, z2y + 3f, zDark, 4f, 1f)
                val z3y = (-26.8f + bob3).roundToInt().toFloat()
                plot(canvas, px, -9f, z3y, zDark, 5f, 1f)
                plot(canvas, px, -9f, z3y + 1f, zBright, 5f, 1f)
                plot(canvas, px, -6f, z3y + 2f, zDark, 1f, 1f)
                plot(canvas, px, -7f, z3y + 3f, zBright, 1f, 1f)
                plot(canvas, px, -8f, z3y + 4f, zDark, 1f, 1f)
                plot(canvas, px, -9f, z3y + 5f, zDark, 5f, 1f)
            }
            "BIG_STRETCH", "HAPPY_PURR" -> {
                val bounce = if (action == "HAPPY_PURR") sin(animTime * 6.5f) * 0.9f else 0f
                drawSmoothSTail(canvas, px, pal, profile.mood, action, animTime, -8.5f, -7.5f)
                plotShadedBlob(canvas, px, -5.5f, -6.5f, 6.2f, 5.6f, pal, "right", false)
                plotShadedBlob(canvas, px, -5.8f, -1.9f, 3.4f, 2.0f, pal, "right", true)
                plotShadedBlob(canvas, px, 1.0f, -6.2f + bounce * 0.4f, 6.4f, 4.8f, pal, "bottom", false)
                plotPixelOval(canvas, px, 2.5f, -5.5f, 3.4f, 3.0f, pal.belly)
                plotShadedBlob(canvas, px, 6.8f, -3.2f, 4.4f, 2.0f, pal, "right", true)
                plotShadedBlob(canvas, px, 9.4f, -1.9f, 3.8f, 1.9f, pal, "right", true)
                plot(canvas, px, 11f, -1f, pal.outline, 1f, 1f)
                drawCollar(canvas, px, profile, animTime, 2.7f, -8.2f + bounce)
                drawChibiHead(canvas, px, pal, profile, animTime, blinkActive, 3.2f, -14.2f + bounce, false, true, false, 0f)
            }
            "DANGLE" -> {
                val kick = sin(animTime * 16.0f) * 1.4f
                drawSmoothSTail(canvas, px, pal, profile.mood, action, animTime, -5f, -6f)
                plotShadedBlob(canvas, px, 0f, -12.5f, 6.2f, 8.8f, pal, "right", false)
                plotPixelOval(canvas, px, 0.5f, -11.5f, 3.8f, 6.0f, pal.belly)
                plotShadedBlob(canvas, px, -5.2f, -14f + kick, 2.4f, 2.4f, pal, "right", true)
                plotShadedBlob(canvas, px, 5.2f, -14f - kick, 2.4f, 2.4f, pal, "right", true)
                plotShadedBlob(canvas, px, -3.2f, -3.5f + kick, 2.6f, 2.4f, pal, "right", true)
                plotShadedBlob(canvas, px, 3.2f, -3.5f - kick, 2.6f, 2.4f, pal, "right", true)
                drawCollar(canvas, px, profile, animTime, 0f, -20.5f)
                drawChibiHead(canvas, px, pal, profile, animTime, blinkActive, 0f, -26.5f, false, false, true, 0f)
            }
            "CLIMB_WALL" -> {
                val step = sin(animTime * 13.0f) * 2.2f
                // Tail hanging cleanly straight DOWN while cat climbs UP
                for (i in 0 until 7) {
                    val sw = sin(animTime * 8.0f - i * 0.6f) * 1.6f
                    plotPixelOval(canvas, px, -2f + sw, -1f + i * 2.0f, 2.6f, 2.3f, pal.outline)
                    plotPixelOval(canvas, px, -2f + sw, -1f + i * 2.0f, 1.7f, 1.5f, if (i % 2 == 0) pal.primary else pal.midShadow)
                }
                plotShadedBlob(canvas, px, -1.5f, -12.5f, 5.8f, 8.2f, pal, "left", false)
                plotShadedBlob(canvas, px, 4.8f, -17.5f + step, 3.0f, 2.2f, pal, "right", true)
                plotShadedBlob(canvas, px, 4.8f, -11.5f - step, 3.0f, 2.2f, pal, "right", true)
                plotShadedBlob(canvas, px, 4.2f, -5.5f + step, 3.0f, 2.2f, pal, "right", true)
                drawCollar(canvas, px, profile, animTime, -1.0f, -19.5f)
                drawChibiHead(canvas, px, pal, profile, animTime, blinkActive, -1.0f, -25.2f, false, false, false, 0f)
            }
            "RUN" -> {
                val cycle = animTime * 3.05f
                val w = cycle * PI.toFloat() * 2f
                val flex = sin(w)
                val loft = sin(w - 0.55f)
                val gather = max(0f, -flex)
                val stretch = max(0f, flex)

                val baseY = -10.3f - loft * 1.55f
                val pelvisX = -4.9f - flex * 2.05f
                val pelvisY = baseY + 0.45f - flex * 0.65f - gather * 0.7f
                val midX = -0.2f
                val midY = baseY - 0.25f + stretch * 0.95f - gather * 2.35f
                val chestX = 4.8f + flex * 1.95f
                val chestY = baseY - 0.55f + flex * 0.55f - gather * 0.25f

                val spineHalfLen = 4.6f + flex * 1.45f
                val waistRy = 3.55f + gather * 0.65f - stretch * 0.35f
                val earSweep = 1.05f + gather * 0.95f + stretch * 0.45f

                drawSmoothSTail(canvas, px, pal, profile.mood, action, animTime, pelvisX - 2.2f, pelvisY - 0.9f)

                val farBack = sampleAgileRunLeg(pelvisX - 0.3f, pelvisY + 1.15f, cycle, 0.20f, true, 5.1f, loft)
                val nearBack = sampleAgileRunLeg(pelvisX + 0.4f, pelvisY + 1.35f, cycle, 0.33f, true, 5.4f, loft)
                val farFront = sampleAgileRunLeg(chestX + 0.3f, chestY + 1.05f, cycle, 0.70f, false, 5.3f, loft)
                val nearFront = sampleAgileRunLeg(chestX - 0.3f, chestY + 1.25f, cycle, 0.83f, false, 5.6f, loft)

                plotArticulatedLegOutline(canvas, px, farBack, pal.outline)
                plotArticulatedLegOutline(canvas, px, farFront, pal.outline)
                plotPixelOval(canvas, px, pelvisX, pelvisY, 4.65f, 4.35f, pal.outline)
                plotPixelOval(canvas, px, (pelvisX + midX) * 0.5f, (pelvisY + midY) * 0.5f, 4.2f, waistRy + 0.95f, pal.outline)
                plotPixelOval(canvas, px, midX, midY, spineHalfLen, waistRy + 1.0f, pal.outline)
                plotPixelOval(canvas, px, (midX + chestX) * 0.5f, (midY + chestY) * 0.5f, 4.2f, waistRy + 1.0f, pal.outline)
                plotPixelOval(canvas, px, chestX, chestY, 4.75f, 4.45f, pal.outline)
                plotArticulatedLegOutline(canvas, px, nearBack, pal.outline)
                plotArticulatedLegOutline(canvas, px, nearFront, pal.outline)

                plotArticulatedLegFill(canvas, px, farBack, pal, true)
                plotArticulatedLegFill(canvas, px, farFront, pal, true)

                plotPixelOval(canvas, px, pelvisX, pelvisY, 3.85f, 3.55f, pal.midShadow)
                plotPixelOval(canvas, px, (pelvisX + midX) * 0.5f, (pelvisY + midY) * 0.5f, 3.5f, waistRy + 0.1f, pal.midShadow)
                plotPixelOval(canvas, px, midX, midY, spineHalfLen - 0.7f, waistRy + 0.15f, pal.midShadow)
                plotPixelOval(canvas, px, (midX + chestX) * 0.5f, (midY + chestY) * 0.5f, 3.5f, waistRy + 0.15f, pal.midShadow)
                plotPixelOval(canvas, px, chestX, chestY, 3.95f, 3.65f, pal.midShadow)

                plotPixelOval(canvas, px, pelvisX - 0.2f, pelvisY - 0.45f, 3.35f, 3.0f, pal.primary)
                plotPixelOval(canvas, px, (pelvisX + midX) * 0.5f, (pelvisY + midY) * 0.5f - 0.45f, 3.1f, waistRy - 0.45f, pal.primary)
                plotPixelOval(canvas, px, midX, midY - 0.55f, spineHalfLen - 1.15f, waistRy - 0.45f, pal.primary)
                plotPixelOval(canvas, px, (midX + chestX) * 0.5f, (midY + chestY) * 0.5f - 0.45f, 3.1f, waistRy - 0.4f, pal.primary)
                plotPixelOval(canvas, px, chestX - 0.15f, chestY - 0.5f, 3.45f, 3.1f, pal.primary)

                plotPixelOval(canvas, px, (pelvisX + midX) * 0.5f, (pelvisY + midY) * 0.5f - 1.75f, 2.7f, 1.1f, pal.highlight)
                plotPixelOval(canvas, px, midX, midY - 1.95f, spineHalfLen * 0.62f, 1.15f, pal.highlight)
                plotPixelOval(canvas, px, (midX + chestX) * 0.5f, (midY + chestY) * 0.5f - 1.75f, 2.7f, 1.1f, pal.highlight)

                if (pal.pattern == "tabby" || pal.pattern == "silver_tabby") {
                    plot(canvas, px, pelvisX + 1.2f, pelvisY - 2.6f, pal.secondary, 2f, 2.5f)
                    plot(canvas, px, midX - 0.5f, midY - 2.8f, pal.secondary, 2f, 3f)
                    plot(canvas, px, chestX - 2.2f, chestY - 2.6f, pal.secondary, 2f, 2.5f)
                } else if (pal.pattern == "calico") {
                    plotPixelOval(canvas, px, (pelvisX + midX) * 0.5f, (pelvisY + midY) * 0.5f - 0.8f, 3.2f, 2.1f, pal.secondary)
                }
                plotPixelOval(canvas, px, chestX + 0.55f, chestY + 0.45f, 2.6f, 2.35f, pal.belly)

                plotArticulatedLegFill(canvas, px, nearBack, pal, false)
                plotArticulatedLegFill(canvas, px, nearFront, pal, false)

                val headX = chestX + 2.35f - flex * 0.35f
                val headY = -15.6f - loft * 0.75f + stretch * 0.35f - gather * 0.25f
                drawCollar(canvas, px, profile, animTime, chestX + 0.7f, chestY - 0.9f)
                drawChibiHead(canvas, px, pal, profile, animTime, blinkActive, headX, headY, false, false, false, earSweep)
            }
            "WALK" -> {
                val cycleU = animTime * 1.85f
                val phaseRad = cycleU * PI.toFloat() * 2f
                val bodyBob = sin(phaseRad * 2f) * 0.55f
                val chestPitch = sin(phaseRad) * 0.38f
                val hipPitch = -chestPitch
                val bodyY = -9.6f + bodyBob

                drawSmoothSTail(canvas, px, pal, profile.mood, action, animTime, -6.2f, -9.8f + bodyBob)

                val farBack = sampleCatWalkLeg(-4.4f, bodyY + 1.5f + hipPitch, cycleU, 3.4f, true)
                val farFront = sampleCatWalkLeg(4.3f, bodyY + 1.2f + chestPitch, cycleU + 0.25f, 3.6f, false)
                val nearBack = sampleCatWalkLeg(-4.7f, bodyY + 1.6f + hipPitch, cycleU + 0.50f, 3.6f, true)
                val nearFront = sampleCatWalkLeg(4.9f, bodyY + 1.4f + chestPitch, cycleU + 0.75f, 3.9f, false)

                plotArticulatedLegOutline(canvas, px, farBack, pal.outline)
                plotArticulatedLegOutline(canvas, px, farFront, pal.outline)
                plotPixelOval(canvas, px, -0.2f, bodyY, 9.4f, 5.4f, pal.outline)
                plotPixelOval(canvas, px, -4.5f, bodyY + 0.3f + hipPitch, 4.8f, 4.6f, pal.outline)
                plotPixelOval(canvas, px, 4.5f, bodyY - 0.3f + chestPitch, 4.8f, 4.8f, pal.outline)
                plotArticulatedLegOutline(canvas, px, nearBack, pal.outline)
                plotArticulatedLegOutline(canvas, px, nearFront, pal.outline)

                plotArticulatedLegFill(canvas, px, farBack, pal, true)
                plotArticulatedLegFill(canvas, px, farFront, pal, true)

                plotPixelOval(canvas, px, -0.2f, bodyY, 8.3f, 4.4f, pal.midShadow)
                plotPixelOval(canvas, px, -4.5f, bodyY + 0.3f + hipPitch, 3.8f, 3.6f, pal.midShadow)
                plotPixelOval(canvas, px, 4.5f, bodyY - 0.3f + chestPitch, 3.8f, 3.8f, pal.midShadow)

                plotPixelOval(canvas, px, -0.8f, bodyY - 0.7f, 7.6f, 3.6f, pal.primary)
                plotPixelOval(canvas, px, -4.7f, bodyY - 0.2f + hipPitch, 3.2f, 2.9f, pal.primary)
                plotPixelOval(canvas, px, 4.3f, bodyY - 0.8f + chestPitch, 3.3f, 3.2f, pal.primary)
                plotPixelOval(canvas, px, -1.2f, bodyY - 2.2f, 5.1f, 1.4f, pal.highlight)

                if (pal.pattern == "tabby" || pal.pattern == "silver_tabby") {
                    plot(canvas, px, -5f, bodyY - 3.5f, pal.secondary, 2f, 3f)
                    plot(canvas, px, -1f, bodyY - 3.5f, pal.secondary, 2f, 3f)
                    plot(canvas, px, 3f, bodyY - 3.5f, pal.secondary, 2f, 2f)
                } else if (pal.pattern == "calico") {
                    plotPixelOval(canvas, px, -4f, bodyY - 1.2f, 3.5f, 2.3f, pal.secondary)
                }
                plotPixelOval(canvas, px, 5.0f, bodyY + 0.3f + chestPitch, 2.9f, 2.7f, pal.belly)

                plotArticulatedLegFill(canvas, px, nearBack, pal, false)
                plotArticulatedLegFill(canvas, px, nearFront, pal, false)

                val headX = 6.2f
                val headY = bodyY - 6.3f - bodyBob * 0.4f + chestPitch * 0.55f
                drawCollar(canvas, px, profile, animTime, headX - 1.2f, bodyY - 1.4f)
                drawChibiHead(canvas, px, pal, profile, animTime, blinkActive, headX, headY, false, false, false, if (profile.mood == "HUNTER") 1.5f else 0f)
            }
            "JUMP", "FALL" -> {
                val actBodyY = -11.4f
                drawSmoothSTail(canvas, px, pal, profile.mood, action, animTime, -7.2f, actBodyY + 0.5f)
                val hL = ArticulatedCatLeg(-5.2f, actBodyY + 1.4f, -8.6f, actBodyY + 3.2f, -11.4f, actBodyY + 5.6f, true, 0f)
                val fL = ArticulatedCatLeg(4.8f, actBodyY + 1.0f, 7.4f, actBodyY + 2.4f, 10.6f, actBodyY + 3.2f, false, 0f)
                plotArticulatedLegOutline(canvas, px, hL, pal.outline)
                plotArticulatedLegOutline(canvas, px, fL, pal.outline)
                plotShadedBlob(canvas, px, -0.5f, actBodyY, 8.6f, 4.9f, pal, "bottom", false)
                plotPixelOval(canvas, px, 4.8f, actBodyY + 0.8f, 3.1f, 2.8f, pal.belly)
                if (pal.pattern == "tabby" || pal.pattern == "silver_tabby") {
                    plot(canvas, px, -5f, actBodyY - 3.6f, pal.secondary, 2f, 3f)
                    plot(canvas, px, -1f, actBodyY - 3.6f, pal.secondary, 2f, 3f)
                    plot(canvas, px, 3f, actBodyY - 3.6f, pal.secondary, 2f, 2f)
                } else if (pal.pattern == "calico") {
                    plotPixelOval(canvas, px, -4f, actBodyY - 1.2f, 3.5f, 2.5f, pal.secondary)
                }
                plotArticulatedLegFill(canvas, px, hL, pal, false)
                plotArticulatedLegFill(canvas, px, fL, pal, false)
                val actHeadX = 5.6f
                val actHeadY = actBodyY - 6.3f
                drawCollar(canvas, px, profile, animTime, actHeadX - 1.2f, actBodyY - 1.4f)
                drawChibiHead(canvas, px, pal, profile, animTime, blinkActive, actHeadX, actHeadY, false, false, false, 0.8f)
            }
            else -> {
                // Cute seated IDLE_SIT pose (exact 1:1 PC sitting cat!)
                val breathe = sin(animTime * 2.8f) * 0.45f
                drawSmoothSTail(canvas, px, pal, profile.mood, action, animTime, -7.5f, -6.2f)
                plotShadedBlob(canvas, px, -3.5f, -6.5f, 6.2f, 6.2f, pal, "right", false)
                plotShadedBlob(canvas, px, 2.2f, -7.8f, 5.2f, 6.4f, pal, "right", false)
                plotPixelOval(canvas, px, 3.0f, -7.5f, 3.2f, 4.2f, pal.belly)
                plotShadedBlob(canvas, px, -5.2f, -2.1f, 3.3f, 2.1f, pal, "right", true)
                plotShadedBlob(canvas, px, 1.2f, -4.8f, 2.3f, 4.4f, pal, "right", false)
                plotShadedBlob(canvas, px, 4.6f, -4.8f, 2.3f, 4.4f, pal, "right", false)
                plotShadedBlob(canvas, px, 1.6f, -1.8f, 2.6f, 1.9f, pal, "right", true)
                plotShadedBlob(canvas, px, 5.0f, -1.8f, 2.6f, 1.9f, pal, "right", true)
                plot(canvas, px, 2f, -1f, pal.outline, 1f, 1f)
                plot(canvas, px, 5f, -1f, pal.outline, 1f, 1f)
                if (pal.pattern == "tabby" || pal.pattern == "silver_tabby") {
                    plot(canvas, px, -7f, -9f, pal.secondary, 3f, 1f)
                    plot(canvas, px, -7f, -6f, pal.secondary, 3f, 1f)
                } else if (pal.pattern == "calico") {
                    plotPixelOval(canvas, px, -4.5f, -7.5f, 3.4f, 2.6f, pal.secondary)
                }
                val headX = 1.6f
                val headY = -17.5f + breathe
                drawCollar(canvas, px, profile, animTime, headX, headY + 6.4f)
                drawChibiHead(canvas, px, pal, profile, animTime, blinkActive, headX, headY, false, false, false, if (profile.mood == "HUNTER") 1.5f else 0f)
            }
        }

        canvas.restore()
    }

    // 1:1 PC High-Detail Plush Quilted Pixel Sleep Pillow (with tassels, seams & squish!)
    fun drawPillow(canvas: Canvas, x: Float, y: Float, dpScale: Float, isCatSleepingOnIt: Boolean, animTime: Float) {
        val squish = if (isCatSleepingOnIt) (2f + sin(animTime * 2.0f) * 0.5f) else 0f
        val px = (1.65f * dpScale).coerceAtLeast(2.2f)
        val rx = 21f
        val ry = max(5f, 7.5f - squish * 0.5f)
        val cyG = -ry

        canvas.save()
        canvas.translate(x.roundToInt().toFloat(), y.roundToInt().toFloat())

        plotPixelOval(canvas, px, 0f, 0.5f, rx + 1.5f, 2.4f, Color.argb(88, 10, 14, 30))

        val cOut = Color.rgb(19, 22, 43)
        val cGold = Color.rgb(251, 191, 36)
        val cCream = Color.rgb(254, 243, 199)

        // Corner plush golden tassels
        plotPixelOval(canvas, px, -rx - 1.4f, cyG + 1.4f, 2.8f, 2.2f, cOut)
        plotPixelOval(canvas, px, rx + 1.4f, cyG + 1.4f, 2.8f, 2.2f, cOut)
        plotPixelOval(canvas, px, -rx - 1.4f, cyG + 1.3f, 1.8f, 1.3f, cGold)
        plotPixelOval(canvas, px, rx + 1.4f, cyG + 1.3f, 1.8f, 1.3f, cGold)
        plot(canvas, px, -rx - 2f, cyG + 1f, cCream, 1f, 1f)
        plot(canvas, px, rx + 1f, cyG + 1f, cCream, 1f, 1f)

        // Bolster cushion layered cel shading
        plotPixelOval(canvas, px, 0f, cyG, rx + 1.2f, ry + 1.2f, cOut)
        plotPixelOval(canvas, px, 0f, cyG + 0.3f, rx, ry, Color.rgb(55, 48, 163))
        plotPixelOval(canvas, px, 0f, cyG - 0.5f, rx - 0.6f, ry - 0.9f, Color.rgb(99, 102, 241))
        plotPixelOval(canvas, px, 0f, cyG - 1.2f, rx - 1.8f, ry - 1.7f, Color.rgb(129, 140, 248))
        plotPixelOval(canvas, px, -2f, cyG - ry * 0.52f, rx * 0.62f, max(1.4f, ry * 0.34f), Color.rgb(199, 210, 254))
        plotPixelOval(canvas, px, -4f, cyG - ry * 0.64f, rx * 0.36f, 1.0f, Color.rgb(238, 242, 255))

        // Golden piped stitched seam
        val cSeam = Color.rgb(253, 230, 138)
        var gx = (-rx + 3f).toInt()
        while (gx <= (rx - 3f).toInt()) {
            val seamY = cyG + (ry * 0.25f + cos((gx / rx) * 1.4f) * 0.7f).roundToInt().toFloat()
            plot(canvas, px, gx.toFloat(), seamY, cSeam, 1f, 1f)
            gx += 2
        }

        // Tufted dimples & woven star accents
        val cDimple = if (isCatSleepingOnIt) Color.rgb(49, 46, 129) else Color.rgb(67, 56, 202)
        plot(canvas, px, -8f, cyG - 1f, cDimple, 2f, 1f)
        plot(canvas, px, 6f, cyG - 1f, cDimple, 2f, 1f)
        plot(canvas, px, -1f, cyG, cDimple, 2f, 1f)
        plot(canvas, px, 0f, cyG + 2f, Color.rgb(254, 240, 138), 1f, 1f)
        plot(canvas, px, -12f, cyG + 1f, Color.rgb(224, 231, 255), 1f, 1f)
        plot(canvas, px, 11f, cyG + 1f, Color.rgb(224, 231, 255), 1f, 1f)

        canvas.restore()
    }

    // 1:1 PC High-Detail Pixel Fish Treat (dorsal/tail fins, shading, eye glint!)
    fun drawFish(canvas: Canvas, x: Float, y: Float, dpScale: Float) {
        val px = (1.15f * dpScale).coerceAtLeast(1.8f)
        val dark = Color.rgb(13, 21, 44)
        canvas.save()
        canvas.translate(x.roundToInt().toFloat(), y.roundToInt().toFloat())

        plot(canvas, px, -11f, -7f, dark, 22f, 14f)
        plot(canvas, px, -17f, -9f, dark, 8f, 18f)
        plot(canvas, px, -3f, -10f, dark, 8f, 4f)
        plot(canvas, px, -9f, -5f, Color.rgb(2, 132, 199), 18f, 10f)
        plot(canvas, px, -8f, -4f, Color.rgb(56, 189, 248), 16f, 6f)
        plot(canvas, px, -6f, -3f, Color.rgb(224, 242, 254), 12f, 2f)
        plot(canvas, px, -1f, -8f, Color.rgb(125, 211, 252), 5f, 3f)
        plot(canvas, px, -15f, -7f, Color.rgb(3, 105, 161), 6f, 14f)
        plot(canvas, px, -14f, -5f, Color.rgb(56, 189, 248), 4f, 10f)
        plot(canvas, px, -4f, 0f, Color.rgb(186, 230, 253), 2f, 2f)
        plot(canvas, px, 0f, -1f, Color.rgb(186, 230, 253), 2f, 2f)
        plot(canvas, px, 4f, -3f, dark, 3f, 3f)
        plot(canvas, px, 4f, -3f, Color.WHITE, 1f, 1f)

        canvas.restore()
    }

    // 1:1 PC High-Detail Pixel Yarn Ball + Trailing Physics Wool String
    fun drawYarn(canvas: Canvas, yarn: YarnItem, dpScale: Float) {
        val cOut = Color.rgb(13, 21, 44)
        val cDeep = Color.rgb(159, 18, 57)
        val cShad = Color.rgb(225, 29, 72)
        val cMain = Color.rgb(244, 63, 94)
        val cHi = Color.rgb(253, 164, 175)

        // Trailing physics yarn string
        if (yarn.trailX.size > 1) {
            fun drawRopePass(col: Int, width: Float) {
                ropePaint.color = col
                ropePaint.strokeWidth = width
                for (i in 0 until yarn.trailX.size - 1) {
                    canvas.drawLine(yarn.trailX[i], yarn.trailY[i], yarn.trailX[i + 1], yarn.trailY[i + 1], ropePaint)
                }
            }
            drawRopePass(cOut, 4.6f * dpScale)
            drawRopePass(cShad, 3.0f * dpScale)
            drawRopePass(cMain, 1.5f * dpScale)
        }

        val px = (1.65f * dpScale).coerceAtLeast(2.2f)
        val gr = 7.5f
        canvas.save()
        canvas.translate(yarn.x.roundToInt().toFloat(), yarn.y.roundToInt().toFloat())

        plotPixelOval(canvas, px, 0f, gr, gr - 1f, 1.8f, Color.argb(82, 12, 18, 38))
        plotPixelOval(canvas, px, 0f, 0f, gr + 1.1f, gr + 1.1f, cOut)
        plotPixelOval(canvas, px, 0f, 0f, gr, gr, cDeep)
        plotPixelOval(canvas, px, -0.8f, -0.8f, gr - 0.9f, gr - 0.9f, cShad)
        plotPixelOval(canvas, px, -1.4f, -1.4f, gr - 1.8f, gr - 1.8f, cMain)

        for (i in -2..2) {
            val off = (sin(yarn.rotation + i * 0.9f) * (gr - 2f)).roundToInt().toFloat()
            plot(canvas, px, -gr + 2f, off, cDeep, gr * 2f - 4f, 1f)
            plot(canvas, px, off, -gr + 2f, cShad, 1f, gr * 2f - 4f)
        }
        plotPixelOval(canvas, px, -gr * 0.38f, -gr * 0.38f, 2.6f, 1.8f, cHi)
        plot(canvas, px, -gr * 0.48f, -gr * 0.45f, Color.WHITE, 1f, 1f)

        canvas.restore()
    }

    // Crisp Pixel-Art Particles (Hearts, Sparkles, Dust, Blue Zzz)
    fun drawParticle(canvas: Canvas, p: OverlayParticle, dpScale: Float) {
        val alpha = ((p.life / p.maxLife).coerceIn(0f, 1f) * 255f).roundToInt()
        if (alpha <= 8) return
        val col = Color.argb(alpha, Color.red(p.color), Color.green(p.color), Color.blue(p.color))
        val px = (1.25f * dpScale * p.scale).coerceAtLeast(1.8f)

        canvas.save()
        canvas.translate(p.x.roundToInt().toFloat(), p.y.roundToInt().toFloat())
        when (p.kind) {
            "heart" -> {
                plot(canvas, px, -4f, -3f, col, 3f, 3f)
                plot(canvas, px, 1f, -3f, col, 3f, 3f)
                plot(canvas, px, -4f, 0f, col, 8f, 3f)
                plot(canvas, px, -2f, 3f, col, 4f, 2f)
            }
            "sparkle" -> {
                plot(canvas, px, -1f, -4f, col, 2f, 8f)
                plot(canvas, px, -4f, -1f, col, 8f, 2f)
                plot(canvas, px, -1f, -1f, Color.argb(alpha, 255, 255, 255), 2f, 2f)
            }
            "zzz" -> {
                plot(canvas, px, -2f, -3f, col, 5f, 1.5f)
                plot(canvas, px, 1f, -1.5f, col, 1.5f, 1.5f)
                plot(canvas, px, -0.5f, 0f, col, 1.5f, 1.5f)
                plot(canvas, px, -2f, 1.5f, col, 5f, 1.5f)
            }
            else -> {
                plot(canvas, px, -2f, -2f, col, 4f, 4f)
            }
        }
        canvas.restore()
    }
}
