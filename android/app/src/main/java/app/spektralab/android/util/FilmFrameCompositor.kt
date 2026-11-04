package app.spektralab.android.util

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import app.spektralab.android.model.DarkroomParams
import app.spektralab.android.model.SpektraCatalog
import kotlin.math.max
import kotlin.math.min

object FilmFrameCompositor {

    fun applyOverlaysIfNeeded(source: Bitmap, params: DarkroomParams): Bitmap {
        if (!params.filmEdgeActive && !params.dateBackActive) {
            return source
        }

        val working = if (params.dateBackActive && !params.filmEdgeActive) {
            source.copy(Bitmap.Config.ARGB_8888, true).also {
                drawQuartzDateBack(Canvas(it), 0f, 0f, it.width.toFloat(), it.height.toFloat(), params.dateBackText)
            }
        } else if (params.filmEdgeActive) {
            composeWithFilmRebate(source, params)
        } else {
            source
        }

        return working
    }

    private fun composeWithFilmRebate(source: Bitmap, params: DarkroomParams): Bitmap {
        val srcW = source.width
        val srcH = source.height
        val longEdge = max(srcW, srcH).toFloat()
        val is135 = params.filmFormatMm <= 36.5

        val sideBorder = (longEdge * 0.032f).toInt().coerceAtLeast(12)
        val railBorder = if (is135) {
            (longEdge * 0.115f).toInt().coerceAtLeast(44)
        } else {
            (longEdge * 0.075f).toInt().coerceAtLeast(28)
        }

        val outW = srcW + sideBorder * 2
        val outH = srcH + railBorder * 2
        val outBitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outBitmap)

        // 1. Deep matte film rebate black base
        canvas.drawColor(Color.rgb(11, 11, 13))

        // 2. Subtle inner gate border highlight
        val gatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(1f, longEdge * 0.0015f)
            color = Color.argb(65, 255, 210, 150)
        }
        val imgLeft = sideBorder.toFloat()
        val imgTop = railBorder.toFloat()
        val imgRight = (sideBorder + srcW).toFloat()
        val imgBottom = (railBorder + srcH).toFloat()

        // 3. Draw image frame
        canvas.drawBitmap(source, imgLeft, imgTop, null)
        canvas.drawRect(imgLeft, imgTop, imgRight, imgBottom, gatePaint)

        // 4. Optional Date Back inside the image frame
        if (params.dateBackActive) {
            drawQuartzDateBack(canvas, imgLeft, imgTop, srcW.toFloat(), srcH.toFloat(), params.dateBackText)
        }

        // 5. Draw 35mm Sprocket Holes if 135 format
        if (is135) {
            val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.rgb(238, 236, 232)
            }
            val holeShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = max(1f, longEdge * 0.0018f)
                color = Color.rgb(38, 34, 30)
            }

            val holeCount = 8
            val holePitch = srcW.toFloat() / holeCount.toFloat()
            val holeW = holePitch * 0.44f
            val holeH = railBorder * 0.46f
            val cornerR = min(holeW, holeH) * 0.22f

            val topHoleY = (railBorder * 0.48f) - holeH * 0.5f
            val bottomHoleY = outH - (railBorder * 0.48f) - holeH * 0.5f

            for (i in 0 until holeCount) {
                val cx = imgLeft + (i + 0.5f) * holePitch
                val topRect = RectF(cx - holeW * 0.5f, topHoleY, cx + holeW * 0.5f, topHoleY + holeH)
                val botRect = RectF(cx - holeW * 0.5f, bottomHoleY, cx + holeW * 0.5f, bottomHoleY + holeH)

                canvas.drawRoundRect(topRect, cornerR, cornerR, holePaint)
                canvas.drawRoundRect(topRect, cornerR, cornerR, holeShadowPaint)

                canvas.drawRoundRect(botRect, cornerR, cornerR, holePaint)
                canvas.drawRoundRect(botRect, cornerR, cornerR, holeShadowPaint)
            }
        }

        // 6. Authentic Edge Print Markings (Amber/Gold for Color, Silver-Gray for B&W)
        val stock = SpektraCatalog.findFilm(params.filmStock)
        val isBw = stock.category == app.spektralab.android.model.FilmCategory.BLACK_AND_WHITE
        val edgeColor = if (isBw) Color.argb(200, 210, 210, 210) else Color.argb(215, 238, 162, 67)

        val textSize = (railBorder * if (is135) 0.19f else 0.30f).coerceAtLeast(10f)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = edgeColor
            this.textSize = textSize
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            letterSpacing = 0.12f
        }

        val frameNo = params.filmEdgeFrameNumber.coerceIn(1, 99)
        val topTextY = if (is135) railBorder * 0.20f else railBorder * 0.62f
        val bottomTextY = if (is135) outH - railBorder * 0.08f else outH - railBorder * 0.35f

        canvas.drawText(stock.edgeCode, imgLeft + srcW * 0.04f, topTextY, textPaint)
        canvas.drawText("▶ $frameNo", imgLeft + srcW * 0.54f, topTextY, textPaint)
        canvas.drawText("${frameNo}A", imgLeft + srcW * 0.86f, topTextY, textPaint)

        canvas.drawText("SPEKTRALAB 81-WL", imgLeft + srcW * 0.04f, bottomTextY, textPaint)
        canvas.drawText("◀ $frameNo", imgLeft + srcW * 0.78f, bottomTextY, textPaint)

        return outBitmap
    }

    private fun drawQuartzDateBack(
        canvas: Canvas,
        frameLeft: Float,
        frameTop: Float,
        frameW: Float,
        frameH: Float,
        dateText: String
    ) {
        val text = dateText.ifBlank { "'26 11 04" }
        val longEdge = max(frameW, frameH)
        val textSize = (longEdge * 0.028f).coerceIn(16f, 96f)

        val monoBold = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = textSize
            typeface = monoBold
            letterSpacing = 0.14f
        }
        val textWidth = measurePaint.measureText(text)
        val x = frameLeft + frameW - textWidth - frameW * 0.055f
        val y = frameTop + frameH - frameH * 0.052f

        // Outer red-orange halation glow
        val glowPaint = Paint(measurePaint).apply {
            color = Color.argb(190, 255, 62, 18)
            maskFilter = BlurMaskFilter(max(2f, textSize * 0.25f), BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawText(text, x, y, glowPaint)

        // Mid orange bloom
        val midPaint = Paint(measurePaint).apply {
            color = Color.argb(230, 255, 118, 28)
            maskFilter = BlurMaskFilter(max(1f, textSize * 0.09f), BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawText(text, x, y, midPaint)

        // Bright warm amber LED core
        val corePaint = Paint(measurePaint).apply {
            color = Color.argb(245, 255, 200, 95)
        }
        canvas.drawText(text, x, y, corePaint)
    }
}
