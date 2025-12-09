package com.example.healthmonitor

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.*

/**
 * Клас для щоденних mood reminders о 12:00 та 20:00
 */
class MoodReminders {

    companion object {
        private const val CHANNEL_ID = "mood_reminder_channel"
        private const val NOON_HOUR = 12 // змінити потім на 12
        private const val NOON_MINUTE = 12  // додай цю константу
        private const val EVENING_HOUR = 20
        private const val REQUEST_CODE_NOON = 2001
        private const val REQUEST_CODE_EVENING = 2002

        /**
         * Запускає обидва щоденних нагадування
         */
        fun scheduleDailyReminders(context: Context) {
            createNotificationChannel(context)
            scheduleReminder(context, NOON_HOUR, NOON_MINUTE, REQUEST_CODE_NOON, isNoon = true)  // змінив тут
            scheduleReminder(context, EVENING_HOUR, 0, REQUEST_CODE_EVENING, isNoon = false)
        }

        /**
         * Скасовує всі mood reminders
         */
        fun cancelReminders(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val noonIntent = createReminderIntent(context, REQUEST_CODE_NOON, true)
            val eveningIntent = createReminderIntent(context, REQUEST_CODE_EVENING, false)

            alarmManager.cancel(noonIntent)
            alarmManager.cancel(eveningIntent)
        }

        private fun scheduleReminder(
            context: Context,
            hour: Int,
            minute: Int,
            requestCode: Int,
            isNoon: Boolean
        ) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)

                // Якщо час вже минув сьогодні, плануємо на завтра
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_MONTH, 1)
                }
            }

            val pendingIntent = createReminderIntent(context, requestCode, isNoon)

            // Використовуємо setRepeating для щоденного повторення
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY, // Повторювати кожен день
                pendingIntent
            )
        }

        private fun createReminderIntent(
            context: Context,
            requestCode: Int,
            isNoon: Boolean
        ): PendingIntent {
            val intent = Intent(context, MoodReminderReceiver::class.java).apply {
                putExtra("is_noon", isNoon)
            }

            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Нагадування про настрій",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Нагадування про відстеження настрою двічі на день"
                    enableLights(true)
                    enableVibration(true)
                }

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }

        fun showMoodNotification(context: Context, isNoon: Boolean) {
            val title = if (isNoon) "☀️ Денний чек-ін" else "🌙 Вечірній чек-ін"
            val message = "Як ви себе почуваєте? Відзначте свій настрій"

            // Intent для відкриття MoodActivity
            val intent = Intent(context, MoodActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                if (isNoon) REQUEST_CODE_NOON else REQUEST_CODE_EVENING,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_mood_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(
                if (isNoon) 1001 else 1002,
                notification
            )
        }
    }
}

/**
 * BroadcastReceiver для обробки alarm-сповіщень
 */
class MoodReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val isNoon = intent.getBooleanExtra("is_noon", true)
        MoodReminders.showMoodNotification(context, isNoon)
    }
}