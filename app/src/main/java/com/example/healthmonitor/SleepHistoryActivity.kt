package com.example.healthmonitor

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
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

class SleepHistoryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvNoData: TextView
    private lateinit var tvAverageSleep: TextView
    private lateinit var database: HealthDatabase
    private lateinit var adapter: SleepHistoryAdapter

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sleep_history)

        findViewById<ImageButton>(btnBack).setOnClickListener {
            finish()
        }

        // Встановлюємо тайтл
        title = "Історія сну"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // ВИПРАВЛЕНО: Ініціалізація БД
        database = Room.databaseBuilder(
            applicationContext,
            HealthDatabase::class.java,
            "health_database"
        ).build()

        initViews()
        loadSleepHistory()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerViewSleep)
        tvNoData = findViewById(R.id.tvNoData)
        tvAverageSleep = findViewById(R.id.tvAverageSleep)

        adapter = SleepHistoryAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadSleepHistory() {
        lifecycleScope.launch {
            try {
                val sleepRecords = withContext(Dispatchers.IO) {
                    database.sleepDao().getLast30Sleeps()
                }

                if (sleepRecords.isEmpty()) {
                    tvNoData.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                    tvAverageSleep.visibility = View.GONE
                } else {
                    tvNoData.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    tvAverageSleep.visibility = View.VISIBLE

                    adapter.submitList(sleepRecords)

                    // Рахуємо середню тривалість
                    val averageMinutes = sleepRecords.map { it.durationMinutes }.average().toInt()
                    val hours = averageMinutes / 60
                    val minutes = averageMinutes % 60
                    tvAverageSleep.text = "Середня тривалість: ${hours}г ${minutes}хв"
                }

            } catch (e: Exception) {
                android.util.Log.e("SleepHistory", "Помилка завантаження історії", e)
                e.printStackTrace()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}