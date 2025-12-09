package com.example.healthmonitor

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.min

class CircularProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var progress = 0f
    private var currentSteps = 0
    private var goalSteps = 10000

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E3F2FD")
        style = Paint.Style.STROKE
        strokeWidth = 40f
        strokeCap = Paint.Cap.ROUND
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 40f
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1976D2")
        textAlign = Paint.Align.CENTER
        textSize = 64f
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#666666")
        textAlign = Paint.Align.CENTER
        textSize = 32f
    }

    private val rectF = RectF()
    private var animator: ValueAnimator? = null

    fun setSteps(steps: Int, goal: Int = 10000, animate: Boolean = true) {
        currentSteps = steps
        goalSteps = goal

        val newProgress = if (goal > 0) (steps.toFloat() / goal).coerceIn(0f, 1f) else 0f

        if (animate) {
            animateProgress(newProgress)
        } else {
            progress = newProgress
            invalidate()
        }
    }

    private fun animateProgress(targetProgress: Float) {
        animator?.cancel()

        animator = ValueAnimator.ofFloat(progress, targetProgress).apply {
            duration = 1000
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                progress = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(width, height) / 2f - backgroundPaint.strokeWidth

        rectF.set(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )

        // Малюємо фоновий круг
        canvas.drawCircle(centerX, centerY, radius, backgroundPaint)

        // Малюємо прогрес
        val sweepAngle = 360f * progress

        progressPaint.color = when {
            progress >= 1.0f -> Color.parseColor("#4CAF50") // Зелений
            progress >= 0.75f -> Color.parseColor("#8BC34A") // Світло-зелений
            progress >= 0.5f -> Color.parseColor("#FFC107") // Жовтий
            progress >= 0.25f -> Color.parseColor("#FF9800") // Помаранчевий
            else -> Color.parseColor("#2196F3") // Синій
        }

        canvas.drawArc(rectF, -90f, sweepAngle, false, progressPaint)

        // Малюємо кількість кроків (по центру)
        val stepsText = currentSteps.toString()
        canvas.drawText(
            stepsText,
            centerX,
            centerY + 20f, // Трохи нижче центру
            textPaint
        )

        // Малюємо підпис "кроків" (під числом)
        canvas.drawText(
            "кроків",
            centerX,
            centerY + 60f,
            labelPaint
        )

        // Малюємо відсоток (зверху, меншим шрифтом)
        val percentText = if (progress >= 1.0f) {
            "🎯 Ціль!"
        } else {
            "${(progress * 100).toInt()}%"
        }

        val percentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (progress >= 1.0f) Color.parseColor("#4CAF50") else Color.parseColor("#999999")
            textAlign = Paint.Align.CENTER
            textSize = 22f // Зменшено розмір відсотків
        }

        canvas.drawText(
            percentText,
            centerX,
            centerY - 40f, // Над числом
            percentPaint
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredSize = 160 // Збільшено для товщих ліній

        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val width = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> min(desiredSize, widthSize)
            else -> desiredSize
        }

        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> min(desiredSize, heightSize)
            else -> desiredSize
        }

        setMeasuredDimension(width, height)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}