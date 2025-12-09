package com.example.healthmonitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MenuItem
import android.view.TextureView
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sqrt

@Suppress("DEPRECATION")
class PulseActivity : AppCompatActivity(), TextureView.SurfaceTextureListener {

    private lateinit var textureView: TextureView
    private lateinit var startButton: Button
    private lateinit var btnHistory: Button // ДОДАНО
    private lateinit var pulseText: TextView
    private lateinit var statusText: TextView
    private lateinit var instructionText: TextView

    private var camera: Camera? = null
    private var isRecording = false
    private val redValues = ArrayList<Double>()
    private val timeStamps = ArrayList<Long>()
    private val handler = Handler(Looper.getMainLooper())
    private var startTime = 0L
    private var measurementRunnable: Runnable? = null

    // База даних
    private lateinit var database: HealthDatabase

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 100
        private const val RECORDING_DURATION = 15000L
        private const val SAMPLE_RATE = 30.0
        private const val TAG = "PulseMeter"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pulse)

        // Налаштовуємо ActionBar для повернення назад
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Вимірювання пульсу"

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // Ініціалізуємо базу даних
        database = Room.databaseBuilder(
            applicationContext,
            HealthDatabase::class.java,
            "health_database"
        ).build()

        initViews()
        setupListeners()

        if (checkCameraPermission()) {
            initCamera()
        } else {
            requestCameraPermission()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun initViews() {
        textureView = findViewById(R.id.textureView)
        startButton = findViewById(R.id.startButton)
        btnHistory = findViewById(R.id.btnHistory) // ДОДАНО
        pulseText = findViewById(R.id.pulseText)
        statusText = findViewById(R.id.statusText)
        instructionText = findViewById(R.id.instructionText)

        textureView.surfaceTextureListener = this
    }

    private fun setupListeners() {
        startButton.setOnClickListener {
            if (!isRecording) {
                startMeasurement()
            } else {
                stopMeasurement()
            }
        }

        // ДОДАНО: Обробник кнопки історії
        btnHistory.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initCamera()
            } else {
                Toast.makeText(this, "Потрібен дозвіл на камеру", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun initCamera() {
        try {
            camera = Camera.open()
            camera?.let { cam ->
                val params = cam.parameters
                if (params.supportedFlashModes?.contains(Camera.Parameters.FLASH_MODE_TORCH) == true) {
                    params.flashMode = Camera.Parameters.FLASH_MODE_TORCH
                }
                cam.parameters = params
            }
        } catch (e: Exception) {
            Log.e(TAG, "Помилка ініціалізації камери: ${e.message}")
            Toast.makeText(this, "Помилка доступу до камери", Toast.LENGTH_LONG).show()
        }
    }

    private fun startMeasurement() {
        redValues.clear()
        timeStamps.clear()
        isRecording = true
        startTime = System.currentTimeMillis()

        startButton.text = "Зупинити"
        statusText.text = "Підготовка до вимірювання..."
        pulseText.text = "---"
        instructionText.text = "Прикладіть палець до камери і тримайте нерухомо"

        startDataCollection()

        measurementRunnable = Runnable {
            if (isRecording) {
                stopMeasurement()
            }
        }
        handler.postDelayed(measurementRunnable!!, RECORDING_DURATION)
    }

    private fun startDataCollection() {
        val dataCollectionRunnable = object : Runnable {
            override fun run() {
                if (isRecording) {
                    captureFrame()
                    handler.postDelayed(this, (1000 / SAMPLE_RATE).toLong())
                }
            }
        }
        handler.post(dataCollectionRunnable)
    }

    private fun stopMeasurement() {
        isRecording = false
        measurementRunnable?.let { handler.removeCallbacks(it) }

        startButton.text = "Почати вимірювання"
        statusText.text = "Обчислення результату..."
        instructionText.text = "Обробка даних..."

        handler.postDelayed({
            calculatePulse()
        }, 500)
    }

    private fun captureFrame() {
        val bitmap = textureView.getBitmap()
        bitmap?.let {
            val redValue = calculateAverageRed(it)
            redValues.add(redValue)
            timeStamps.add(System.currentTimeMillis() - startTime)

            val progress = ((System.currentTimeMillis() - startTime) * 100 / RECORDING_DURATION).toInt()
            statusText.text = "Вимірювання... $progress%"

            if (redValues.size > 10) {
                val recentAvg = redValues.takeLast(10).average()
                val signalStrength = when {
                    redValue > recentAvg * 1.02 -> "📈"
                    redValue < recentAvg * 0.98 -> "📉"
                    else -> "📊"
                }
                instructionText.text = "Тримайте палець нерухомо $signalStrength"
            }
        }
    }

    private fun calculateAverageRed(bitmap: Bitmap): Double {
        val width = bitmap.width
        val height = bitmap.height
        var totalRed = 0.0
        var pixelCount = 0

        val centerX = width / 2
        val centerY = height / 2
        val radius = minOf(width, height) / 6

        for (x in (centerX - radius)..(centerX + radius) step 5) {
            for (y in (centerY - radius)..(centerY + radius) step 5) {
                if (x in 0 until width && y in 0 until height) {
                    val pixel = bitmap.getPixel(x, y)
                    val red = (pixel shr 16) and 0xFF
                    totalRed += red.toDouble()
                    pixelCount++
                }
            }
        }

        return if (pixelCount > 0) totalRed / pixelCount else 0.0
    }

    private fun calculatePulse() {
        Log.d(TAG, "Початок обчислення пульсу. Кількість значень: ${redValues.size}")

        if (redValues.size < 100) {
            showResult("Недостатньо даних", "Спробуйте ще раз, тримаючи палець на камері довше")
            return
        }

        val rawMean = redValues.average()
        val rawMin = redValues.minOrNull() ?: 0.0
        val rawMax = redValues.maxOrNull() ?: 0.0
        val rawRange = rawMax - rawMin

        Log.d(TAG, "Сирі дані - середнє: $rawMean, мін: $rawMin, макс: $rawMax, діапазон: $rawRange")

        try {
            if (rawRange < 0.5) {
                showResult("Слабкий сигнал", "Діапазон сигналу занадто малий - притисніть палець щільніше")
                return
            }

            val detrendedData = removeLinearTrend(redValues)
            val smoothedData = simpleMovingAverage(detrendedData, windowSize = 3)
            val normalizedData = normalizeSignal(smoothedData)

            val procMin = normalizedData.minOrNull() ?: 0.0
            val procMax = normalizedData.maxOrNull() ?: 0.0
            val procMean = normalizedData.average()
            val procStdDev = sqrt(normalizedData.map { (it - procMean) * (it - procMean) }.average())

            Log.d(TAG, "Оброблений сигнал - мін: $procMin, макс: $procMax, середнє: $procMean, stdDev: $procStdDev")
            Log.d(TAG, "Перші 30 значень: ${normalizedData.take(30).joinToString { "%.2f".format(it) }}")

            val hasOscillations = checkForOscillations(normalizedData)
            Log.d(TAG, "Сигнал має коливання: $hasOscillations")

            var peaks = findPeaksAdaptive(normalizedData, sensitivity = 1.0)
            Log.d(TAG, "Знайдено піків (висока чутливість): ${peaks.size}")

            if (peaks.size < 5) {
                peaks = findPeaksAdaptive(normalizedData, sensitivity = 1.5)
                Log.d(TAG, "Знайдено піків (дуже висока чутливість): ${peaks.size}")
            }

            if (peaks.size >= 3) {
                val intervals = mutableListOf<Long>()
                for (i in 1 until peaks.size) {
                    val interval = timeStamps[peaks[i]] - timeStamps[peaks[i - 1]]
                    intervals.add(interval)
                    Log.d(TAG, "Інтервал $i: $interval мс (${60000.0 / interval} BPM)")
                }

                val bpm = SignalProcessor.calculatePulseFromPeaks(peaks, timeStamps)
                Log.d(TAG, "Розрахований BPM: $bpm")

                when {
                    bpm == 0 -> {
                        showResult("Помилка", "Не вдалося обчислити пульс")
                    }
                    bpm in 40..200 -> {
                        val confidence = calculateConfidence(peaks, timeStamps, bpm)
                        val variability = calculateVariability(intervals)

                        Log.d(TAG, "Впевненість: $confidence, Варіабельність: $variability")

                        savePulseResult(bpm)

                        val status = when {
                            bpm in 60..100 -> "Нормальний пульс"
                            bpm in 100..120 -> "Підвищений пульс"
                            bpm in 40..60 -> "Знижений пульс"
                            bpm > 120 -> "Високий пульс"
                            else -> "Результат"
                        }

                        val qualityInfo = when {
                            confidence > 0.8 -> "відмінно"
                            confidence > 0.6 -> "добре"
                            confidence > 0.4 -> "задовільно"
                            else -> "низька точність"
                        }

                        showResult("$bpm BPM", "$status\nЯкість: $qualityInfo (${peaks.size} ударів)")
                    }
                    else -> {
                        showResult("$bpm BPM", "Перевірте вимірювання - результат поза нормою")
                    }
                }
            } else {
                showResult("Недостатньо даних", "Знайдено ${peaks.size} ударів. Потрібно мінімум 3")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Помилка обчислення: ${e.message}", e)
            showResult("Помилка", "Спробуйте ще раз")
        }
    }

    private fun findPeaksAdaptive(data: List<Double>, sensitivity: Double = 1.0): List<Int> {
        val peaks = ArrayList<Int>()

        if (data.size < 10) {
            Log.d(TAG, "Замало даних для пошуку піків")
            return peaks
        }

        val minPeakDistanceHigh = (SAMPLE_RATE * 0.25 / sensitivity).toInt()
        val localMaxima = mutableListOf<Pair<Int, Double>>()

        for (i in 2 until data.size - 2) {
            val current = data[i]

            if (current > data[i-1] && current > data[i-2] &&
                current > data[i+1] && current > data[i+2]) {
                localMaxima.add(Pair(i, current))
            }
        }

        Log.d(TAG, "Знайдено локальних максимумів: ${localMaxima.size}")

        if (localMaxima.isEmpty()) return peaks

        val sortedMaxima = localMaxima.sortedByDescending { it.second }
        val topCount = maxOf(3, localMaxima.size / 2)
        val dynamicThreshold = sortedMaxima[minOf(topCount - 1, sortedMaxima.lastIndex)].second * (0.5 / sensitivity)

        Log.d(TAG, "Динамічний поріг: $dynamicThreshold (на основі топ-$topCount максимумів)")
        Log.d(TAG, "Топ-5 максимумів: ${sortedMaxima.take(5).map { "idx=${it.first}, val=%.2f".format(it.second) }}")

        for ((index, value) in sortedMaxima) {
            if (value < dynamicThreshold) {
                Log.d(TAG, "Максимум на індексі $index відхилено: значення $value < поріг $dynamicThreshold")
                continue
            }

            var tooClose = false
            for (existingPeak in peaks) {
                if (abs(index - existingPeak) < minPeakDistanceHigh) {
                    tooClose = true
                    Log.d(TAG, "Максимум на індексі $index занадто близько до піку на $existingPeak")
                    break
                }
            }

            if (!tooClose) {
                peaks.add(index)
                Log.d(TAG, "✓ Пік прийнято: індекс=$index, значення=$value, час=${timeStamps.getOrNull(index)}мс")
            }
        }

        peaks.sort()
        return peaks
    }

    private fun removeLinearTrend(data: List<Double>): List<Double> {
        if (data.size < 3) return data

        val n = data.size
        val xMean = (n - 1) / 2.0
        val yMean = data.average()

        var numerator = 0.0
        var denominator = 0.0

        for (i in data.indices) {
            val xDiff = i - xMean
            numerator += xDiff * (data[i] - yMean)
            denominator += xDiff * xDiff
        }

        val slope = if (denominator != 0.0) numerator / denominator else 0.0
        val intercept = yMean - slope * xMean

        return data.mapIndexed { i, value ->
            value - (slope * i + intercept)
        }
    }

    private fun simpleMovingAverage(data: List<Double>, windowSize: Int): List<Double> {
        if (data.size < windowSize) return data

        val result = mutableListOf<Double>()

        for (i in data.indices) {
            val start = maxOf(0, i - windowSize / 2)
            val end = minOf(data.size, i + windowSize / 2 + 1)
            val avg = data.subList(start, end).average()
            result.add(avg)
        }

        return result
    }

    private fun checkForOscillations(data: List<Double>): Boolean {
        if (data.size < 10) return false

        var signChanges = 0
        for (i in 1 until data.size) {
            if ((data[i] > 0 && data[i-1] < 0) || (data[i] < 0 && data[i-1] > 0)) {
                signChanges++
            }
        }

        return signChanges >= 5
    }

    private fun normalizeSignal(data: List<Double>): List<Double> {
        if (data.size < 10) return data

        val mean = data.average()
        val stdDev = sqrt(data.map { (it - mean) * (it - mean) }.average())

        return if (stdDev > 0.001) {
            data.map { (it - mean) / stdDev }
        } else {
            data.map { it - mean }
        }
    }

    private fun calculateVariability(intervals: List<Long>): Double {
        if (intervals.size < 2) return 0.0

        val mean = intervals.average()
        val variance = intervals.map { (it - mean) * (it - mean) }.average()

        return if (mean > 0) sqrt(variance) / mean else 0.0
    }

    private fun calculateConfidence(peaks: List<Int>, timeStamps: List<Long>, bpm: Int): Double {
        if (peaks.size < 3) return 0.0

        val intervals = mutableListOf<Double>()
        for (i in 1 until peaks.size) {
            intervals.add((timeStamps[peaks[i]] - timeStamps[peaks[i - 1]]).toDouble())
        }

        if (intervals.isEmpty()) return 0.0

        val sortedIntervals = intervals.sorted()
        val medianInterval = sortedIntervals[sortedIntervals.size / 2]
        val expectedInterval = 60000.0 / bpm

        val tolerance = 0.25
        val consistentIntervals = intervals.count {
            abs(it - medianInterval) / medianInterval < tolerance
        }

        val consistencyScore = consistentIntervals.toDouble() / intervals.size
        val accuracyScore = 1.0 - abs(medianInterval - expectedInterval) / expectedInterval

        return (consistencyScore * 0.7 + accuracyScore.coerceIn(0.0, 1.0) * 0.3)
    }

    private fun showResult(pulse: String, status: String) {
        pulseText.text = pulse
        statusText.text = status
        instructionText.text = "Натисніть кнопку для нового вимірювання"
    }

    private fun savePulseResult(bpm: Int) {
        lifecycleScope.launch {
            try {
                val pulseRecord = PulseRecord(
                    bpm = bpm,
                    timestamp = System.currentTimeMillis()
                )

                withContext(Dispatchers.IO) {
                    database.pulseDao().insert(pulseRecord)
                }

                Log.d(TAG, "Результат збережено: $bpm BPM")

            } catch (e: Exception) {
                Log.e(TAG, "Помилка збереження результату: ${e.message}")
            }
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        startCamera()
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        stopCamera()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    private fun startCamera() {
        camera?.let { cam ->
            try {
                cam.setPreviewTexture(textureView.surfaceTexture)
                cam.startPreview()
            } catch (e: Exception) {
                Log.e(TAG, "Помилка запуску камери: ${e.message}")
                Toast.makeText(this, "Помилка запуску камери", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopCamera() {
        camera?.let { cam ->
            try {
                cam.stopPreview()
                val params = cam.parameters
                if (params.flashMode == Camera.Parameters.FLASH_MODE_TORCH) {
                    params.flashMode = Camera.Parameters.FLASH_MODE_OFF
                    cam.parameters = params
                }
                cam.release()
            } catch (e: Exception) {
                Log.e(TAG, "Помилка зупинки камери: ${e.message}")
            }
        }
        camera = null
    }

    override fun onPause() {
        super.onPause()
        if (isRecording) {
            stopMeasurement()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCamera()
    }
}