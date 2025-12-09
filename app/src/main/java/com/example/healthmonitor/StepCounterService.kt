package com.example.healthmonitor

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

class StepCounterService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var stepCounterSensor: Sensor? = null
    private var stepDetectorSensor: Sensor? = null
    private var accelerometerSensor: Sensor? = null

    private var todaySteps = 0
    private var initialStepCount = 0
    private var currentSteps = 0

    // Для погодинного відстеження
    private var currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    private var currentHourDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) // НОВИЙ!!!
    private var stepsThisHour = 0
    private var lastSavedDate = ""

    // Для акселерометра - ЗБАЛАНСОВАНІ параметри
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var lastStepTime = 0L
    private val stepThreshold = 9.5f
    private val stepDelayMs = 300L

    // Буфер для фільтрації різких поодиноких рухів
    private val recentAccelerations = mutableListOf<Float>()
    private val accelerationBufferSize = 3

    // Змінні для відстеження сну
    private var isSleepTracking = false
    private var sleepStartTime = 0L
    private var lastMovementTime = 0L
    private var movementCount = 0

    private val noMovementThresholdMs = 30 * 60 * 1000L
    private val wakeUpStepsThreshold = 5

    private var recentStepsCount = 0
    private var recentStepsStartTime = 0L

    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var notificationManager: NotificationManager
    private lateinit var database: HealthDatabase

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    // Handler для періодичної перевірки дати
    private val dateCheckHandler = Handler(Looper.getMainLooper())
    private val dateCheckRunnable = object : Runnable {
        override fun run() {
            checkForNewDay()
            dateCheckHandler.postDelayed(this, 60000) // Перевірка кожну хвилину
        }
    }

    // НОВИЙ: Wake Lock щоб Service не вбивався
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "StepCounterChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP_SERVICE = "STOP_SERVICE"
        const val ACTION_NEW_DAY = "NEW_DAY"
    }

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): StepCounterService = this@StepCounterService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()

        sharedPrefs = getSharedPreferences("HealthMonitor", Context.MODE_PRIVATE)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        currentHourDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) // НОВИЙ!!!
        stepsThisHour = 0

        android.util.Log.d("HourlyActivity", "Service створено. Поточна година: $currentHour, дата: $currentHourDate")

        database = Room.databaseBuilder(
            applicationContext,
            HealthDatabase::class.java,
            "health_database"
        )
            .fallbackToDestructiveMigration()
            .build()

        loadTodaySteps()
        createNotificationChannel()
        initializeSensors()
        scheduleMidnightReset()

        // НОВИЙ: Acquire Wake Lock
        acquireWakeLock()
    }

    // НОВИЙ: Wake Lock метод
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "HealthMonitor::StepCounterWakeLock"
            )
            wakeLock?.acquire(24 * 60 * 60 * 1000L) // 24 години
            android.util.Log.d("StepCounterService", "✅ WakeLock acquired")
        } catch (e: Exception) {
            android.util.Log.e("StepCounterService", "❌ Failed to acquire WakeLock", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                saveTodayStepsToDatabase()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_NEW_DAY -> {
                handleNewDay()
            }
        }

        startForeground(NOTIFICATION_ID, createNotification())
        startStepCounting()

        // Запускаємо періодичну перевірку дати
        dateCheckHandler.post(dateCheckRunnable)

        return START_STICKY
    }

    private fun loadTodaySteps() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        lastSavedDate = sharedPrefs.getString("last_active_date", "") ?: ""

        if (lastSavedDate != today) {
            // Новий день - зберігаємо вчорашні дані та обнуляємо
            if (lastSavedDate.isNotEmpty()) {
                savePreviousDaySteps(lastSavedDate)
            }

            // ПОВНЕ ОБНУЛЕННЯ
            todaySteps = 0
            stepsThisHour = 0
            initialStepCount = 0
            currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            currentHourDate = today // НОВИЙ!!!

            sharedPrefs.edit()
                .putInt("today_steps", 0)
                .putString("last_active_date", today)
                .apply()

            // FIX: Видаляємо старі погодинні дані (старші 7 днів)
            deleteOldHourlyData()
        } else {
            todaySteps = sharedPrefs.getInt("today_steps", 0)
        }

        lastSavedDate = today

        // Відновлюємо стан сну якщо був
        isSleepTracking = sharedPrefs.getBoolean("is_sleep_tracking", false)
        sleepStartTime = sharedPrefs.getLong("sleep_start_time", 0L)
        if (isSleepTracking && sleepStartTime > 0) {
            lastMovementTime = System.currentTimeMillis()
            android.util.Log.d("SleepTracking", "Відновлено стан сну. Початок: ${Date(sleepStartTime)}")
        }
    }

    private fun scheduleMidnightReset() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, 1)
        }

        val intent = Intent(this, StepCounterService::class.java).apply {
            action = ACTION_NEW_DAY
        }

        val pendingIntent = PendingIntent.getService(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }

    private fun checkForNewDay() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        if (lastSavedDate != today) {
            handleNewDay()
        }
    }

    // !!!
    private fun handleNewDay() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        // Зберігаємо дані попереднього дня
        if (lastSavedDate.isNotEmpty() && todaySteps > 0) {
            savePreviousDaySteps(lastSavedDate)
            saveHourlyActivity(lastSavedDate, currentHour, stepsThisHour) // ВИПРАВЛЕНО: передаємо дату!!!
        }

        //  Очищаємо старі погодинні дані
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    database.hourlyActivityDao().deleteAllExceptToday(today)
                    android.util.Log.d("StepCounterService", "🗑️ видалено старі погодинні дані")
                } catch (e: Exception) {
                    android.util.Log.e("StepCounterService", "❌ помилка видалення старих даних", e)
                }
            }
        }

        // ОБНУЛЯЄМО ВСЕ
        if (stepCounterSensor != null) {
            initialStepCount = currentSteps
        }
        todaySteps = 0
        stepsThisHour = 0
        currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        currentHourDate = today // НОВИЙ!!!

        sharedPrefs.edit()
            .putInt("today_steps", 0)
            .putString("last_active_date", today)
            .apply()

        lastSavedDate = today
        updateNotification()

        scheduleMidnightReset()
    }

    // FIX: Метод для видалення старих погодинних даних
    private fun deleteOldHourlyData() {
        serviceScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val calendar = Calendar.getInstance()
                    calendar.add(Calendar.DAY_OF_MONTH, -7)
                    val sevenDaysAgo = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        .format(calendar.time)

                    database.hourlyActivityDao().deleteOldActivity(sevenDaysAgo)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Перевірка чи зараз нічний час для сну (22:00 - 08:00)
    private fun isNightTime(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= 23 || hour < 7 // 23:00 - 07:00
    }

    // Перевірка чи час прокидатися (після 08:00)
    private fun isWakeUpTime(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= 3 // після 03:00
    }
    private fun savePreviousDaySteps(date: String) {
        val steps = sharedPrefs.getInt("today_steps", 0)
        if (steps > 0) {
            serviceScope.launch {
                try {
                    val distance = calculateDistance(steps)
                    val calories = calculateCalories(steps)

                    val dailySteps = DailySteps(
                        date = date,
                        steps = steps,
                        distance = distance,
                        calories = calories
                    )

                    withContext(Dispatchers.IO) {
                        database.dailyStepsDao().insertOrUpdate(dailySteps)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Підрахунок кроків",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Відображає поточну кількість кроків"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, StepCounterService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val distance = calculateDistance(todaySteps)
        val calories = calculateCalories(todaySteps)

        // Додаємо статус сну в нотифікацію
        val contentText = if (isSleepTracking) {
            "💤 Сон • $todaySteps кроків • ${String.format("%.2f", distance)} км"
        } else {
            "$todaySteps кроків • ${String.format("%.2f", distance)} км • $calories ккал"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🚶‍♂️ Кроки сьогодні")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_mood_notification)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Зупинити",
                stopPendingIntent
            )
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    fun updateNotification() {
        val notification = createNotification()
        notificationManager.notify(NOTIFICATION_ID, notification)
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

    private fun initializeSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepDetectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    private fun startStepCounting() {
        android.util.Log.e("SleepTracking", "🔍 startStepCounting() викликано")
        android.util.Log.e("SleepTracking", "   stepCounterSensor: $stepCounterSensor")
        android.util.Log.e("SleepTracking", "   accelerometerSensor: $accelerometerSensor")

        // РЕЄСТРУЄМО STEP_COUNTER для підрахунку кроків
        when {
            stepCounterSensor != null -> {
                android.util.Log.e("SleepTracking", "✅ Реєструю STEP_COUNTER")
                sensorManager.registerListener(
                    this, stepCounterSensor, SensorManager.SENSOR_DELAY_NORMAL
                )
            }
            stepDetectorSensor != null -> {
                android.util.Log.e("SleepTracking", "✅ Реєструю STEP_DETECTOR")
                sensorManager.registerListener(
                    this, stepDetectorSensor, SensorManager.SENSOR_DELAY_NORMAL
                )
            }
        }

        //  ПРИМУСОВО реєструємо акселерометр для СНУ
        if (accelerometerSensor != null) {
            android.util.Log.e("SleepTracking", "✅ ДОДАТКОВО реєструю ACCELEROMETER для сну")
            sensorManager.registerListener(
                this, accelerometerSensor, SensorManager.SENSOR_DELAY_GAME
            )
        } else {
            android.util.Log.e("SleepTracking", "❌ НЕМАЄ АКСЕЛЕРОМЕТРА!")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            when (it.sensor.type) {
                Sensor.TYPE_STEP_COUNTER -> {
                    currentSteps = it.values[0].toInt()
                    if (initialStepCount == 0) {
                        initialStepCount = currentSteps - todaySteps
                    }
                    todaySteps = currentSteps - initialStepCount
                    stepsThisHour++

                    saveSteps()
                    checkAndSaveHourlyActivity()
                    updateNotification()
                }

                Sensor.TYPE_STEP_DETECTOR -> {
                    todaySteps++
                    stepsThisHour++

                    saveSteps()
                    checkAndSaveHourlyActivity()
                    updateNotification()
                }

                Sensor.TYPE_ACCELEROMETER -> {

                    handleAccelerometerStep(it)
                }
            }
        }
    }

    // ОНОВЛЕНА логіка для акселерометра з відстеженням сну
    private fun handleAccelerometerStep(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val currentTime = System.currentTimeMillis()



        // Фільтр 1: Мінімальна затримка між кроками
        if ((currentTime - lastStepTime) < stepDelayMs) {
            lastX = x
            lastY = y
            lastZ = z
            return
        }

        // Обчислюємо загальне прискорення
        val totalAcceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        val gravity = SensorManager.GRAVITY_EARTH
        val acceleration = abs(totalAcceleration - gravity)

        // Додаємо в буфер
        recentAccelerations.add(acceleration)
        if (recentAccelerations.size > accelerationBufferSize) {
            recentAccelerations.removeAt(0)
        }

        // Фільтр 2: Перевірка що це не поодинокий різкий рух
        val significantAccelerations = recentAccelerations.count { it > stepThreshold }
        val isStep = significantAccelerations >= 2

        // ===== ЛОГІКА ВІДСТЕЖЕННЯ СНУ =====
        // Відстеження рухів для сну
        if (acceleration > 2.0f) { // Будь-який значний рух
            lastMovementTime = currentTime
            movementCount++

            // 🧪 ДОДАЙ ЛОГ:
            android.util.Log.d("SleepTracking", "💫 Рух виявлено! acceleration=$acceleration")

            // Якщо спить - перевіряємо чи не час прокидатися
            if (isSleepTracking) {
                android.util.Log.d("SleepTracking", "😴 Спить. Перевіряю пробудження...")

                if (isWakeUpTime() && isStep) {
                    recentStepsCount++
                    android.util.Log.d("SleepTracking", "🚶 Крок! recentStepsCount=$recentStepsCount")

                    // Рахуємо кроки за останні 5 хвилин
                    if (recentStepsStartTime == 0L) {
                        recentStepsStartTime = currentTime
                    }

                    val fiveMinutesMs = 5 * 60 * 1000L
                    if (currentTime - recentStepsStartTime > fiveMinutesMs) {
                        recentStepsCount = 0
                        recentStepsStartTime = currentTime
                    }

                    // Якщо 3+ кроків за 5 хв - прокинувся!
                    if (recentStepsCount >= wakeUpStepsThreshold) {
                        android.util.Log.d("SleepTracking", "☀️☀️☀️ ПРОБУДЖЕННЯ! $recentStepsCount кроків")
                        endSleepTracking()
                    }
                }
            }
        }

        // Перевірка початку сну (будь-який час +  хв без рухів)
        if (!isSleepTracking && isNightTime()) {

            if (lastMovementTime > 0 && (currentTime - lastMovementTime) > noMovementThresholdMs) {
                android.util.Log.d("SleepTracking", "😴😴😴 ЗАСИНАННЯ! Без руху ${(currentTime - lastMovementTime) / 1000}s")
                startSleepTracking()
            }
        }

        // ===== КІНЕЦЬ БЛОКУ ЛОГІКИ СНУ =====

        if (isStep) {
            todaySteps++
            stepsThisHour++
            lastStepTime = currentTime

            saveSteps()
            checkAndSaveHourlyActivity()
            updateNotification()
        }

        lastX = x
        lastY = y
        lastZ = z
    }

    // ВИПРАВЛЕНИЙ метод!!!
    private fun checkAndSaveHourlyActivity() {
        val calendar = Calendar.getInstance()
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        // Якщо година змінилась
        if (hour != currentHour) {
            android.util.Log.d("HourlyActivity", "🕐 Година змінилась: $currentHour → $hour")

            // Зберігаємо дані за ПОПЕРЕДНЮ годину з ЇЇ датою
            if (stepsThisHour > 0 && currentHourDate.isNotEmpty()) {
                saveHourlyActivity(currentHourDate, currentHour, stepsThisHour) // ВИПРАВЛЕНО: передаємо дату!!!
                android.util.Log.d("HourlyActivity", "💾 Збережено: дата=$currentHourDate, година=$currentHour, кроки=$stepsThisHour")
            }

            // Оновлюємо поточну годину і ЗАПАМ'ЯТОВУЄМО її дату
            currentHour = hour
            currentHourDate = todayDate // НОВИЙ!!!
            stepsThisHour = 0

            android.util.Log.d("HourlyActivity", "🆕 Нова година: $currentHour на дату $currentHourDate")
        }

        // Збільшуємо лічильник кроків для поточної години
        stepsThisHour++
    }

    // ВИПРАВЛЕНИЙ метод!!!
    private fun saveHourlyActivity(date: String, hour: Int, steps: Int) {
        serviceScope.launch {
            try {
                val activity = HourlyActivity(
                    date = date, // ВИПРАВЛЕНО: використовуємо передану дату!!!
                    hour = hour,
                    steps = steps
                )

                android.util.Log.d("HourlyActivity", "📝 Зберігаю в БД: дата=$date, година=$hour, кроки=$steps")

                withContext(Dispatchers.IO) {
                    database.hourlyActivityDao().insertOrUpdate(activity)
                }

                android.util.Log.d("HourlyActivity", "✅ Успішно збережено")

            } catch (e: Exception) {
                android.util.Log.e("HourlyActivity", "❌ Помилка збереження", e)
                e.printStackTrace()
            }
        }
    }

    // Початок відстеження сну
    private fun startSleepTracking() {
        isSleepTracking = true
        sleepStartTime = System.currentTimeMillis()
        movementCount = 0

        android.util.Log.d("SleepTracking", "=== ЗАСИНАННЯ ===")
        android.util.Log.d("SleepTracking", "Час засинання: ${Date(sleepStartTime)}")

        // Зберігаємо в SharedPreferences на випадок перезапуску
        sharedPrefs.edit()
            .putBoolean("is_sleep_tracking", true)
            .putLong("sleep_start_time", sleepStartTime)
            .apply()

        updateNotification()
    }

    // Завершення відстеження сну
    private fun endSleepTracking() {
        if (!isSleepTracking) return
        android.util.Log.d("SleepTracking", "☀️☀️☀️ ПРОБУДЖЕННЯ! $recentStepsCount кроків")

        // Перевірка і відновлення sleepStartTime якщо він обнулився
        if (sleepStartTime == 0L) {
            android.util.Log.e("SleepTracking", "❌ sleepStartTime = 0! Відновлюю з SharedPrefs")
            sleepStartTime = sharedPrefs.getLong("sleep_start_time", 0L)

            if (sleepStartTime == 0L) {
                android.util.Log.e("SleepTracking", "❌ Не вдалося відновити! Скасовую.")
                isSleepTracking = false
                sharedPrefs.edit()
                    .putBoolean("is_sleep_tracking", false)
                    .putLong("sleep_start_time", 0L)
                    .apply()
                updateNotification()
                return
            }
            android.util.Log.d("SleepTracking", "✅ Відновлено sleepStartTime: ${Date(sleepStartTime)}")
        }

        val sleepEndTime = System.currentTimeMillis()
        val durationMs = sleepEndTime - sleepStartTime
        val durationMinutes = (durationMs / 60000).toInt()

        android.util.Log.d("SleepTracking", "=== ПРОБУДЖЕННЯ ===")
        android.util.Log.d("SleepTracking", "sleepStartTime: $sleepStartTime (${Date(sleepStartTime)})")
        android.util.Log.d("SleepTracking", "sleepEndTime: $sleepEndTime (${Date(sleepEndTime)})")
        android.util.Log.d("SleepTracking", "Тривалість сну: $durationMinutes хвилин")

        // ТИМЧАСОВО: 1 хвилина для тестування
        // Для продакшну поверни на 120!
        if (durationMinutes >= 120) { // 2 години
            android.util.Log.d("SleepTracking", "✅ Зберігаю запис сну (тривалість $durationMinutes хв)")


            // Зберігаємо timestamp в локальні змінні ПЕРЕД coroutine
            val startTime = sleepStartTime
            val endTime = sleepEndTime

            serviceScope.launch {
                try {
                    val sleepDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        .format(Date(startTime))

                    // Розраховуємо якість сну
                    val sleepQuality = calculateSleepQuality(
                        durationMinutes = durationMinutes,
                        movementCount = movementCount,
                        startTime = startTime
                    )

                    android.util.Log.d("SleepTracking", "   Якість сну: $sleepQuality%")
                    android.util.Log.d("SleepTracking", "   pухів за ніч: $movementCount")

                    val sleepRecord = SleepRecord(
                        date = sleepDate,
                        sleepStartTime = startTime,
                        sleepEndTime = endTime,
                        durationMinutes = durationMinutes,
                        quality = sleepQuality
                    )

                    val recordId = withContext(Dispatchers.IO) {
                        database.sleepDao().insert(sleepRecord)
                    }

                    android.util.Log.d("SleepTracking", "✅ Сон збережено! ID: $recordId")
                    android.util.Log.d("SleepTracking", "   Дата: $sleepDate")
                    android.util.Log.d("SleepTracking", "   Час: ${Date(startTime)} → ${Date(endTime)}")
                    android.util.Log.d("SleepTracking", "   Тривалість: $durationMinutes хв")

                    // Сповіщаємо MainActivity
                    val intent = Intent("com.example.healthmonitor.SLEEP_DATA_UPDATED")
                    sendBroadcast(intent)
                    android.util.Log.d("SleepTracking", "📢 Broadcast відправлено")

                    withContext(Dispatchers.IO) {
                        val calendar = Calendar.getInstance()
                        calendar.add(Calendar.DAY_OF_MONTH, -30)
                        val thirtyDaysAgo = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            .format(calendar.time)
                        database.sleepDao().deleteOldSleeps(thirtyDaysAgo)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SleepTracking", "❌ Помилка збереження сну!", e)
                    e.printStackTrace()
                }
            }
        } else {
            android.util.Log.d("SleepTracking", "⚠️ Сон занадто короткий ($durationMinutes хв), не зберігаю")
        }

        // Скидаємо стан сну (локальні копії вже збережені в корутині)
        isSleepTracking = false
        sleepStartTime = 0L
        recentStepsCount = 0
        recentStepsStartTime = 0L
        movementCount = 0

        sharedPrefs.edit()
            .putBoolean("is_sleep_tracking", false)
            .putLong("sleep_start_time", 0L)
            .apply()

        updateNotification()

        android.util.Log.d("SleepTracking", "=== СОН ЗАВЕРШЕНО ===")
    }

    // РОЗРАХУНОК ЯКОСТІ СНУ
    private fun calculateSleepQuality(
        durationMinutes: Int,
        movementCount: Int,
        startTime: Long
    ): Float {
        var quality = 100f

        // 1. Оцінка за тривалістю (0-40 балів)
        val durationHours = durationMinutes / 60f
        quality -= when {
            durationHours in 7f..9f -> 0f          // Ідеально!
            durationHours in 6f..7f || durationHours in 9f..10f -> 10f  // Добре
            durationHours in 5f..6f || durationHours in 10f..11f -> 20f // Прийнятно
            else -> 40f                            // Мало/багато
        }

        // 2. Оцінка за рухи (0-30 балів)
        // Норма: 10-20 рухів за ніч
        val movementsPerHour = movementCount / durationHours
        quality -= when {
            movementsPerHour <= 3f -> 0f           // Дуже спокійний сон
            movementsPerHour <= 5f -> 10f          // Спокійний
            movementsPerHour <= 8f -> 20f          // Середні рухи
            else -> 30f                            // Багато рухів
        }

        // 3. Оцінка за час засинання (0-30 балів)
        val cal = Calendar.getInstance().apply { timeInMillis = startTime }
        val hourOfDay = cal.get(Calendar.HOUR_OF_DAY)
        quality -= when (hourOfDay) {
            21, 22, 23 -> 0f                       // Ідеальний час
            20, 0 -> 10f                           // Добрий час
            19, 1, 2 -> 20f                        // Прийнятно
            else -> 30f                            // Пізно/рано
        }

        // Обмежуємо 60-100%
        return quality.coerceIn(60f, 100f)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun saveSteps() {
        sharedPrefs.edit()
            .putInt("today_steps", todaySteps)
            .apply()
    }

    private fun saveTodayStepsToDatabase() {
        if (todaySteps > 0) {
            saveHourlyActivity(currentHourDate, currentHour, stepsThisHour) // ВИПРАВЛЕНО: передаємо дату!!!

            serviceScope.launch {
                try {
                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    val distance = calculateDistance(todaySteps)
                    val calories = calculateCalories(todaySteps)

                    val dailySteps = DailySteps(
                        date = today,
                        steps = todaySteps,
                        distance = distance,
                        calories = calories
                    )

                    withContext(Dispatchers.IO) {
                        database.dailyStepsDao().insertOrUpdate(dailySteps)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun getTodaySteps(): Int = todaySteps

    // Отримати статус сну
    fun isSleeping(): Boolean = isSleepTracking

    fun resetTodaySteps() {
        if (stepCounterSensor != null) {
            initialStepCount = currentSteps
        }
        todaySteps = 0
        stepsThisHour = 0
        recentAccelerations.clear()
        saveSteps()
        updateNotification()
    }

    override fun onDestroy() {
        super.onDestroy()

        // Зберігаємо дані поточної години перед закриттям
        if (stepsThisHour > 0 && currentHourDate.isNotEmpty()) {
            saveHourlyActivity(currentHourDate, currentHour, stepsThisHour) // ВИПРАВЛЕНО: передаємо дату!!!
            android.util.Log.d("HourlyActivity", "💾 Збережено при закритті: дата=$currentHourDate, година=$currentHour, кроки=$stepsThisHour")
        }

        dateCheckHandler.removeCallbacks(dateCheckRunnable)
        sensorManager.unregisterListener(this)
        saveTodayStepsToDatabase()
        saveSteps()
        serviceJob.cancel()

        try {
            wakeLock?.release()
            android.util.Log.d("StepCounterService", "✅ WakeLock released")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}