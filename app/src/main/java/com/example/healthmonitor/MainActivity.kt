package com.example.healthmonitor

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.content.BroadcastReceiver
import android.content.IntentFilter
import com.google.android.material.bottomnavigation.BottomNavigationView


class MainActivity : AppCompatActivity() {

    // UI елементи
    private lateinit var tvGreeting: TextView
    private lateinit var circularProgress: CircularProgressView
    private lateinit var tvStepsGoal: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnStartStopSteps: MaterialButton
    private lateinit var btnResetSteps: MaterialButton
    private lateinit var btnExpandSteps: TextView
    private lateinit var layoutStepButtons: LinearLayout
    private lateinit var cardPulse: CardView
    private lateinit var cardSteps: CardView
    private lateinit var btnSettings: Button
    private lateinit var tvLastPulse: TextView
    private lateinit var tvLastPulseTime: TextView
    private lateinit var tvDailyTip: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvCalories: TextView
    private lateinit var activityChart: ActivityChartView
    private lateinit var btnPulseHistory: CardView
    private lateinit var btnSleepHistory: CardView
    private lateinit var btnStepsHistory: CardView


    // UI елементи для сну
    private lateinit var cardSleep: CardView
    private lateinit var tvSleepDuration: TextView
    private lateinit var tvSleepTime: TextView
    private lateinit var tvSleepDate: TextView
    private lateinit var tvAverageSleep: TextView
    private lateinit var tvSleepQuality: TextView
    private lateinit var tvSleepStatus: TextView
    // private lateinit var btnTestSleep: MaterialButton // НОВИЙ
    // НОВИЙ: Bottom Navigation
    private lateinit var bottomNavigation: BottomNavigationView

    // Крокомір
    private var stepCounterService: StepCounterService? = null
    private var isServiceBound = false
    private var isServiceRunning = false
    private var isStepButtonsExpanded = false

    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var database: HealthDatabase

    private val decimalFormat = DecimalFormat("#.##")
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    // Handler для автоматичного оновлення графіка
    private val updateHandler = Handler(Looper.getMainLooper())
    private val updateInterval = 30000L // 30 секунд

    private val autoUpdateRunnable = object : Runnable {
        override fun run() {
            if (isServiceRunning) {
                loadTodayActivityChart()
                updateUI()
            }
            updateHandler.postDelayed(this, updateInterval)
        }
    }

