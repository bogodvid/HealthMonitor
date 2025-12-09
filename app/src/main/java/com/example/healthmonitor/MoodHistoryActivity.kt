package com.example.healthmonitor

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MoodHistoryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvNoData: TextView
    private lateinit var tvAverageMood: TextView
    private lateinit var database: HealthDatabase
    private lateinit var adapter: MoodHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mood_history)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        title = "Історія настрою"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        database = HealthDatabase.getInstance(applicationContext)

        initViews()
        loadMoodHistory()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerViewMoods)
        tvNoData = findViewById(R.id.tvNoData)
        tvAverageMood = findViewById(R.id.tvAverageMood)

        adapter = MoodHistoryAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadMoodHistory() {
        lifecycleScope.launch {
            try {
                val moods = withContext(Dispatchers.IO) {
                    database.moodDao().getAllMoods()
                }

                if (moods.isEmpty()) {
                    tvNoData.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                    tvAverageMood.visibility = View.GONE
                } else {
                    tvNoData.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    tvAverageMood.visibility = View.VISIBLE

                    adapter.submitList(moods)

                    // Середній настрій
                    val avgMood = moods.map { it.moodLevel }.average()
                    val avgEmoji = when {
                        avgMood >= 4.5 -> "😄"
                        avgMood >= 3.5 -> "🙂"
                        avgMood >= 2.5 -> "😐"
                        avgMood >= 1.5 -> "😕"
                        else -> "😢"
                    }
                    tvAverageMood.text = "Середній настрій: $avgEmoji ${String.format("%.1f", avgMood)}/5"
                }

            } catch (e: Exception) {
                android.util.Log.e("MoodHistory", "Помилка завантаження", e)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}