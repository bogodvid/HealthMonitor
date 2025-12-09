package com.example.healthmonitor

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import com.example.healthmonitor.R.id.btnBack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat

class StepsHistoryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: StepsHistoryAdapter
    private lateinit var database: HealthDatabase

    private lateinit var tvTotalDays: TextView
    private lateinit var tvTotalSteps: TextView
    private lateinit var tvTotalDistance: TextView
    private lateinit var tvAverageSteps: TextView

    private val decimalFormat = DecimalFormat("#.##")

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_steps_history)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Історія кроків"

        // Кнопка назад
        findViewById<ImageButton>(btnBack).setOnClickListener {
            finish()
        }

        database = Room.databaseBuilder(
            applicationContext,
            HealthDatabase::class.java,
            "health_database"
        )
            .fallbackToDestructiveMigration()
            .build()

        initViews()
        loadStepsHistory()
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
        recyclerView = findViewById(R.id.recyclerViewStepsHistory)
        tvTotalDays = findViewById(R.id.tvTotalDays)
        tvTotalSteps = findViewById(R.id.tvTotalSteps)
        tvTotalDistance = findViewById(R.id.tvTotalDistance)
        tvAverageSteps = findViewById(R.id.tvAverageSteps)

        adapter = StepsHistoryAdapter(this) // ДОДАНО: передаємо context
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadStepsHistory() {
        lifecycleScope.launch {
            try {
                val history = withContext(Dispatchers.IO) {
                    database.dailyStepsDao().getLast30Days()
                }

                // ДОДАНО: Отримуємо ціль з налаштувань
                val sharedPrefs = getSharedPreferences("HealthMonitor", Context.MODE_PRIVATE)
                val stepsGoal = sharedPrefs.getInt("steps_goal", 10000)

                adapter.updateData(history, stepsGoal) // ОНОВЛЕНО: передаємо ціль

                // Розраховуємо статистику
                if (history.isNotEmpty()) {
                    val totalSteps = history.sumOf { it.steps }
                    val totalDistance = history.sumOf { it.distance.toDouble() }
                    val averageSteps = totalSteps / history.size

                    tvTotalDays.text = history.size.toString()
                    tvTotalSteps.text = totalSteps.toString()
                    tvTotalDistance.text = "${decimalFormat.format(totalDistance)} км"
                    tvAverageSteps.text = "$averageSteps кроків/день"
                } else {
                    tvTotalDays.text = "0"
                    tvTotalSteps.text = "0"
                    tvTotalDistance.text = "0 км"
                    tvAverageSteps.text = "0 кроків/день"
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}