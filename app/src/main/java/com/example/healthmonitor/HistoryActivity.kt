package com.example.healthmonitor

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PulseHistoryAdapter
    private lateinit var database: HealthDatabase

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Історія вимірювань"

        database = Room.databaseBuilder(
            applicationContext,
            HealthDatabase::class.java,
            "health_database"
        ).build()

        initViews()
        loadHistory()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = PulseHistoryAdapter()
        recyclerView.adapter = adapter
    }

    private fun loadHistory() {
        // Використовуємо lifecycleScope замість Thread
        lifecycleScope.launch {
            try {
                // Отримуємо дані з бази в IO контексті
                val records = withContext(Dispatchers.IO) {
                    database.pulseDao().getAllPulseRecords()
                }

                // Оновлюємо UI в Main потоці (тут ми вже в Main контексті)
                adapter.updateData(records)

            } catch (e: Exception) {
                // Обробка помилок
                e.printStackTrace()
                // Можна додати Toast з повідомленням про помилку
                // Toast.makeText(this@HistoryActivity, "Помилка завантаження даних", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}