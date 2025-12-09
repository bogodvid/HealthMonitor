package com.example.healthmonitor

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.animation.ScaleAnimation
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

class MoodActivity : AppCompatActivity() {

    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var database: HealthDatabase
    private lateinit var sharedPrefs: SharedPreferences

    // Views - Настрій
    private lateinit var mood1: LinearLayout
    private lateinit var mood2: LinearLayout
    private lateinit var mood3: LinearLayout
    private lateinit var mood4: LinearLayout
    private lateinit var mood5: LinearLayout

    private lateinit var cardMood1: CardView
    private lateinit var cardMood2: CardView
    private lateinit var cardMood3: CardView
    private lateinit var cardMood4: CardView
    private lateinit var cardMood5: CardView

    // Views - Теги (окремі чіпси)
    private lateinit var tagStress: Chip
    private lateinit var tagTired: Chip
    private lateinit var tagJoy: Chip
    private lateinit var tagAnxiety: Chip
    private lateinit var tagEnergy: Chip
    private lateinit var tagSad: Chip
    private lateinit var tagSick: Chip
    private lateinit var tagCalm: Chip
    private lateinit var tagMotivated: Chip

    // Views - Фактори (окремі чіпси)
    private lateinit var factorWeather: Chip
    private lateinit var factorWork: Chip
    private lateinit var factorSocial: Chip
    private lateinit var factorFood: Chip
    private lateinit var factorCoffee: Chip
    private lateinit var factorExercise: Chip
    private lateinit var factorMedicine: Chip
    private lateinit var factorSleep: Chip
    private lateinit var factorAlcohol: Chip

    // Views - Інше
    private lateinit var tvGreeting: TextView
    private lateinit var tvSelectedMood: TextView
    private lateinit var etNote: EditText
    private lateinit var btnSaveMood: MaterialButton
    private lateinit var cardMoodHistory: CardView

