package com.example.healthmonitor

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class SplashActivity : AppCompatActivity() {

    private lateinit var sharedPrefs: SharedPreferences

    // Крок 1 - Дані користувача
    private lateinit var layoutStep1: LinearLayout
    private lateinit var tvWelcome: TextView
    private lateinit var etUserName: EditText
    private lateinit var etUserHeight: EditText
    private lateinit var etUserWeight: EditText
    private lateinit var btnNext: Button

    // Крок 2 - Дозволи
    private lateinit var layoutStep2: LinearLayout
    private lateinit var btnGrantPermissions: Button
    private lateinit var btnSkip: Button

    private var currentStep = 1

    // Launcher для запиту дозволів
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Toast.makeText(this, "✅ Дозволи надано!", Toast.LENGTH_SHORT).show()
            checkBatteryOptimization()
        } else {
            Toast.makeText(this, "⚠️ Деякі дозволи не надано", Toast.LENGTH_LONG).show()
            showPermissionsExplanation()
        }
    }

    // Launcher для налаштувань батареї
    private val batteryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        startMainActivity()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPrefs = getSharedPreferences("HealthMonitor", Context.MODE_PRIVATE)

        // Перевіряємо чи користувач вже зареєстрований
        val userName = sharedPrefs.getString("user_name", null)
        if (!userName.isNullOrEmpty()) {
            startMainActivity()
            return
        }

        // Відображаємо екран реєстрації
        setContentView(R.layout.activity_splash)

        initViews()
        setupListeners()
        showStep1()
    }

    private fun initViews() {
        // Крок 1
        layoutStep1 = findViewById(R.id.layoutStep1)
        tvWelcome = findViewById(R.id.tvWelcome)
        etUserName = findViewById(R.id.etUserName)
        etUserHeight = findViewById(R.id.etUserHeight)
        etUserWeight = findViewById(R.id.etUserWeight)
        btnNext = findViewById(R.id.btnNext)

        // Крок 2
        layoutStep2 = findViewById(R.id.layoutStep2)
        btnGrantPermissions = findViewById(R.id.btnGrantPermissions)
        btnSkip = findViewById(R.id.btnSkip)
    }

    private fun setupListeners() {
        btnNext.setOnClickListener {
            if (validateAndSaveUserData()) {
                showStep2()
            }
        }

        btnGrantPermissions.setOnClickListener {
            requestAllPermissions()
        }

        btnSkip.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("⚠️ Пропустити дозволи?")
                .setMessage(
                    "Без дозволів деякі функції не працюватимуть:\n\n" +
                            "• Камера - вимірювання пульсу\n" +
                            "• Крокомір - підрахунок кроків\n" +
                            "• Сповіщення - нагадування\n\n" +
                            "Ви зможете надати дозволи пізніше в налаштуваннях."
                )
                .setPositiveButton("Все одно пропустити") { _, _ ->
                    startMainActivity()
                }
                .setNegativeButton("Повернутись", null)
                .show()
        }
    }

    private fun showStep1() {
        currentStep = 1
        layoutStep1.visibility = LinearLayout.VISIBLE
        layoutStep2.visibility = LinearLayout.GONE
    }

    private fun showStep2() {
        currentStep = 2
        layoutStep1.visibility = LinearLayout.GONE
        layoutStep2.visibility = LinearLayout.VISIBLE
    }

    private fun validateAndSaveUserData(): Boolean {
        val name = etUserName.text.toString().trim()
        val heightStr = etUserHeight.text.toString().trim()
        val weightStr = etUserWeight.text.toString().trim()

        // Валідація
        if (name.isEmpty()) {
            etUserName.error = "Введіть ваше ім'я"
            return false
        }

        if (name.length < 2) {
            etUserName.error = "Ім'я занадто коротке"
            return false
        }

        if (heightStr.isEmpty() || heightStr.toFloatOrNull() == null ||
            heightStr.toFloat() < 100 || heightStr.toFloat() > 250) {
            etUserHeight.error = "Введіть зріст (100-250 см)"
            return false
        }

        if (weightStr.isEmpty() || weightStr.toFloatOrNull() == null ||
            weightStr.toFloat() < 30 || weightStr.toFloat() > 300) {
            etUserWeight.error = "Введіть вагу (30-300 кг)"
            return false
        }

        // Зберігаємо дані
        sharedPrefs.edit()
            .putString("user_name", name)
            .putFloat("user_height", heightStr.toFloat())
            .putFloat("user_weight", weightStr.toFloat())
            .putBoolean("first_launch", false)
            .apply()

        return true
    }

    private fun requestAllPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Камера (для пульсу)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }

        // Активність (для кроків)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }

        // Сповіщення (для нагадувань)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            // Всі дозволи вже надано
            checkBatteryOptimization()
        }
    }

    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val packageName = packageName

            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                AlertDialog.Builder(this)
                    .setTitle("🔋 Оптимізація батареї")
                    .setMessage(
                        "Для точного підрахунку кроків у фоновому режимі рекомендується " +
                                "вимкнути оптимізацію батареї для цього додатку.\n\n" +
                                "Це не вплине на тривалість роботи батареї."
                    )
                    .setPositiveButton("Налаштувати") { _, _ ->
                        openBatterySettings()
                    }
                    .setNegativeButton("Пропустити") { _, _ ->
                        startMainActivity()
                    }
                    .show()
            } else {
                startMainActivity()
            }
        } else {
            startMainActivity()
        }
    }

    private fun openBatterySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                batteryLauncher.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Не вдалося відкрити налаштування", Toast.LENGTH_SHORT).show()
                startMainActivity()
            }
        }
    }

    private fun showPermissionsExplanation() {
        AlertDialog.Builder(this)
            .setTitle("ℹ️ Чому потрібні дозволи?")
            .setMessage(
                "📷 Камера - для вимірювання пульсу через камеру\n\n" +
                        "👣 Активність - для підрахунку кроків\n\n" +
                        "🔔 Сповіщення - для нагадувань про здоров'я\n\n" +
                        "Ви можете надати дозволи пізніше в налаштуваннях додатку або системних налаштуваннях."
            )
            .setPositiveButton("Спробувати ще раз") { _, _ ->
                requestAllPermissions()
            }
            .setNegativeButton("Продовжити без дозволів") { _, _ ->
                startMainActivity()
            }
            .show()
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {
        if (currentStep == 2) {
            // Повертаємось на крок 1
            showStep1()
        } else {
            // На кроці 1 - виходимо з додатку
            super.onBackPressed()
        }
    }
}