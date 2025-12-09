package com.example.healthmonitor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class SimpleHealthAnalyzer(private val database: HealthDatabase) {

    data class HealthScore(
        val totalScore: Int,
        val stepsScore: Int,
        val sleepScore: Int,
        val pulseScore: Int,
        val moodScore: Int
    )

    data class RiskAssessment(
        val burnoutRisk: Int,
        val burnoutText: String,
        val sleepDisorderRisk: Int,
        val sleepText: String,
        val stressRisk: Int,
        val stressText: String,
        val depressionRisk: Int,
        val depressionText: String
    )

    data class Pattern(
        val emoji: String,
        val title: String,
        val description: String,
        val frequency: String,
        val advice: String,
        val isNegative: Boolean
    )

    data class WeeklySummary(
        val bestDay: String,
        val worstDay: String,
        val moodTrend: String,
        val sleepTrend: String,
        val activityTrend: String
    )

    data class HealthInsight(
        val emoji: String,
        val title: String,
        val message: String,
        val type: InsightType,
        val category: InsightCategory
    )

    enum class InsightType {
        GOOD, WARNING, INFO
    }

    enum class InsightCategory {
        CRITICAL, ATTENTION, POSITIVE, PERSONALIZED
    }

    // ========== ГОЛОВНІ МЕТОДИ ==========

    suspend fun calculateHealthScore(days: Int = 7): HealthScore = withContext(Dispatchers.IO) {
        val startDate = getDateBefore(days)

        val avgSteps = database.dailyStepsDao().getAverageStepsSince(startDate) ?: 0f
        val avgSleep = database.sleepDao().getAverageSleepDuration(startDate) ?: 0f
        val recentPulse = database.pulseDao().getLatestPulse()
        val avgMood = database.moodDao().getAverageMood(startDate) ?: 0f

        val stepsScore = calculateStepsScore(avgSteps)
        val sleepScore = calculateSleepScore(avgSleep)
        val pulseScore = calculatePulseScore(recentPulse?.bpm ?: 0)
        val moodScore = calculateMoodScore(avgMood)

        val totalScore = (stepsScore * 0.3 + sleepScore * 0.3 +
                pulseScore * 0.2 + moodScore * 0.2).roundToInt()

        HealthScore(totalScore, stepsScore, sleepScore, pulseScore, moodScore)
    }

    suspend fun assessRisks(days: Int = 7): RiskAssessment = withContext(Dispatchers.IO) {
        val startDate = getDateBefore(days)
        val moods = database.moodDao().getMoodsSince(startDate)
        val sleeps = database.sleepDao().getSleepRecordsSince(startDate)

        // РИЗИК ЕМОЦІЙНОГО ВИГОРАННЯ
        var burnoutRisk = 0
        val burnoutParts = mutableListOf<String>()

        val lowMoodDays = moods.count { it.moodLevel < 3 }
        if (lowMoodDays >= 5) {
            burnoutRisk += 70
            burnoutParts.add("$lowMoodDays днів настрій нижче 3.0")
        }

        val stressTags = moods.flatMap { it.getTagsList() }
            .count { it in listOf("stress", "anxiety") }
        if (stressTags > 10) {
            burnoutRisk += 20
            burnoutParts.add("часто відчували стрес ($stressTags разів)")
        }

        val avgSleep = if (sleeps.isNotEmpty()) sleeps.map { it.durationMinutes }.average() else 420.0
        if (avgSleep < 360 && sleeps.isNotEmpty()) {
            burnoutRisk += 10
            burnoutParts.add("недостатньо сну")
        }

        val burnoutText = if (burnoutParts.isNotEmpty()) {
            "У вас ${burnoutParts.joinToString(" та ")}. Спробуйте зробити перерву та відпочити."
        } else ""

        // РИЗИК ПОРУШЕННЯ СНУ
        var sleepRisk = 0
        val sleepParts = mutableListOf<String>()

        if (sleeps.isNotEmpty()) {
            if (avgSleep < 360) {
                sleepRisk += 60
                sleepParts.add("середній сон ${(avgSleep / 60).roundToInt()} годин (норма 7-9)")
            }

            if (sleeps.size >= 3) {
                val durations = sleeps.map { it.durationMinutes }
                val maxDiff = (durations.maxOrNull() ?: 0) - (durations.minOrNull() ?: 0)
                if (maxDiff > 120) {
                    sleepRisk += 30
                    sleepParts.add("нерегулярний режим сну")
                }
            }
        }

        val sleepText = if (sleepParts.isNotEmpty()) {
            "У вас ${sleepParts.joinToString(", ")}. Намагайтесь лягати та прокидатись в один час."
        } else ""

        // РИЗИК ХРОНІЧНОГО СТРЕСУ
        var stressRisk = 0
        val stressParts = mutableListOf<String>()

        val recentPulse = database.pulseDao().getLatestPulse()
        if (recentPulse != null && recentPulse.bpm > 90) {
            stressRisk += 50
            stressParts.add("підвищений пульс ${recentPulse.bpm} BPM")
        }

        val stressCount = moods.flatMap { it.getTagsList() }.count { it == "stress" }
        if (stressCount > 5) {
            stressRisk += 30
            stressParts.add("часто відчуваєте стрес ($stressCount разів)")
        }

        val avgMood = if (moods.isNotEmpty()) moods.map { it.moodLevel }.average() else 5.0
        if (avgMood < 3.5 && moods.isNotEmpty()) {
            stressRisk += 20
            stressParts.add("знижений настрій")
        }

        val stressText = if (stressParts.isNotEmpty()) {
            "У вас ${stressParts.joinToString(", ")}. Практикуйте дихальні вправи та медитацію."
        } else ""

        // РИЗИК ДЕПРЕСІЇ (Н)
        var depressionRisk = 0
        val depressionParts = mutableListOf<String>()

        val veryLowMoodDays = moods.count { it.moodLevel < 3 }
        if (veryLowMoodDays >= 7) {
            depressionRisk += 60
            depressionParts.add("$veryLowMoodDays днів поспіль низький настрій")
        }

        val sadTags = moods.flatMap { it.getTagsList() }
            .count { it in listOf("sad", "tired", "sick") }
        if (sadTags > 10) {
            depressionRisk += 25
            depressionParts.add("часті негативні емоції")
        }

        val avgSteps = database.dailyStepsDao().getAverageStepsSince(startDate) ?: 10000f
        if (avgSteps < 3000) {
            depressionRisk += 15
            depressionParts.add("низька фізична активність")
        }

        val depressionText = if (depressionParts.isNotEmpty()) {
            "Виявлено: ${depressionParts.joinToString(", ")}. Це може бути ознакою депресії. Рекомендуємо консультацію з лікарем або психологом."
        } else ""

        RiskAssessment(
            burnoutRisk.coerceIn(0, 100),
            burnoutText,
            sleepRisk.coerceIn(0, 100),
            sleepText,
            stressRisk.coerceIn(0, 100),
            stressText,
            depressionRisk.coerceIn(0, 100),
            depressionText
        )
    }

    suspend fun findPatterns(days: Int = 7): List<Pattern> = withContext(Dispatchers.IO) {
        val patterns = mutableListOf<Pattern>()
        val startDate = getDateBefore(days)
        val moods = database.moodDao().getMoodsSince(startDate)

        if (moods.size < 5) return@withContext patterns

        // Аналіз комбінацій тегів
        val tagCombos = listOf(
            Triple(listOf("stress", "tired"), "Стрес та Втому", "Коли відчуваєте стрес - відпочиньте перед роботою. Зробіть перерву 15 хвилин."),
            Triple(listOf("stress", "anxiety"), "Стрес та Тривогу", "Практикуйте дихальні вправи: вдих 4 сек, затримка 4 сек, видих 4 сек."),
            Triple(listOf("tired", "sick"), "Втому та Хворобу", "Ваш організм потребує відпочинку. Спробуйте виспатися та зменшити навантаження."),
            Triple(listOf("joy", "energy"), "Радість та Енергію", "Чудова комбінація! Продовжуйте робити те, що приносить вам радість."),
            Triple(listOf("stress", "work"), "Стрес на роботі", "Плануйте робочий день з перервами кожні 90 хвилин. Це знижує стрес.")
        )

        tagCombos.forEach { (combo, name, advice) ->
            val moodsWithCombo = moods.filter { mood ->
                val tags = mood.getTagsList()
                val factors = mood.getFactorsList()
                combo.all { it in tags || it in factors }
            }

            if (moodsWithCombo.size >= 2) {
                val avgMood = moodsWithCombo.map { it.moodLevel }.average()
                val allAvgMood = moods.map { it.moodLevel }.average()
                val diff = avgMood - allAvgMood

                val isNegative = diff < -0.5
                val percentage = ((avgMood - allAvgMood) / allAvgMood * 100).roundToInt()

                val description = if (isNegative) {
                    "Коли ви відчуваєте $name , ваш настрій падає до ${String.format("%.1f", avgMood)}/5 (на ${-percentage}% нижче середнього)."
                } else {
                    "Коли ви відчуваєте $name , ваш настрій підвищується до ${String.format("%.1f", avgMood)}/5 (на +${percentage}% вище середнього)."
                }

                patterns.add(Pattern(
                    emoji = if (isNegative) "⚠️" else "✅",
                    title = if (isNegative) "Негативний паттерн" else "Позитивний паттерн",
                    description = description,
                    frequency = "Це відбувалося ${moodsWithCombo.size} ${if (moodsWithCombo.size == 1) "раз" else if (moodsWithCombo.size < 5) "рази" else "разів"} за останній ${if (days == 7) "тиждень" else "$days днів"}.",
                    advice = advice,
                    isNegative = isNegative
                ))
            }
        }

        patterns.sortedBy { it.isNegative }.reversed()
    }

    suspend fun getWeeklySummary(days: Int = 7): WeeklySummary? = withContext(Dispatchers.IO) {
        if (days < 7) return@withContext null

        val startDate = getDateBefore(days)
        val moods = database.moodDao().getMoodsSince(startDate)
        val sleeps = database.sleepDao().getSleepRecordsSince(startDate)
        val steps = database.dailyStepsDao().getStepsSince(startDate)

        if (moods.isEmpty()) return@withContext null

        // Групуємо по датах
        val moodsByDate = moods.groupBy { it.date }
            .mapValues { it.value.map { m -> m.moodLevel }.average() }
        val sleepByDate = sleeps.associateBy { it.date }
        val stepsByDate = steps.associateBy { it.date }

        // Знаходимо найкращий/найгірший день
        val bestDay = moodsByDate.maxByOrNull { it.value }?.key ?: ""
        val worstDay = moodsByDate.minByOrNull { it.value }?.key ?: ""

        // Тренди
        val moodValues = moods.sortedBy { it.date }.map { it.moodLevel }
        val moodTrend = calculateTrend(moodValues)

        val sleepValues = sleeps.sortedBy { it.date }.map { it.durationMinutes }
        val sleepTrend = calculateTrend(sleepValues)

        val stepValues = steps.sortedBy { it.date }.map { it.steps }
        val activityTrend = calculateTrend(stepValues)

        WeeklySummary(
            bestDay = formatDate(bestDay),
            worstDay = formatDate(worstDay),
            moodTrend = moodTrend,
            sleepTrend = sleepTrend,
            activityTrend = activityTrend
        )
    }

    suspend fun generateRecommendations(days: Int = 7): List<HealthInsight> = withContext(Dispatchers.IO) {
        val insights = mutableListOf<HealthInsight>()
        val startDate = getDateBefore(days)

        val moods = database.moodDao().getMoodsSince(startDate)
        val avgSteps = database.dailyStepsDao().getAverageStepsSince(startDate) ?: 0f
        val avgSleep = database.sleepDao().getAverageSleepDuration(startDate) ?: 0f
        val avgSleepHours = avgSleep / 60f
        val avgMood = if (moods.isNotEmpty()) moods.map { it.moodLevel }.average() else 0.0

        // 🔴 КРИТИЧНІ
        if (avgSleepHours > 0 && avgSleepHours < 6) {
            insights.add(
                HealthInsight(
                    "😴",
                    "КРИТИЧНО: Недостатньо сну",
                    "Ви спите лише ${String.format("%.1f", avgSleepHours)} годин на день. Норма: 7-9 годин. Хронічний недосип підвищує ризик серцево-судинних захворювань. Спробуйте лягати о 22:30 та прибрати телефон за годину до сну.",
                    InsightType.WARNING,
                    InsightCategory.CRITICAL
                )
            )
        }

        val lowMoodDays = moods.count { it.moodLevel < 3 }
        if (lowMoodDays >= 5) {
            insights.add(
                HealthInsight(
                    "🆘",
                    "КРИТИЧНО: Тривалий знижений настрій",
                    "$lowMoodDays днів поспіль ваш настрій нижче 3.0. Це може бути ознакою депресії або вигорання. Настійно рекомендуємо звернутися до психолога або лікаря.",
                    InsightType.WARNING,
                    InsightCategory.CRITICAL
                )
            )
        }

        // 🟠 ПОТРЕБУЄ УВАГИ
        if (avgSteps > 0 && avgSteps < 5000) {
            insights.add(
                HealthInsight(
                    "🚶‍♂️",
                    "Низька фізична активність",
                    "Ви проходите ${avgSteps.roundToInt()} кроків на день. Рекомендовано мінімум 5000. Почніть з малого: паркуйтесь далі від входу, ходіть сходами замість ліфта, робіть 10-хвилинну прогулянку після обіду.",
                    InsightType.INFO,
                    InsightCategory.ATTENTION
                )
            )
        }

        val sleeps = database.sleepDao().getSleepRecordsSince(startDate)
        if (sleeps.size >= 3) {
            val durations = sleeps.map { it.durationMinutes }
            val maxDiff = (durations.maxOrNull() ?: 0) - (durations.minOrNull() ?: 0)
            if (maxDiff > 120) {
                insights.add(
                    HealthInsight(
                        "🕐",
                        "Нерегулярний режим сну",
                        "Різниця між вашим найдовшим та найкоротшим сном складає ${maxDiff / 60} годин. Нерегулярний сон погіршує якість відпочинку. Встановіть будильник на однаковий час навіть у вихідні.",
                        InsightType.INFO,
                        InsightCategory.ATTENTION
                    )
                )
            }
        }

        // 🟢 ПОЗИТИВНІ
        if (avgMood >= 4.0 && moods.isNotEmpty()) {
            insights.add(
                HealthInsight(
                    "😊",
                    "Чудовий настрій!",
                    "Ваш середній настрій ${String.format("%.1f", avgMood)}/5. Ви в чудовій емоційній формі! Продовжуйте робити те, що приносить вам радість.",
                    InsightType.GOOD,
                    InsightCategory.POSITIVE
                )
            )
        }

        if (avgSteps >= 8000) {
            insights.add(
                HealthInsight(
                    "🎉",
                    "Відмінна активність!",
                    "Ви проходите ${avgSteps.roundToInt()} кроків на день - це чудовий результат! Регулярна фізична активність знижує ризик серцево-судинних захворювань на 30-40%.",
                    InsightType.GOOD,
                    InsightCategory.POSITIVE
                )
            )
        }

        if (avgSleepHours >= 7 && avgSleepHours <= 9) {
            insights.add(
                HealthInsight(
                    "✨",
                    "Ідеальний сон!",
                    "Ви спите ${String.format("%.1f", avgSleepHours)} годин - це оптимально для здоров'я! Якісний сон підвищує імунітет, покращує пам'ять та знижує стрес.",
                    InsightType.GOOD,
                    InsightCategory.POSITIVE
                )
            )
        }

        // Якщо немає даних взагалі
        if (insights.isEmpty()) {
            insights.add(
                HealthInsight(
                    "📊",
                    "Почніть відстежувати своє здоров'я",
                    "Використовуйте додаток регулярно: вимірюйте пульс, записуйте сон та настрій, відстежуйте кроки. Через тиждень ви отримаєте персоналізовані рекомендації!",
                    InsightType.INFO,
                    InsightCategory.ATTENTION
                )
            )
        }

        insights
    }

    // ========== ДОПОМІЖНІ МЕТОДИ ==========

    private fun calculateStepsScore(avgSteps: Float): Int {
        return when {
            avgSteps >= 10000 -> 100
            avgSteps >= 8000 -> 90
            avgSteps >= 5000 -> 70
            avgSteps >= 3000 -> 50
            avgSteps >= 1000 -> 30
            else -> 10
        }
    }

    private fun calculateSleepScore(avgSleepMinutes: Float): Int {
        val hours = avgSleepMinutes / 60f
        return when {
            hours in 7.0..9.0 -> 100
            hours in 6.0..7.0 || hours in 9.0..10.0 -> 75
            hours in 5.0..6.0 || hours in 10.0..11.0 -> 50
            else -> 25
        }
    }

    private fun calculatePulseScore(bpm: Int): Int {
        return when {
            bpm in 60..100 -> 100
            bpm in 50..60 || bpm in 100..110 -> 75
            bpm in 40..50 || bpm in 110..120 -> 50
            else -> 25
        }
    }

    private fun calculateMoodScore(avgMood: Float): Int {
        return when {
            avgMood >= 4.5 -> 100
            avgMood >= 3.5 -> 75
            avgMood >= 2.5 -> 50
            else -> 25
        }
    }

    private fun calculateTrend(values: List<Int>): String {
        if (values.size < 2) return "→ стабільно"

        val first = values.take(values.size / 2).average()
        val second = values.takeLast(values.size / 2).average()
        val change = ((second - first) / first * 100).roundToInt()

        return when {
            change > 10 -> "↑ +$change%"
            change < -10 -> "↓ $change%"
            else -> "→ стабільно"
        }
    }

    private fun formatDate(date: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("EEEE", Locale("uk", "UA"))
            val parsed = inputFormat.parse(date)
            outputFormat.format(parsed ?: Date())
        } catch (e: Exception) {
            date
        }
    }

    private fun getDateBefore(days: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, -days)
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
    }
}