    private val sleepDataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            android.util.Log.d("MainActivity", "📢 Отримано broadcast - оновлюю дані сну")
            loadSleepData()
        }
    }

    private val healthTips = arrayOf(
        "💧 Пийте щонайменше 8 склянок води на день для підтримання гідратації",
        "🥗 Включайте в раціон більше овочів та фруктів різних кольорів",
        "😴 Спіть 7-9 годин на добу для відновлення організму",
        "🧘‍♀️ Практикуйте медитацію або дихальні вправи для зменшення стресу",
        "📱 Робіть перерви від екранів кожні 20-30 хвилин",
        "🍎 Замініть солодощі на здорові перекуси: горіхи, фрукти",
        "☀️ Проводьте час на свіжому повітрі для отримання вітаміну D",
        "🤝 Підтримуйте соціальні зв'язки - це важливо для психічного здоров'я",
        "🦷 Не забувайте чистити зуби двічі на день",
        "🧴 Мийте руки регулярно для запобігання інфекціям",
        "📖 Читайте книги для розвитку мозку та зменшення стресу",
        "🎵 Слухайте улюблену музику - це покращує настрій",
        "💪 Робіть розтяжку щодня для гнучкості м'язів"
    )

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as StepCounterService.LocalBinder
            stepCounterService = binder.getService()
            isServiceBound = true
            updateUI()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isServiceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPrefs = getSharedPreferences("HealthMonitor", Context.MODE_PRIVATE)

        database = Room.databaseBuilder(
            applicationContext,
            HealthDatabase::class.java,
            "health_database"
        )
            .fallbackToDestructiveMigration()
            .build()

        checkFirstLaunchAndProfile()

        initViews()
        setupListeners()
        setupBottomNavigation() // НОВИй
        setupGreeting()
        showDailyTip()
        checkAndInitializeNewDay()
        MoodReminders.scheduleDailyReminders(this)
        updateUI()
        loadLastPulseData()
        loadTodayActivityChart()
        loadSleepData()
    }

    override fun onStart() {
        super.onStart()
        checkServiceStatus()
    }

    private fun checkServiceStatus() {
        val isRunning = isServiceRunning(StepCounterService::class.java)
        isServiceRunning = isRunning

        if (isRunning && !isServiceBound) {
            val serviceIntent = Intent(this, StepCounterService::class.java)
            bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        }

        updateUI()
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun checkFirstLaunchAndProfile() {
        val userName = sharedPrefs.getString("user_name", null)
        val userHeight = sharedPrefs.getFloat("user_height", 0f)
        val userWeight = sharedPrefs.getFloat("user_weight", 0f)

        if (userName.isNullOrEmpty() || userHeight == 0f || userWeight == 0f) {
            val intent = Intent(this, SplashActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun initViews() {
        tvGreeting = findViewById(R.id.tvGreeting)
        circularProgress = findViewById(R.id.circularProgress)
        tvStepsGoal = findViewById(R.id.tvStepsGoal)
        tvStatus = findViewById(R.id.tvStatus)
        btnStartStopSteps = findViewById(R.id.btnStartStopSteps)
        btnResetSteps = findViewById(R.id.btnResetSteps)
        btnExpandSteps = findViewById(R.id.btnExpandSteps)
        layoutStepButtons = findViewById(R.id.layoutStepButtons)
        cardPulse = findViewById(R.id.cardPulse)
        cardSteps = findViewById(R.id.cardSteps)
        btnSettings = findViewById(R.id.btnSettings)
        tvLastPulse = findViewById(R.id.tvLastPulse)
        tvLastPulseTime = findViewById(R.id.tvLastPulseTime)
        tvDailyTip = findViewById(R.id.tvDailyTip)
        tvDistance = findViewById(R.id.tvDistance)
        tvCalories = findViewById(R.id.tvCalories)
        activityChart = findViewById(R.id.activityChart)

        btnPulseHistory = findViewById(R.id.btnPulseHistory)
        btnSleepHistory = findViewById(R.id.btnSleepHistory)
        btnStepsHistory = findViewById(R.id.btnStepsHistory)

        // Ініціалізація UI для сну
        cardSleep = findViewById(R.id.cardSleep)
        tvSleepDuration = findViewById(R.id.tvSleepDuration)
        tvSleepTime = findViewById(R.id.tvSleepTime)
        tvSleepDate = findViewById(R.id.tvSleepDate)
        tvAverageSleep = findViewById(R.id.tvAverageSleep)
        tvSleepQuality = findViewById(R.id.tvSleepQuality)
        tvSleepStatus = findViewById(R.id.tvSleepStatus)
        // btnTestSleep = findViewById(R.id.btnTestSleep) // НОВИЙ
        // НОВИЙ: Bottom Navigation
        bottomNavigation = findViewById(R.id.bottomNavigation)
    }

    private fun setupListeners() {
        btnExpandSteps.setOnClickListener {
            toggleStepButtons()
        }

        // ===== ОБРОБНИКИ =====

        // Пульсомір
        cardPulse.setOnClickListener {
            val intent = Intent(this, PulseActivity::class.java)
            startActivity(intent)
        }

        btnPulseHistory.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }

        // Сон
        cardSleep.setOnClickListener {
            val intent = Intent(this, SleepHistoryActivity::class.java)
            startActivity(intent)
        }

        btnSleepHistory.setOnClickListener {
            val intent = Intent(this, SleepHistoryActivity::class.java)
            startActivity(intent)
        }

        cardSleep.setOnLongClickListener {
            loadSleepData()
            Toast.makeText(this, "🔄 Дані сну оновлено", Toast.LENGTH_SHORT).show()
            true
        }

        // Кроки
        cardSteps.setOnClickListener {
            val intent = Intent(this, StepsHistoryActivity::class.java)
            startActivity(intent)
        }

        btnStepsHistory.setOnClickListener {
            val intent = Intent(this, StepsHistoryActivity::class.java)
            startActivity(intent)
        }

        // ===== РЕШТА БЕЗ ЗМІН =====

        btnStartStopSteps.setOnClickListener {
            if (isServiceRunning) {
                stopStepCountingService()
            } else {
                startStepCountingService()
            }
        }

        btnResetSteps.setOnClickListener {
            resetDailySteps()
        }

        btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivityForResult(intent, 100)
        }

        tvDailyTip.setOnClickListener {
            showDailyTip()
        }
    }

    private fun toggleStepButtons() {
        isStepButtonsExpanded = !isStepButtonsExpanded

        if (isStepButtonsExpanded) {
            layoutStepButtons.visibility = View.VISIBLE
            btnExpandSteps.text = "▲ Сховати керування ▲"
        } else {
            layoutStepButtons.visibility = View.GONE
            btnExpandSteps.text = "▼ Керування крокоміром ▼"
        }
    }

    private fun setupGreeting() {
        val userName = sharedPrefs.getString("user_name", "Користувач") ?: "Користувач"
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        val greeting = when (hour) {
            in 5..11 -> "Доброго ранку"
            in 12..17 -> "Добрий день"
            in 18..22 -> "Добрий вечір"
            else -> "Доброї ночі"
        }

        tvGreeting.text = "$greeting, $userName!"
    }

    private fun showDailyTip() {
        val randomTip = healthTips.random()
        tvDailyTip.text = randomTip
    }

    private fun checkAndInitializeNewDay() {
        val today = dateFormat.format(Date())
        val lastActiveDate = sharedPrefs.getString("last_active_date", "")

        if (lastActiveDate != today) {
            lifecycleScope.launch {
                if (!lastActiveDate.isNullOrEmpty()) {
                    saveDailySteps(lastActiveDate)
                }

                resetDailyStepsInternal()

                sharedPrefs.edit()
                    .putString("last_active_date", today)
                    .apply()

                updateUI()
            }
        }
    }

    private suspend fun saveDailySteps(date: String) {
        val todaySteps = sharedPrefs.getInt("today_steps", 0)
        if (todaySteps > 0) {
            val distance = calculateDistance(todaySteps)
            val calories = calculateCalories(todaySteps)

            val dailySteps = DailySteps(
                date = date,
                steps = todaySteps,
                distance = distance,
                calories = calories
            )

            withContext(Dispatchers.IO) {
                try {
                    database.dailyStepsDao().insertOrUpdate(dailySteps)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun calculateDistance(steps: Int): Float {
        val userHeight = sharedPrefs.getFloat("user_height", 170f)
        val stepLength = userHeight * 0.415f / 100f
        val distanceInMeters = steps * stepLength
        return distanceInMeters / 1000f
    }

    private fun calculateCalories(steps: Int): Int {
        val userWeight = sharedPrefs.getFloat("user_weight", 70f)
        val userHeight = sharedPrefs.getFloat("user_height", 170f)
        val caloriesPerStep = (userWeight * 0.0005f) + (userHeight * 0.000015f)
        return (steps * caloriesPerStep).toInt()
    }

    private fun loadLastPulseData() {
        lifecycleScope.launch {
            try {
                val lastPulse = withContext(Dispatchers.IO) {
                    database.pulseDao().getLatestPulse()
                }

                if (lastPulse != null) {
                    updatePulseDisplay(lastPulse.bpm, lastPulse.timestamp)
                } else {
                    updatePulseDisplay(null, null)
                }
            } catch (e: Exception) {
                updatePulseDisplay(null, null)
            }
        }
    }

    private fun loadTodayActivityChart() {
        lifecycleScope.launch {
            try {
                val today = dateFormat.format(Date())

                android.util.Log.d("ActivityChart", "=== ПОЧАТОК ЗАВАНТАЖЕННЯ ===")
                android.util.Log.d("ActivityChart", "Сьогоднішня дата: $today")

                // ДІАГНОСТИКА: Скільки всього записів в БД
                val totalRecords = withContext(Dispatchers.IO) {
                    database.hourlyActivityDao().getRecordCount()
                }
                android.util.Log.d("ActivityChart", "Всього записів в БД: $totalRecords")

                // ДІАГНОСТИКА: Які дати є в БД
                val allDates = withContext(Dispatchers.IO) {
                    database.hourlyActivityDao().getAllDates()
                }
                android.util.Log.d("ActivityChart", "Дати в БД: ${allDates.joinToString()}")

                // Завантажуємо дані
                val hourlyData = withContext(Dispatchers.IO) {
                    database.hourlyActivityDao().getActivityForDate(today)
                }

                android.util.Log.d("ActivityChart", "Отримано ${hourlyData.size} записів для дати $today")

                // Логуємо КОЖЕН запис
                hourlyData.forEachIndexed { index, record ->
                    android.util.Log.d("ActivityChart", "[$index] date='${record.date}', hour=${record.hour}, steps=${record.steps}")
                }

                // Видалення старих даних (>7 днів)
                withContext(Dispatchers.IO) {
                    val calendar = Calendar.getInstance()
                    calendar.add(Calendar.DAY_OF_MONTH, -7)
                    val sevenDaysAgo = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)

                    database.hourlyActivityDao().deleteOldActivity(sevenDaysAgo)
                    android.util.Log.d("ActivityChart", "Видалено дані старіше: $sevenDaysAgo")
                }

                // ПОТРІЙНА ПЕРЕВІРКА фільтрації
                val chartData = hourlyData
                    .filter { record ->
                        val matches = record.date == today
                        if (!matches) {
                            android.util.Log.e("ActivityChart", " ЗНАЙДЕНО ЗАПИС З ІНШОЮ ДАТОЮ: '${record.date}' замість '$today'")
                        }
                        matches
                    }
                    .map { Pair(it.hour, it.steps) }
                    .sortedBy { it.first }

                android.util.Log.d("ActivityChart", "Після фільтрації: ${chartData.size} записів")
                android.util.Log.d("ActivityChart", "Дані для графіка: $chartData")
                android.util.Log.d("ActivityChart", "=== КІНЕЦЬ ЗАВАНТАЖЕННЯ ===\n")

                activityChart.setData(chartData)

            } catch (e: Exception) {
                android.util.Log.e("ActivityChart", "❌ ПОМИЛКА завантаження графіка", e)
                e.printStackTrace()
                activityChart.setData(emptyList())
            }
        }
    }

    private fun loadSleepData() {
        lifecycleScope.launch {
            try {
                val latestSleep = withContext(Dispatchers.IO) {
                    database.sleepDao().getLatestSleep()
                }

                val calendar = Calendar.getInstance()
                calendar.add(Calendar.DAY_OF_MONTH, -7)
                val sevenDaysAgo = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(calendar.time)

                val averageDuration = withContext(Dispatchers.IO) {
                    database.sleepDao().getAverageSleepDuration(sevenDaysAgo)
                }

                val isSleeping = stepCounterService?.isSleeping() ?: false

                updateSleepDisplay(latestSleep, averageDuration, isSleeping)

            } catch (e: Exception) {
                e.printStackTrace()
                updateSleepDisplay(null, null, false)
            }
        }
    }

    // НОВИЙ: Тестова симуляція сну
    private fun testSleepSimulation() {
        lifecycleScope.launch {
            try {
                val now = System.currentTimeMillis()
                // Симулюємо 7-годинний сон
                val sleepStart = now - (7 * 60 * 60 * 1000L) // 7 годин тому
                val sleepEnd = now
                val duration = ((sleepEnd - sleepStart) / 60000).toInt()

                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                val testSleep = SleepRecord(
                    date = today,
                    sleepStartTime = sleepStart,
                    sleepEndTime = sleepEnd,
                    durationMinutes = duration
                )

                withContext(Dispatchers.IO) {
                    database.sleepDao().insert(testSleep)
                }

                android.util.Log.d("MainActivity", "✅ Тестовий сон додано: ${duration} хв")
                Toast.makeText(
                    this@MainActivity,
                    "✅ Тестовий сон додано!\nТривалість: ${duration/60}г ${duration%60}хв",
                    Toast.LENGTH_LONG
                ).show()

                loadSleepData()

            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "❌ Помилка додавання тестового сну", e)
                Toast.makeText(this@MainActivity, "❌ Помилка: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }
    private fun clearAllSleepRecords() {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    database.sleepDao().deleteAllSleeps()
                }

                Toast.makeText(this@MainActivity, "🗑️ Всі записи сну видалено", Toast.LENGTH_SHORT).show()
                loadSleepData()

            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Помилка видалення", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }
    }

    private fun updateSleepDisplay(
        latestSleep: SleepRecord?,
        averageDuration: Float?,
        isSleeping: Boolean
    ) {
        if (isSleeping) {
            tvSleepStatus.visibility = View.VISIBLE
            tvSleepStatus.text = "💤 Спить зараз"
        } else {
            tvSleepStatus.visibility = View.GONE
        }

        if (latestSleep != null) {
            tvSleepDuration.text = latestSleep.getFormattedDuration()
            tvSleepTime.text = "${latestSleep.getFormattedStartTime()} → ${latestSleep.getFormattedEndTime()}"

            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            if (latestSleep.date == today) {
                tvSleepDate.text = "сьогодні"
                tvSleepDate.setTextColor(getColor(android.R.color.holo_green_dark))
            } else {
                tvSleepDate.text = latestSleep.getFormattedDate()
                tvSleepDate.setTextColor(getColor(android.R.color.darker_gray))
            }
        } else {
            tvSleepDuration.text = "---"
            tvSleepTime.text = "Немає даних"
            tvSleepDate.text = ""
        }

        if (averageDuration != null && averageDuration > 0) {
            val hours = (averageDuration / 60).toInt()
            val minutes = (averageDuration % 60).toInt()
            tvAverageSleep.text = "${hours}г ${minutes}хв"
        } else {
            tvAverageSleep.text = "---"
        }

        tvSleepQuality.text = "Норма"
    }

    private fun updatePulseDisplay(bpm: Int?, timestamp: Long?) {
        if (bpm != null && timestamp != null) {
            tvLastPulse.text = "$bpm BPM"

            val measurementDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

            val timeText = if (measurementDate == today) {
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
                "о $time"
            } else {
                SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
            }

            tvLastPulseTime.text = timeText
            tvLastPulseTime.setTextColor(
                if (measurementDate == today) {
                    getColor(android.R.color.darker_gray)
                } else {
                    getColor(android.R.color.holo_orange_light)
                }
            )
        } else {
            tvLastPulse.text = "---"
            tvLastPulseTime.text = "Немає даних"
            tvLastPulseTime.setTextColor(getColor(android.R.color.darker_gray))
        }
    }

    private fun startStepCountingService() {
        val serviceIntent = Intent(this, StepCounterService::class.java)
        startForegroundService(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        isServiceRunning = true

        sharedPrefs.edit().putBoolean("service_running", true).apply()

        Toast.makeText(this, "Підрахунок кроків увімкнено", Toast.LENGTH_SHORT).show()
        updateUI()
    }

    private fun stopStepCountingService() {
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }

        val serviceIntent = Intent(this, StepCounterService::class.java)
        stopService(serviceIntent)
        isServiceRunning = false

        sharedPrefs.edit().putBoolean("service_running", false).apply()

        Toast.makeText(this, "Підрахунок кроків вимкнено", Toast.LENGTH_SHORT).show()
        updateUI()
    }

    private fun resetDailySteps() {
        resetDailyStepsInternal()
        Toast.makeText(this, "Денний лічильник скинуто", Toast.LENGTH_SHORT).show()
        updateUI()
        loadTodayActivityChart()
    }

    private fun resetDailyStepsInternal() {
        stepCounterService?.resetTodaySteps()
        sharedPrefs.edit()
            .putInt("today_steps", 0)
            .apply()
    }

    private fun updateUI() {
        val todaySteps = stepCounterService?.getTodaySteps()
            ?: sharedPrefs.getInt("today_steps", 0)

        val stepsGoal = sharedPrefs.getInt("steps_goal", 10000)
        circularProgress.setSteps(todaySteps, stepsGoal, animate = true)

        tvStepsGoal.text = "Ціль: $stepsGoal кроків"

        val distance = calculateDistance(todaySteps)
        val calories = calculateCalories(todaySteps)

        tvDistance.text = decimalFormat.format(distance)
        tvCalories.text = calories.toString()

        if (isServiceRunning) {
            tvStatus.text = "Статус крокоміра: Активний"
            btnStartStopSteps.text = "Зупинити"
            btnStartStopSteps.backgroundTintList =
                ContextCompat.getColorStateList(this, android.R.color.holo_red_light)
        } else {
            tvStatus.text = "Статус крокоміра: Неактивний"
            btnStartStopSteps.text = "Запустити"
            btnStartStopSteps.backgroundTintList =
                ContextCompat.getColorStateList(this, android.R.color.holo_green_light)
        }

        loadSleepData()
    }

    override fun onResume() {
        super.onResume()
        checkAndInitializeNewDay()
        updateUI()
        loadLastPulseData()
        loadTodayActivityChart()
        loadSleepData()
        showDailyTip()

        // Запускаємо автоматичне оновлення графіка
        updateHandler.post(autoUpdateRunnable)

        // Реєструємо receiver для оновлення сну
        try {
            registerReceiver(
                sleepDataReceiver,
                IntentFilter("com.example.healthmonitor.SLEEP_DATA_UPDATED"),
                Context.RECEIVER_NOT_EXPORTED
            )
            android.util.Log.d("MainActivity", "✅ Receiver зареєстровано")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "❌ Помилка реєстрації receiver", e)
            e.printStackTrace()
        }
    }

    override fun onPause() {
        super.onPause()
        // Зупиняємо оновлення коли додаток не видимий
        updateHandler.removeCallbacks(autoUpdateRunnable)

        // НОВИЙ: Дереєструємо receiver
        try {
            unregisterReceiver(sleepDataReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isServiceBound) {
            unbindService(serviceConnection)
        }
    }
    // НОВИЙ: Налаштування Bottom Navigation
    private fun setupBottomNavigation() {
        bottomNavigation.selectedItemId = R.id.nav_home

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    // Вже на головній
                    true
                }
                R.id.nav_mood -> {
                    startActivity(Intent(this, MoodActivity::class.java))
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    finish()
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