package com.example.healthmonitor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

class ActivityChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.FILL
    }

    private val axisLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BDBDBD")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#757575")
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }

    private val gridLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E0E0")
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    private var hourlyData = IntArray(24) { 0 }
    private val paddingLeft = 100f
    private val paddingRight = 40f
    private val paddingTop = 40f
    private val paddingBottom = 80f
    private val barSpacing = 4f

    fun setData(data: List<Pair<Int, Int>>) {
        // Очищаємо попередні дані
        hourlyData = IntArray(24) { 0 }

        android.util.Log.d("ActivityChartView", "Отримано ${data.size} записів для відображення")

        // Заповнюємо новими даними (час -> кроки)
        data.forEach { (hour, steps) ->
            if (hour in 0..23) {
                hourlyData[hour] = steps
                android.util.Log.d("ActivityChartView", "Година $hour: $steps кроків")
            }
        }

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width == 0 || height == 0) return

        val chartWidth = width - paddingLeft - paddingRight
        val chartHeight = height - paddingTop - paddingBottom

        // ВИПРАВЛЕНО: Розумне масштабування
        val maxStepsInData = hourlyData.maxOrNull() ?: 0
        val minStepsInData = hourlyData.filter { it > 0 }.minOrNull() ?: 0

        // Мінімальний масштаб 200 кроків для красивого відображення
        val baseScale = 200

        // Якщо максимум більший за мінімальний масштаб, додаємо 30% зверху
        val maxSteps = if (maxStepsInData > baseScale) {
            (maxStepsInData * 1.3).toInt()
        } else {
            baseScale
        }

        android.util.Log.d("ActivityChartView", "Масштаб: min=$minStepsInData, max=$maxStepsInData, display=$maxSteps")

        // Малюємо горизонтальні лінії сітки та підписи кроків
        drawGridAndLabels(canvas, chartWidth, chartHeight, maxSteps)

        // Малюємо вертикальні осі
        canvas.drawLine(
            paddingLeft,
            paddingTop,
            paddingLeft,
            height - paddingBottom,
            axisLinePaint
        )
        canvas.drawLine(
            paddingLeft,
            height - paddingBottom,
            width - paddingRight,
            height - paddingBottom,
            axisLinePaint
        )

        // Малюємо стовпчики та підписи годин
        drawBars(canvas, chartWidth, chartHeight, maxSteps)

        // Підписи осей
        drawAxisLabels(canvas)
    }

    private fun drawGridAndLabels(
        canvas: Canvas,
        chartWidth: Float,
        chartHeight: Float,
        maxSteps: Int
    ) {
        val stepLevels = calculateStepLevels(maxSteps)

        stepLevels.forEach { steps ->
            val y = paddingTop + chartHeight - (steps.toFloat() / maxSteps * chartHeight)

            // Горизонтальна лінія
            canvas.drawLine(paddingLeft, y, width - paddingRight, y, gridLinePaint)

            // Підпис кроків зліва
            val labelText = formatStepLabel(steps)
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.textSize = 22f
            canvas.drawText(
                labelText,
                paddingLeft - 10,
                y + 7,
                textPaint
            )
        }
    }

    private fun calculateStepLevels(maxSteps: Int): List<Int> {
        // ВИПРАВЛЕНО: Розумне округлення для красивих чисел
        val roundedMax = when {
            maxSteps <= 100 -> 100
            maxSteps <= 200 -> 200
            maxSteps <= 500 -> ((maxSteps + 99) / 100) * 100
            maxSteps <= 1000 -> ((maxSteps + 199) / 200) * 200
            maxSteps <= 2000 -> ((maxSteps + 249) / 250) * 250
            maxSteps <= 5000 -> ((maxSteps + 499) / 500) * 500
            else -> ((maxSteps + 999) / 1000) * 1000
        }

        return listOf(
            0,
            roundedMax / 4,
            roundedMax / 2,
            roundedMax * 3 / 4,
            roundedMax
        )
    }

    private fun formatStepLabel(steps: Int): String {
        return when {
            steps >= 10000 -> "${steps / 1000}k"
            steps >= 1000 -> String.format("%.1fk", steps / 1000f)
            else -> steps.toString()
        }
    }

    private fun drawBars(
        canvas: Canvas,
        chartWidth: Float,
        chartHeight: Float,
        maxSteps: Int
    ) {
        val barWidth = (chartWidth / 24f) - barSpacing
        val maxRight = width - paddingRight

        for (i in 0..23) {
            val steps = hourlyData[i]
            if (steps > 0) {
                // ВИПРАВЛЕНО: Масштабування відносно maxSteps
                val barHeight = (steps.toFloat() / maxSteps * chartHeight).coerceAtMost(chartHeight)

                val left = paddingLeft + i * (barWidth + barSpacing)
                val top = height - paddingBottom - barHeight
                var right = left + barWidth
                val bottom = height - paddingBottom

                // Обмежуємо стовпчик щоб не виходив за межі
                if (right > maxRight) {
                    right = maxRight
                }

                // Малюємо тільки якщо стовпчик в межах
                if (left < maxRight) {
                    // Колір залежно від кількості кроків
                    barPaint.color = getBarColor(steps, maxSteps)

                    // Малюємо стовпчик
                    val rect = RectF(left, top, right, bottom)
                    canvas.drawRoundRect(rect, 6f, 6f, barPaint)
                }
            }

            // Підписи годин (показуємо кожну 3-ю годину)
            if (i % 3 == 0) {
                val x = paddingLeft + i * (barWidth + barSpacing) + barWidth / 2
                if (x < maxRight) {
                    textPaint.textAlign = Paint.Align.CENTER
                    textPaint.textSize = 20f
                    canvas.drawText(
                        String.format("%02d", i),
                        x,
                        height - paddingBottom + 35,
                        textPaint
                    )
                }
            }
        }
    }

    private fun getBarColor(steps: Int, maxSteps: Int): Int {
        val ratio = steps.toFloat() / maxSteps
        return when {
            ratio >= 0.7 -> Color.parseColor("#4CAF50") // Зелений
            ratio >= 0.4 -> Color.parseColor("#FFC107") // Жовтий
            else -> Color.parseColor("#2196F3") // Синій
        }
    }

    private fun drawAxisLabels(canvas: Canvas) {
        textPaint.textSize = 26f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.parseColor("#616161")

        // Підпис осі X (час)
        canvas.drawText(
            "Година доби",
            paddingLeft + (width - paddingLeft - paddingRight) / 2,
            height - 15f,
            textPaint
        )

        // Підпис осі Y (кроки) - повертаємо і малюємо
        canvas.save()
        canvas.rotate(-90f, 25f, height / 2f)
        canvas.drawText(
            "Кроки",
            25f,
            height / 2f + 10,
            textPaint
        )
        canvas.restore()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = 900
        val desiredHeight = 450

        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val width = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> minOf(desiredWidth, widthSize)
            else -> desiredWidth
        }

        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> minOf(desiredHeight, heightSize)
            else -> desiredHeight
        }

        setMeasuredDimension(width, height)
    }
}