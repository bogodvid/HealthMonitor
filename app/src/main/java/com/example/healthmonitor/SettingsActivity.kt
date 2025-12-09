package com.example.healthmonitor

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class SettingsActivity : AppCompatActivity() {

    private lateinit var etUserName: EditText
    private lateinit var etUserHeight: EditText
    private lateinit var etUserWeight: EditText
    private lateinit var etStepsGoal: EditText
    private lateinit var btnSaveProfile: Button
    private lateinit var btnDeleteAllData: Button  // НОВИЙ!
    private lateinit var btnExportData: Button
    private lateinit var btnImportData: Button

    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var database: HealthDatabase

    private val gson = Gson()

    companion object {
        private const val STORAGE_PERMISSION_REQUEST = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Налаштування"

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        sharedPrefs = getSharedPreferences("HealthMonitor", Context.MODE_PRIVATE)

        database = Room.databaseBuilder(
            applicationContext,
            HealthDatabase::class.java,
            "health_database"
        )
            .fallbackToDestructiveMigration()
            .build()

        initViews()
        loadCurrentSettings()
        setupListeners()
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
        etUserName = findViewById(R.id.etUserName)
        etUserHeight = findViewById(R.id.etUserHeight)
        etUserWeight = findViewById(R.id.etUserWeight)
        etStepsGoal = findViewById(R.id.etStepsGoal)
        btnSaveProfile = findViewById(R.id.btnSaveProfile)
        btnDeleteAllData = findViewById(R.id.btnDeleteAllData)  // НОВИЙ!
        btnExportData = findViewById(R.id.btnExportData)
        btnImportData = findViewById(R.id.btnImportData)
    }

    private fun loadCurrentSettings() {
        val userName = sharedPrefs.getString("user_name", "")
        val userHeight = sharedPrefs.getFloat("user_height", 0f)
        val userWeight = sharedPrefs.getFloat("user_weight", 0f)
        val stepsGoal = sharedPrefs.getInt("steps_goal", 10000)

        etUserName.setText(userName)
        if (userHeight > 0) etUserHeight.setText(userHeight.toInt().toString())
        if (userWeight > 0) etUserWeight.setText(userWeight.toInt().toString())
        etStepsGoal.setText(stepsGoal.toString())
    }

    private fun setupListeners() {
        btnSaveProfile.setOnClickListener {
            saveProfile()
        }

        // НОВИЙ!
        btnDeleteAllData.setOnClickListener {
            showDeleteConfirmation()
        }

        btnExportData.setOnClickListener {
            if (checkStoragePermission()) {
                exportData()
            } else {
                requestStoragePermission()
            }
        }

        btnImportData.setOnClickListener {
            if (checkStoragePermission()) {
                showImportDialog()
            } else {
                requestStoragePermission()
            }
        }
    }

    private fun saveProfile() {
        val name = etUserName.text.toString().trim()
        val heightStr = etUserHeight.text.toString().trim()
        val weightStr = etUserWeight.text.toString().trim()
        val goalStr = etStepsGoal.text.toString().trim()

        // Валідація імені
        if (name.isEmpty()) {
            etUserName.error = "Введіть ваше ім'я"
            return
        }

        if (name.length < 2) {
            etUserName.error = "Ім'я занадто коротке"
            return
        }

        // Валідація зросту
        if (heightStr.isEmpty() || heightStr.toFloatOrNull() == null ||
            heightStr.toFloat() < 100 || heightStr.toFloat() > 250) {
            etUserHeight.error = "Введіть зріст (100-250 см)"
            return
        }

        // Валідація ваги
        if (weightStr.isEmpty() || weightStr.toFloatOrNull() == null ||
            weightStr.toFloat() < 30 || weightStr.toFloat() > 300) {
            etUserWeight.error = "Введіть вагу (30-300 кг)"
            return
        }

        // Валідація цілі
        if (goalStr.isEmpty() || goalStr.toIntOrNull() == null ||
            goalStr.toInt() < 1000 || goalStr.toInt() > 50000) {
            etStepsGoal.error = "Введіть ціль (1000-50000 кроків)"
            return
        }

        // Зберігаємо
        sharedPrefs.edit()
            .putString("user_name", name)
            .putFloat("user_height", heightStr.toFloat())
            .putFloat("user_weight", weightStr.toFloat())
            .putInt("steps_goal", goalStr.toInt())
            .apply()

        Toast.makeText(this, "✅ Профіль збережено", Toast.LENGTH_SHORT).show()

        // Оновлюємо MainActivity якщо вона запущена
        setResult(RESULT_OK)
    }

    // НОВИЙ МЕТОД - видалення всіх даних
    private fun showDeleteConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Видалити всі дані?")
            .setMessage(
                "Це призведе до видалення:\n\n" +
                        "• Всіх записів пульсу\n" +
                        "• Всіх записів кроків\n" +
                        "• Всіх записів сну\n" +
                        "• Всіх записів настрою\n\n" +
                        "ЦЮ ДІЮ НЕ МОЖНА ВІДМІНИТИ!\n\n" +
                        "Профіль (ім'я, зріст, вага) залишиться."
            )
            .setPositiveButton("Видалити") { _, _ ->
                deleteAllData()
            }
            .setNegativeButton("Скасувати", null)
            .show()
    }

    // НОВИЙ МЕТОД - виконання видалення
    private fun deleteAllData() {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    database.pulseDao().deleteAll()
                    database.dailyStepsDao().deleteAll()
                    database.sleepDao().deleteAllSleeps()
                    database.moodDao().deleteAllMoods()
                }

                Toast.makeText(
                    this@SettingsActivity,
                    "🗑️ Всі дані видалено!",
                    Toast.LENGTH_LONG
                ).show()

                // Пропонуємо перезапуск
                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("Дані видалено")
                    .setMessage("Рекомендується перезапустити додаток")
                    .setPositiveButton("Перезапустити") { _, _ ->
                        restartApp()
                    }
                    .setNegativeButton("Пізніше", null)
                    .show()

            } catch (e: Exception) {
                Toast.makeText(
                    this@SettingsActivity,
                    "❌ Помилка: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            true
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                STORAGE_PERMISSION_REQUEST
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Дозвіл надано", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Дозвіл відхилено", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ВИПРАВЛЕНО - додано sleepRecords і moodRecords!
    private fun exportData() {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@SettingsActivity, "Експорт даних...", Toast.LENGTH_SHORT).show()

                val backupData = withContext(Dispatchers.IO) {
                    BackupData(
                        userName = sharedPrefs.getString("user_name", null),
                        userHeight = sharedPrefs.getFloat("user_height", 0f),
                        userWeight = sharedPrefs.getFloat("user_weight", 0f),
                        pulseRecords = database.pulseDao().getAllPulseRecords(),
                        dailySteps = database.dailyStepsDao().getLast30Days(),
                        sleepRecords = database.sleepDao().getLast30Sleeps(),  // ДОДАНО!
                        moodRecords = database.moodDao().getLast100Moods()      // ДОДАНО!
                    )
                }

                val json = gson.toJson(backupData)

                val fileName = "HealthMonitor_Backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
                val file = saveToFile(fileName, json)

                if (file != null) {
                    Toast.makeText(
                        this@SettingsActivity,
                        "✅ Дані експортовано: ${file.name}",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(this@SettingsActivity, "❌ Помилка експорту", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Toast.makeText(
                    this@SettingsActivity,
                    "❌ Помилка: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun saveToFile(fileName: String, content: String): File? {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)

            FileWriter(file).use { writer ->
                writer.write(content)
            }

            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun showImportDialog() {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val files = downloadsDir.listFiles { file ->
            file.name.startsWith("HealthMonitor_Backup_") && file.name.endsWith(".json")
        }

        if (files.isNullOrEmpty()) {
            Toast.makeText(this, "Файли бекапу не знайдено в Downloads", Toast.LENGTH_LONG).show()
            return
        }

        val fileNames = files.map { it.name }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Виберіть файл для імпорту")
            .setItems(fileNames) { _, which ->
                importData(files[which])
            }
            .setNegativeButton("Скасувати", null)
            .show()
    }

    private fun importData(file: File) {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@SettingsActivity, "Імпорт даних...", Toast.LENGTH_SHORT).show()

                val json = withContext(Dispatchers.IO) {
                    FileReader(file).use { it.readText() }
                }

                val backupData = gson.fromJson(json, BackupData::class.java)

                // ОНОВЛЕНО - показуємо всі дані
                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("Імпорт даних")
                    .setMessage(
                        "Знайдено:\n" +
                                "• Ім'я: ${backupData.userName ?: "відсутнє"}\n" +
                                "• Записів пульсу: ${backupData.pulseRecords?.size ?: 0}\n" +
                                "• Записів кроків: ${backupData.dailySteps?.size ?: 0}\n" +
                                "• Записів сну: ${backupData.sleepRecords?.size ?: 0}\n" +
                                "• Записів настрою: ${backupData.moodRecords?.size ?: 0}\n\n" +
                                "Імпортувати ці дані?\n" +
                                "⚠️ УВАГА: Поточні дані будуть ВИДАЛЕНІ!"
                    )
                    .setPositiveButton("Імпортувати") { _, _ ->
                        performImport(backupData)
                    }
                    .setNegativeButton("Скасувати", null)
                    .show()

            } catch (e: Exception) {
                Toast.makeText(
                    this@SettingsActivity,
                    "❌ Помилка читання файлу: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ВИПРАВЛЕНО - додано sleepRecords і moodRecords, ВИДАЛЯЄМО старі дані перед імпортом!
    private fun performImport(backupData: BackupData) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Зберігаємо профіль
                    val editor = sharedPrefs.edit()
                    backupData.userName?.let { editor.putString("user_name", it) }
                    backupData.userHeight?.let { if (it > 0) editor.putFloat("user_height", it) }
                    backupData.userWeight?.let { if (it > 0) editor.putFloat("user_weight", it) }
                    editor.apply()

                    // ВИДАЛЯЄМО СТАРІ ДАНІ перед імпортом!
                    database.pulseDao().deleteAll()
                    database.dailyStepsDao().deleteAll()
                    database.sleepDao().deleteAllSleeps()
                    database.moodDao().deleteAllMoods()

                    // Імпортуємо НОВІ дані
                    backupData.pulseRecords?.let { records ->
                        if (records.isNotEmpty()) {
                            database.pulseDao().insertAll(records)
                        }
                    }

                    backupData.dailySteps?.let { steps ->
                        steps.forEach { dailyStep ->
                            database.dailyStepsDao().insertOrUpdate(dailyStep)
                        }
                    }

                    // ДОДАНО!
                    backupData.sleepRecords?.let { records ->
                        records.forEach { sleepRecord ->
                            database.sleepDao().insert(sleepRecord)
                        }
                    }

                    // ДОДАНО!
                    backupData.moodRecords?.let { records ->
                        records.forEach { moodRecord ->
                            database.moodDao().insert(moodRecord)
                        }
                    }
                }

                loadCurrentSettings()

                Toast.makeText(
                    this@SettingsActivity,
                    "✅ Дані успішно імпортовано!",
                    Toast.LENGTH_LONG
                ).show()

                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("Імпорт завершено")
                    .setMessage("Рекомендується перезапустити додаток для коректного відображення даних")
                    .setPositiveButton("Перезапустити") { _, _ ->
                        restartApp()
                    }
                    .setNegativeButton("Пізніше", null)
                    .show()

            } catch (e: Exception) {
                Toast.makeText(
                    this@SettingsActivity,
                    "❌ Помилка імпорту: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun restartApp() {
        val intent = Intent(this, SplashActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }
}