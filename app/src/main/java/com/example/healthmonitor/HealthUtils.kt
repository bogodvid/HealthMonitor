package com.example.healthmonitor

import java.text.SimpleDateFormat
import java.util.*

object HealthUtils {

    fun getPulseStatus(bpm: Int): String {
        return when {
            bpm < 60 -> "Низький"
            bpm in 60..100 -> "Нормальний"
            bpm in 101..120 -> "Підвищений"
            else -> "Високий"
        }
    }

    fun getPulseStatusColor(bpm: Int): Int {
        return when {
            bpm < 60 -> android.R.color.holo_blue_light
            bpm in 60..100 -> android.R.color.holo_green_light
            bpm in 101..120 -> android.R.color.holo_orange_light
            else -> android.R.color.holo_red_light
        }
    }

    fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun getGreetingByTime(): String {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        return when (hour) {
            in 6..11 -> "Доброго ранку"
            in 12..17 -> "Добрий день"
            in 18..22 -> "Добрий вечір"
            else -> "Доброї ночі"
        }
    }

    fun calculateCalories(steps: Int): Int {
        // Приблизний розрахунок: 1 крок ≈ 0.04 калорії
        return (steps * 0.04).toInt()
    }

    fun calculateDistance(steps: Int): Float {
        // Приблизний розрахунок: 1 крок ≈ 0.75 м
        return (steps * 0.75f) / 1000 // в км
    }
}