package com.example.healthmonitor

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

class SignalProcessor {
    companion object {
        private const val MIN_PULSE_RATE = 40.0
        private const val MAX_PULSE_RATE = 200.0

        fun applyBandpassFilter(data: List<Double>, sampleRate: Double): List<Double> {
            if (data.size < 10) return data

            // Покращений банд-пас фільтр для пульсу (0.5 - 5 Hz)
            val nyquist = sampleRate / 2.0
            val lowCutoff = 0.5 / nyquist
            val highCutoff = 5.0 / nyquist

            // Застосовуємо високочастотний фільтр для видалення тренду
            val highPassFiltered = highPassFilter(data, lowCutoff)

            // Потім низькочастотний для видалення високочастотного шуму
            val bandPassFiltered = lowPassFilter(highPassFiltered, highCutoff)

            // М'яке згладжування з збереженням піків
            return adaptiveSmoothing(bandPassFiltered)
        }

        private fun highPassFilter(data: List<Double>, cutoff: Double): List<Double> {
            val alpha = 1.0 / (1.0 + 2.0 * PI * cutoff)
            val filtered = mutableListOf<Double>()

            if (data.isEmpty()) return filtered

            filtered.add(data[0])
            var prevFiltered = data[0]
            var prevInput = data[0]

            for (i in 1 until data.size) {
                val current = alpha * (prevFiltered + data[i] - prevInput)
                filtered.add(current)
                prevFiltered = current
                prevInput = data[i]
            }

            return filtered
        }

        private fun lowPassFilter(data: List<Double>, cutoff: Double): List<Double> {
            val alpha = 2.0 * PI * cutoff / (1.0 + 2.0 * PI * cutoff)
            val filtered = mutableListOf<Double>()

            if (data.isEmpty()) return filtered

            filtered.add(data[0])
            var prevFiltered = data[0]

            for (i in 1 until data.size) {
                val current = alpha * data[i] + (1.0 - alpha) * prevFiltered
                filtered.add(current)
                prevFiltered = current
            }

            return filtered
        }

        private fun adaptiveSmoothing(data: List<Double>): List<Double> {
            if (data.size < 5) return data

            val smoothed = mutableListOf<Double>()
            val windowSize = 3

            for (i in data.indices) {
                val start = maxOf(0, i - windowSize / 2)
                val end = minOf(data.size - 1, i + windowSize / 2)

                // Використовуємо медіанний фільтр для збереження піків
                val window = data.subList(start, end + 1).sorted()
                val median = window[window.size / 2]
                smoothed.add(median)
            }

            return smoothed
        }

        fun calculatePulseFromPeaks(peaks: List<Int>, timeStamps: List<Long>): Int {
            if (peaks.size < 3) return 0
            val intervals = mutableListOf<Double>()
            for (i in 1 until peaks.size) {
                val intervalMs = timeStamps[peaks[i]] - timeStamps[peaks[i - 1]]
                if (intervalMs > 0) {
                    intervals.add(intervalMs.toDouble())
                }
            }
            if (intervals.isEmpty()) return 0
            val cleanIntervals = removeOutliersAdaptive(intervals)
            if (cleanIntervals.isEmpty()) return 0
            val medianInterval = cleanIntervals.sorted()[cleanIntervals.size / 2]
            val bpm = (60000.0 / medianInterval).toInt()
            return if (bpm in MIN_PULSE_RATE.toInt()..MAX_PULSE_RATE.toInt()) {
                bpm
            } else {
                calculateAlternativePulse(cleanIntervals)
            }
        }

        private fun removeOutliersAdaptive(data: List<Double>): List<Double> {
            if (data.size < 3) return data

            val sorted = data.sorted()
            val median = sorted[sorted.size / 2]

            // Обчислюємо MAD (Median Absolute Deviation)
            val deviations = data.map { abs(it - median) }.sorted()
            val mad = deviations[deviations.size / 2]

            // Адаптивний поріг на основі MAD
            val threshold = 2.5 * mad

            return if (threshold > 0) {
                data.filter { abs(it - median) <= threshold }
            } else {
                // Якщо MAD дорівнює 0, використовуємо стандартне відхилення
                val mean = data.average()
                val stdDev = sqrt(data.map { (it - mean).pow(2) }.average())
                data.filter { abs(it - mean) <= 2.0 * stdDev }
            }
        }

        private fun calculateAlternativePulse(intervals: List<Double>): Int {
            if (intervals.isEmpty()) return 0

            // Спробуємо знайти домінуючий ритм
            val mean = intervals.average()
            val candidates = mutableListOf<Int>()

            // Перевіряємо різні варіанти
            for (multiplier in listOf(1.0, 0.5, 2.0)) {
                val bpm = (60000.0 / (mean * multiplier)).toInt()
                if (bpm in MIN_PULSE_RATE.toInt()..MAX_PULSE_RATE.toInt()) {
                    candidates.add(bpm)
                }
            }

            return candidates.minByOrNull {
                abs(it - 70) // Віддаємо перевагу значенням ближче до нормального пульсу
            } ?: 0
        }

        // Покращений метод для валідації якості сигналу
        fun validateSignalQuality(redValues: List<Double>): Double {
            if (redValues.size < 50) return 0.5 // Повертаємо середню якість для коротких сигналів
            val mean = redValues.average()
            val variance = redValues.map { (it - mean).pow(2) }.average()
            val stdDev = sqrt(variance)
            if (stdDev < 0.1) return 0.1 // Дуже слабкий сигнал
            val minValue = redValues.minOrNull() ?: 0.0
            val maxValue = redValues.maxOrNull() ?: 0.0
            val range = maxValue - minValue
            // Якщо діапазон занадто малий, сигнал слабкий
            if (range < 1.0) return 0.2
            // Перевіряємо наявність періодичних коливань
            val periodicityScore = analyzeSignalPeriodicity(redValues)
            // Комбінуємо різні показники якості
            val variabilityScore = minOf(1.0, stdDev / 10.0)
            val rangeScore = minOf(1.0, range / 20.0)
            return maxOf(0.1, (variabilityScore + rangeScore + periodicityScore) / 3.0)
        }

        private fun analyzeSignalPeriodicity(data: List<Double>): Double {
            if (data.size < 30) return 0.5

            // Простий аналіз автокореляції для виявлення періодичності
            val windowSize = minOf(30, data.size / 3)
            var maxCorrelation = 0.0

            for (lag in 5..windowSize) {
                var correlation = 0.0
                var count = 0

                for (i in 0 until data.size - lag) {
                    correlation += data[i] * data[i + lag]
                    count++
                }

                if (count > 0) {
                    correlation /= count
                    maxCorrelation = maxOf(maxCorrelation, abs(correlation))
                }
            }

            return minOf(1.0, maxCorrelation / 1000.0)
        }

        // Метод для адаптивного знаходження піків
        fun findAdaptivePeaks(data: List<Double>, sampleRate: Double): List<Int> {
            if (data.size < 10) return emptyList()

            // Обчислюємо адаптивний поріг на основі статистики сигналу
            val mean = data.average()
            val stdDev = sqrt(data.map { (it - mean).pow(2) }.average())
            val threshold = mean + 0.5 * stdDev

            // Мінімальна відстань між піками (відповідає максимальному пульсу)
            val minPeakDistance = (sampleRate * 60.0 / MAX_PULSE_RATE).toInt()

            val peaks = mutableListOf<Int>()
            var lastPeakIndex = -minPeakDistance

            for (i in 2 until data.size - 2) {
                val current = data[i]

                // Перевіряємо, чи є поточна точка піком
                if (current > threshold &&
                    current > data[i - 1] && current > data[i - 2] &&
                    current > data[i + 1] && current > data[i + 2] &&
                    i - lastPeakIndex >= minPeakDistance) {

                    peaks.add(i)
                    lastPeakIndex = i
                }
            }

            return peaks
        }
    }
}