    private var selectedMoodLevel: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mood)

        database = HealthDatabase.getInstance(applicationContext)
        sharedPrefs = getSharedPreferences("HealthMonitor", Context.MODE_PRIVATE)

        initViews()
        setupGreeting()
        setupBottomNavigation()
        setupMoodButtons()
        setupSaveButton()
        setupHistoryButton()
    }

    private fun initViews() {
        bottomNavigation = findViewById(R.id.bottomNavigation)

        // Настрої
        mood1 = findViewById(R.id.mood1)
        mood2 = findViewById(R.id.mood2)
        mood3 = findViewById(R.id.mood3)
        mood4 = findViewById(R.id.mood4)
        mood5 = findViewById(R.id.mood5)

        cardMood1 = findViewById(R.id.cardMood1)
        cardMood2 = findViewById(R.id.cardMood2)
        cardMood3 = findViewById(R.id.cardMood3)
        cardMood4 = findViewById(R.id.cardMood4)
        cardMood5 = findViewById(R.id.cardMood5)

        // Теги
        tagStress = findViewById(R.id.tagStress)
        tagTired = findViewById(R.id.tagTired)
        tagJoy = findViewById(R.id.tagJoy)
        tagAnxiety = findViewById(R.id.tagAnxiety)
        tagEnergy = findViewById(R.id.tagEnergy)
        tagSad = findViewById(R.id.tagSad)
        tagSick = findViewById(R.id.tagSick)
        tagCalm = findViewById(R.id.tagCalm)
        tagMotivated = findViewById(R.id.tagMotivated)

        // Фактори
        factorWeather = findViewById(R.id.factorWeather)
        factorWork = findViewById(R.id.factorWork)
        factorSocial = findViewById(R.id.factorSocial)
        factorFood = findViewById(R.id.factorFood)
        factorCoffee = findViewById(R.id.factorCoffee)
        factorExercise = findViewById(R.id.factorExercise)
        factorMedicine = findViewById(R.id.factorMedicine)
        factorSleep = findViewById(R.id.factorSleep)
        factorAlcohol = findViewById(R.id.factorAlcohol)

        // Інше
        tvGreeting = findViewById(R.id.tvGreeting)
        tvSelectedMood = findViewById(R.id.tvSelectedMood)
        etNote = findViewById(R.id.etNote)
        btnSaveMood = findViewById(R.id.btnSaveMood)
        cardMoodHistory = findViewById(R.id.cardMoodHistory)
    }

    private fun setupGreeting() {
        val userName = sharedPrefs.getString("user_name", "Користувач") ?: "Користувач"
        tvGreeting.text = "$userName, як ваше самопочуття?"
    }

    private fun setupMoodButtons() {
        mood1.setOnClickListener { selectMood(1, "😢 Дуже погано", cardMood1) }
        mood2.setOnClickListener { selectMood(2, "😕 Погано", cardMood2) }
        mood3.setOnClickListener { selectMood(3, "😐 Нормально", cardMood3) }
        mood4.setOnClickListener { selectMood(4, "🙂 Добре", cardMood4) }
        mood5.setOnClickListener { selectMood(5, "😄 Чудово", cardMood5) }
    }

    private fun selectMood(level: Int, text: String, selectedCard: CardView) {
        selectedMoodLevel = level
        tvSelectedMood.text = text
        btnSaveMood.isEnabled = true

        resetMoodCards()
        highlightCard(selectedCard)
    }

    private fun resetMoodCards() {
        val cards = listOf(cardMood1, cardMood2, cardMood3, cardMood4, cardMood5)
        cards.forEach { card ->
            card.cardElevation = 2f
            card.clearAnimation()
            card.scaleX = 1f
            card.scaleY = 1f
        }
    }

    private fun highlightCard(card: CardView) {
        card.cardElevation = 8f

        val scaleUp = ScaleAnimation(
            1f, 1.1f,
            1f, 1.1f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f
        )
        scaleUp.duration = 200
        scaleUp.fillAfter = true
        card.startAnimation(scaleUp)
    }

    private fun setupSaveButton() {
        btnSaveMood.setOnClickListener {
            saveMood()
        }
    }

    private fun setupHistoryButton() {
        cardMoodHistory.setOnClickListener {
            val intent = Intent(this, MoodHistoryActivity::class.java)
            startActivity(intent)
        }
    }

    private fun saveMood() {
        if (selectedMoodLevel == 0) {
            Toast.makeText(this, "⚠️ Оберіть настрій!", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val now = Date()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

                // Збираємо обрані теги
                val selectedTags = getSelectedTags()
                val tagsJson = JSONArray(selectedTags).toString()

                // Збираємо обрані фактори
                val selectedFactors = getSelectedFactors()
                val factorsJson = JSONArray(selectedFactors).toString()

                val moodRecord = MoodRecord(
                    date = dateFormat.format(now),
                    time = timeFormat.format(now),
                    moodLevel = selectedMoodLevel,
                    intensity = 5, // За замовчуванням (можна додати slider)
                    note = etNote.text?.toString() ?: "",
                    tags = tagsJson,
                    factors = factorsJson
                )

                withContext(Dispatchers.IO) {
                    database.moodDao().insert(moodRecord)
                }

                Toast.makeText(
                    this@MoodActivity,
                    "✅ Настрій збережено!",
                    Toast.LENGTH_SHORT
                ).show()

                // Очищаємо форму
                clearForm()

            } catch (e: Exception) {
                android.util.Log.e("MoodActivity", "Помилка збереження", e)
                Toast.makeText(
                    this@MoodActivity,
                    "❌ Помилка збереження",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun getSelectedTags(): List<String> {
        val selected = mutableListOf<String>()
        val chips = listOf(
            tagStress to "stress",
            tagTired to "tired",
            tagJoy to "joy",
            tagAnxiety to "anxiety",
            tagEnergy to "energy",
            tagSad to "sad",
            tagSick to "sick",
            tagCalm to "calm",
            tagMotivated to "motivated"
        )

        chips.forEach { (chip, value) ->
            if (chip.isChecked) selected.add(value)
        }

        return selected
    }

    private fun getSelectedFactors(): List<String> {
        val selected = mutableListOf<String>()
        val chips = listOf(
            factorWeather to "weather",
            factorWork to "work",
            factorSocial to "social",
            factorFood to "food",
            factorCoffee to "coffee",
            factorExercise to "exercise",
            factorMedicine to "medicine",
            factorSleep to "sleep",
            factorAlcohol to "alcohol"
        )

        chips.forEach { (chip, value) ->
            if (chip.isChecked) selected.add(value)
        }

        return selected
    }

    private fun clearForm() {
        selectedMoodLevel = 0
        tvSelectedMood.text = "Оберіть настрій"
        etNote.text?.clear()
        btnSaveMood.isEnabled = false
        resetMoodCards()

        // Скидаємо всі теги
        tagStress.isChecked = false
        tagTired.isChecked = false
        tagJoy.isChecked = false
        tagAnxiety.isChecked = false
        tagEnergy.isChecked = false
        tagSad.isChecked = false
        tagSick.isChecked = false
        tagCalm.isChecked = false
        tagMotivated.isChecked = false

        // Скидаємо всі фактори
        factorWeather.isChecked = false
        factorWork.isChecked = false
        factorSocial.isChecked = false
        factorFood.isChecked = false
        factorCoffee.isChecked = false
        factorExercise.isChecked = false
        factorMedicine.isChecked = false
        factorSleep.isChecked = false
        factorAlcohol.isChecked = false
    }

    private fun setupBottomNavigation() {
        bottomNavigation.selectedItemId = R.id.nav_mood

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                    finish()
                    true
                }
                R.id.nav_mood -> {
                    true
                }
                R.id.nav_health -> {
                    startActivity(Intent(this, HealthInsightsActivity::class.java))
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    finish()
                    true
                }
                else -> false
            }
        }
    }
}