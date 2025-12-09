package com.example.healthmonitor

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

class HealthInsightsActivity : AppCompatActivity() {

    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var database: HealthDatabase
    private lateinit var analyzer: SimpleHealthAnalyzer

    // Period buttons
    private lateinit var btn3Days: Button
    private lateinit var btn7Days: Button
    private lateinit var btn14Days: Button
    private var selectedPeriod = 7

    // Health Score
    private lateinit var tvHealthScore: TextView
    private lateinit var tvScoreDescription: TextView
    private lateinit var tvStepsScore: TextView
    private lateinit var tvSleepScore: TextView
    private lateinit var tvPulseScore: TextView
    private lateinit var tvMoodScore: TextView

    // Risks
    private lateinit var cardRisks: CardView

    // Patterns
    private lateinit var cardPatterns: CardView
    private lateinit var layoutPatterns: LinearLayout

    // Weekly Summary
    private lateinit var cardWeeklySummary: CardView
    private lateinit var layoutWeeklySummary: LinearLayout

    // Insights
    private lateinit var layoutInsights: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_health_insights)

        database = HealthDatabase.getInstance(applicationContext)
        analyzer = SimpleHealthAnalyzer(database)

        initViews()
        setupPeriodButtons()
        setupBottomNavigation()
        loadHealthData(selectedPeriod)
    }

    private fun initViews() {
        bottomNavigation = findViewById(R.id.bottomNavigation)

        btn3Days = findViewById(R.id.btn3Days)
        btn7Days = findViewById(R.id.btn7Days)
        btn14Days = findViewById(R.id.btn14Days)

        tvHealthScore = findViewById(R.id.tvHealthScore)
        tvScoreDescription = findViewById(R.id.tvScoreDescription)
        tvStepsScore = findViewById(R.id.tvStepsScore)
        tvSleepScore = findViewById(R.id.tvSleepScore)
        tvPulseScore = findViewById(R.id.tvPulseScore)
        tvMoodScore = findViewById(R.id.tvMoodScore)

        cardRisks = findViewById(R.id.cardRisks)

        cardPatterns = findViewById(R.id.cardPatterns)
        layoutPatterns = findViewById(R.id.layoutPatterns)

        cardWeeklySummary = findViewById(R.id.cardWeeklySummary)
        layoutWeeklySummary = findViewById(R.id.layoutWeeklySummary)

        layoutInsights = findViewById(R.id.layoutInsights)
    }

    private fun setupPeriodButtons() {
        btn3Days.setOnClickListener {
            if (selectedPeriod != 3) {
                selectedPeriod = 3
                updateButtonStates()
                loadHealthData(3)
            }
        }

        btn7Days.setOnClickListener {
            if (selectedPeriod != 7) {
                selectedPeriod = 7
                updateButtonStates()
                loadHealthData(7)
            }
        }

        btn14Days.setOnClickListener {
            if (selectedPeriod != 14) {
                selectedPeriod = 14
                updateButtonStates()
                loadHealthData(14)
            }
        }

        updateButtonStates()
    }

    private fun updateButtonStates() {
        val selectedColor = ContextCompat.getColor(this, R.color.primary_blue)
        val selectedTextColor = ContextCompat.getColor(this, android.R.color.white)
        val unselectedColor = ContextCompat.getColor(this, R.color.card_background)
        val unselectedTextColor = ContextCompat.getColor(this, R.color.text_primary)

        listOf(btn3Days, btn7Days, btn14Days).forEach { btn ->
            btn.setBackgroundColor(unselectedColor)
            btn.setTextColor(unselectedTextColor)
        }

        when (selectedPeriod) {
            3 -> {
                btn3Days.setBackgroundColor(selectedColor)
                btn3Days.setTextColor(selectedTextColor)
            }
            7 -> {
                btn7Days.setBackgroundColor(selectedColor)
                btn7Days.setTextColor(selectedTextColor)
            }
            14 -> {
                btn14Days.setBackgroundColor(selectedColor)
                btn14Days.setTextColor(selectedTextColor)
            }
        }
    }

    private fun loadHealthData(days: Int) {
        lifecycleScope.launch {
            try {
                val healthScore = analyzer.calculateHealthScore(days)
                displayHealthScore(healthScore)

                val risks = analyzer.assessRisks(days)
                displayRisks(risks)

                val patterns = analyzer.findPatterns(days)
                displayPatterns(patterns)

                if (days >= 7) {
                    val summary = analyzer.getWeeklySummary(days)
                    displayWeeklySummary(summary)
                } else {
                    cardWeeklySummary.visibility = View.GONE
                }

                val insights = analyzer.generateRecommendations(days)
                displayInsights(insights)

            } catch (e: Exception) {
                android.util.Log.e("HealthInsights", "Помилка", e)
            }
        }
    }

    private fun displayHealthScore(score: SimpleHealthAnalyzer.HealthScore) {
        tvHealthScore.text = "${score.totalScore}"

        tvScoreDescription.text = when {
            score.totalScore >= 80 -> "Чудовий стан здоров'я! 💪"
            score.totalScore >= 60 -> "Добре, є що покращити 👍"
            score.totalScore >= 40 -> "Потрібна увага ⚠️"
            score.totalScore > 0 -> "Зверніть увагу на своє здоров'я 🏥"
            else -> "Немає даних"
        }

        tvStepsScore.text = if (score.stepsScore > 0) "${score.stepsScore}/100" else "--/100"
        tvSleepScore.text = if (score.sleepScore > 0) "${score.sleepScore}/100" else "--/100"
        tvPulseScore.text = if (score.pulseScore > 0) "${score.pulseScore}/100" else "--/100"
        tvMoodScore.text = if (score.moodScore > 0) "${score.moodScore}/100" else "--/100"

        val color = when {
            score.totalScore >= 80 -> ContextCompat.getColor(this, R.color.health_green)
            score.totalScore >= 60 -> ContextCompat.getColor(this, R.color.health_orange)
            score.totalScore > 0 -> ContextCompat.getColor(this, R.color.health_red)
            else -> ContextCompat.getColor(this, R.color.text_secondary)
        }
        tvHealthScore.setTextColor(color)

        // Кольори для детальних показників
        setScoreColor(tvStepsScore, score.stepsScore)
        setScoreColor(tvSleepScore, score.sleepScore)
        setScoreColor(tvPulseScore, score.pulseScore)
        setScoreColor(tvMoodScore, score.moodScore)
    }

    private fun setScoreColor(textView: TextView, score: Int) {
        val color = when {
            score >= 75 -> R.color.health_green
            score >= 50 -> R.color.health_orange
            score > 0 -> R.color.health_red
            else -> R.color.text_secondary
        }
        textView.setTextColor(ContextCompat.getColor(this, color))
    }

    private fun displayRisks(risks: SimpleHealthAnalyzer.RiskAssessment) {
        val hasRisks = risks.burnoutRisk > 0 || risks.sleepDisorderRisk > 0 ||
                risks.stressRisk > 0 || risks.depressionRisk > 0

        if (!hasRisks) {
            cardRisks.visibility = View.GONE
            return
        }

        cardRisks.visibility = View.VISIBLE

        // Очищаємо старі дані
        (findViewById<LinearLayout>(R.id.layoutRisks) as LinearLayout).removeAllViews()

        val container = findViewById<LinearLayout>(R.id.layoutRisks)

        // Заголовок
        val title = TextView(this).apply {
            text = "⚠️ Оцінка ризиків"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setPadding(0, 0, 0, 24.dpToPx())
        }
        container.addView(title)

        // Burnout
        if (risks.burnoutRisk > 0) {
            container.addView(createRiskView(
                "🔥 Емоційне вигорання: ${risks.burnoutRisk}%",
                risks.burnoutRisk,
                risks.burnoutText
            ))
        }

        // Depression
        if (risks.depressionRisk > 0) {
            container.addView(createRiskView(
                "😔 Ризик депресії: ${risks.depressionRisk}%",
                risks.depressionRisk,
                risks.depressionText
            ))
        }

        // Sleep
        if (risks.sleepDisorderRisk > 0) {
            container.addView(createRiskView(
                "😴 Порушення сну: ${risks.sleepDisorderRisk}%",
                risks.sleepDisorderRisk,
                risks.sleepText
            ))
        }

        // Stress
        if (risks.stressRisk > 0) {
            container.addView(createRiskView(
                "😰 Хронічний стрес: ${risks.stressRisk}%",
                risks.stressRisk,
                risks.stressText
            ))
        }
    }

    private fun createRiskView(titleText: String, risk: Int, description: String): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16.dpToPx()
            }
        }

        val title = TextView(this).apply {
            text = titleText
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 8.dpToPx())
        }

        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                24.dpToPx()
            ).apply {
                bottomMargin = 8.dpToPx()
            }
            max = 100
            progress = risk
            progressTintList = ContextCompat.getColorStateList(context, getRiskColor(risk))
        }

        val descText = TextView(this).apply {
            text = description
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setLineSpacing(4.dpToPx().toFloat(), 1f)
        }

        layout.addView(title)
        layout.addView(progressBar)
        layout.addView(descText)

        return layout
    }

    private fun displayPatterns(patterns: List<SimpleHealthAnalyzer.Pattern>) {
        if (patterns.isEmpty()) {
            cardPatterns.visibility = View.GONE
            return
        }

        cardPatterns.visibility = View.VISIBLE
        layoutPatterns.removeAllViews()

        patterns.forEach { pattern ->
            val patternView = createPatternView(pattern)
            layoutPatterns.addView(patternView)
        }
    }

    private fun createPatternView(pattern: SimpleHealthAnalyzer.Pattern): View {
        val card = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12.dpToPx()
            }
            radius = 12.dpToPx().toFloat()
            cardElevation = 2.dpToPx().toFloat()

            val bgColor = if (pattern.isNegative) R.color.health_red_light else R.color.health_green_light
            setCardBackgroundColor(ContextCompat.getColor(context, bgColor))
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
        }

        val title = TextView(this).apply {
            text = "${pattern.emoji} ${pattern.title}"
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 8.dpToPx())
        }

        val description = TextView(this).apply {
            text = pattern.description
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setLineSpacing(4.dpToPx().toFloat(), 1f)
            setPadding(0, 0, 0, 8.dpToPx())
        }

        val frequency = TextView(this).apply {
            text = pattern.frequency
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setPadding(0, 0, 0, 12.dpToPx())
        }

        val advice = TextView(this).apply {
            text = "💡 Порада: ${pattern.advice}"
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.primary_blue))
            setLineSpacing(4.dpToPx().toFloat(), 1f)
        }

        layout.addView(title)
        layout.addView(description)
        layout.addView(frequency)
        layout.addView(advice)
        card.addView(layout)

        return card
    }

    private fun displayWeeklySummary(summary: SimpleHealthAnalyzer.WeeklySummary?) {
        if (summary == null) {
            cardWeeklySummary.visibility = View.GONE
            return
        }

        cardWeeklySummary.visibility = View.VISIBLE
        layoutWeeklySummary.removeAllViews()

        val summaryText = TextView(this).apply {
            text = buildString {
                append("🏆 Найкращий день: ${summary.bestDay}\n")
                append("📉 Найгірший день: ${summary.worstDay}\n\n")
                append("📊 Тренди:\n")
                append("  Настрій: ${summary.moodTrend}\n")
                append("  Сон: ${summary.sleepTrend}\n")
                append("  Активність: ${summary.activityTrend}")
            }
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setLineSpacing(4.dpToPx().toFloat(), 1f)
        }

        layoutWeeklySummary.addView(summaryText)
    }

    private fun displayInsights(insights: List<SimpleHealthAnalyzer.HealthInsight>) {
        layoutInsights.removeAllViews()

        // Групуємо за категоріями
        val critical = insights.filter { it.category == SimpleHealthAnalyzer.InsightCategory.CRITICAL }
        val attention = insights.filter { it.category == SimpleHealthAnalyzer.InsightCategory.ATTENTION }
        val positive = insights.filter { it.category == SimpleHealthAnalyzer.InsightCategory.POSITIVE }
        val personalized = insights.filter { it.category == SimpleHealthAnalyzer.InsightCategory.PERSONALIZED }

        // 🔴 КРИТИЧНО
        if (critical.isNotEmpty()) {
            layoutInsights.addView(createCategoryHeader("! КРИТИЧНО !"))
            layoutInsights.addView(createDivider())
            critical.forEach { layoutInsights.addView(createInsightCard(it)) }
        }

        // 🟠 ПОТРЕБУЄ УВАГИ
        if (attention.isNotEmpty()) {
            layoutInsights.addView(createCategoryHeader("| ПОТРЕБУЄ УВАГИ |"))
            layoutInsights.addView(createDivider())
            attention.forEach { layoutInsights.addView(createInsightCard(it)) }
        }

        // 🟢 ВИ МОЛОДЕЦЬ
        if (positive.isNotEmpty()) {
            layoutInsights.addView(createCategoryHeader("/ ВИ МОЛОДЕЦЬ /"))
            layoutInsights.addView(createDivider())
            positive.forEach { layoutInsights.addView(createInsightCard(it)) }
        }

        // 💡 ПЕРСОНАЛЬНІ ІНСАЙТИ
        if (personalized.isNotEmpty()) {
            layoutInsights.addView(createCategoryHeader("💡 ПЕРСОНАЛЬНІ ІНСАЙТИ"))
            layoutInsights.addView(createDivider())
            personalized.forEach { layoutInsights.addView(createInsightCard(it)) }
        }
    }

    private fun createCategoryHeader(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 16.dpToPx(), 0, 8.dpToPx())
        }
    }

    private fun createDivider(): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1.dpToPx()
            ).apply {
                bottomMargin = 12.dpToPx()
            }
            setBackgroundColor(ContextCompat.getColor(context, R.color.text_secondary))
            alpha = 0.2f
        }
    }

    private fun createInsightCard(insight: SimpleHealthAnalyzer.HealthInsight): CardView {
        val cardView = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12.dpToPx()
            }
            radius = 12.dpToPx().toFloat()
            cardElevation = 4.dpToPx().toFloat()
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_background))
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
        }

        val title = TextView(this).apply {
            text = "${insight.emoji} ${insight.title}"
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val message = TextView(this).apply {
            text = insight.message
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setPadding(0, 8.dpToPx(), 0, 0)
        }

        layout.addView(title)
        layout.addView(message)
        cardView.addView(layout)

        return cardView
    }

    private fun getRiskColor(risk: Int): Int {
        return when {
            risk >= 70 -> R.color.health_red
            risk >= 40 -> R.color.health_orange
            else -> R.color.health_green
        }
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun setupBottomNavigation() {
        bottomNavigation.selectedItemId = R.id.nav_health

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                    finish()
                    true
                }
                R.id.nav_mood -> {
                    startActivity(Intent(this, MoodActivity::class.java))
                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                    finish()
                    true
                }
                R.id.nav_health -> true
                else -> false
            }
        }
    }
}