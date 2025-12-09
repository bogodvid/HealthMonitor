package com.example.healthmonitor

import androidx.room.*
import java.text.SimpleDateFormat
import java.util.*
import android.content.Context
import androidx.room.Room

// 1. Entity клас для записів пульсу
@Entity(tableName = "pulse_records")
data class PulseRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bpm: Int,
    val timestamp: Long
) {
    fun getFormattedDate(): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

// 2. Entity клас для щоденних кроків
@Entity(tableName = "daily_steps")
data class DailySteps(
    @PrimaryKey
    val date: String, // формат YYYY-MM-DD
    val steps: Int,
    val distance: Float = 0f, // в кілометрах
    val calories: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun getFormattedDate(): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            val date = inputFormat.parse(date)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            date
        }
    }

    fun getFormattedDateShort(): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
            val date = inputFormat.parse(date)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            date
        }
    }
}

// 3. Entity клас для погодинної активності
@Entity(tableName = "hourly_activity")
data class HourlyActivity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String, // формат YYYY-MM-DD
    val hour: Int, // 0-23
    val steps: Int,
    val timestamp: Long = System.currentTimeMillis()
)

// 4. Entity для записів сну
@Entity(tableName = "sleep_records")
data class SleepRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String, // дата засинання (формат YYYY-MM-DD)
    val sleepStartTime: Long, // timestamp коли заснув
    val sleepEndTime: Long, // timestamp коли прокинувся
    val durationMinutes: Int, // тривалість сну в хвилинах
    val quality: Float = 0f // якість сну 0-100%
) {
    fun getFormattedStartTime(): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(sleepStartTime))
    }

    fun getFormattedEndTime(): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(sleepEndTime))
    }

    fun getFormattedDuration(): String {
        val hours = durationMinutes / 60
        val minutes = durationMinutes % 60
        return "${hours}г ${minutes}хв"
    }

    fun getFormattedDate(): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            val date = inputFormat.parse(date)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            date
        }
    }
}

// 5. НОВИЙ Entity для настрою
@Entity(tableName = "mood_records")
data class MoodRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String, // формат YYYY-MM-DD
    val time: String, // формат HH:mm
    val moodLevel: Int, // 1-5 (1=дуже погано, 5=чудово)
    val intensity: Int = 5, // НОВИЙ: 1-10 інтенсивність
    val note: String = "", // опціональна нотатка
    val tags: String = "", // НОВИЙ: JSON список тегів ["stress","tired"]
    val factors: String = "", // НОВИЙ: JSON список факторів ["work","coffee"]
    val timestamp: Long = System.currentTimeMillis()
) {
    fun getFormattedDateTime(): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.getDefault())
            val dateTime = inputFormat.parse("$date $time")
            outputFormat.format(dateTime ?: Date())
        } catch (e: Exception) {
            "$date $time"
        }
    }

    fun getFormattedDate(): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            val parsedDate = inputFormat.parse(date)
            outputFormat.format(parsedDate ?: Date())
        } catch (e: Exception) {
            date
        }
    }

    fun getMoodEmoji(): String {
        return when (moodLevel) {
            1 -> "😢"
            2 -> "😕"
            3 -> "😐"
            4 -> "🙂"
            5 -> "😄"
            else -> "😐"
        }
    }

    fun getMoodText(): String {
        return when (moodLevel) {
            1 -> "Дуже погано"
            2 -> "Погано"
            3 -> "Нормально"
            4 -> "Добре"
            5 -> "Чудово"
            else -> "Невідомо"
        }
    }

    // НОВИЙ: парсинг тегів
    fun getTagsList(): List<String> {
        return if (tags.isEmpty()) emptyList()
        else tags.removeSurrounding("[", "]")
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotEmpty() }
    }

    // НОВИЙ: парсинг факторів
    fun getFactorsList(): List<String> {
        return if (factors.isEmpty()) emptyList()
        else factors.removeSurrounding("[", "]")
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotEmpty() }
    }
}


// 6. DAO для записів пульсу
@Dao
interface PulseDao {
    @Query("SELECT * FROM pulse_records ORDER BY timestamp DESC")
    suspend fun getAllPulseRecords(): List<PulseRecord>

    @Insert
    suspend fun insert(record: PulseRecord)

    @Insert
    suspend fun insertAll(records: List<PulseRecord>)

    @Query("DELETE FROM pulse_records")
    suspend fun deleteAll()

    @Query("SELECT * FROM pulse_records ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestPulse(): PulseRecord?

    @Query("SELECT * FROM pulse_records WHERE timestamp >= :startTime ORDER BY timestamp DESC")
    suspend fun getPulseRecordsAfter(startTime: Long): List<PulseRecord>
}

// 7. DAO для щоденних кроків
@Dao
interface DailyStepsDao {
    @Query("SELECT * FROM daily_steps ORDER BY date DESC LIMIT 30")
    suspend fun getLast30Days(): List<DailySteps>

    @Query("SELECT * FROM daily_steps WHERE date = :date")
    suspend fun getStepsForDate(date: String): DailySteps?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(dailySteps: DailySteps)

    @Query("SELECT SUM(steps) FROM daily_steps WHERE date >= :startDate")
    suspend fun getTotalStepsSince(startDate: String): Int?

    @Query("SELECT AVG(steps) FROM daily_steps WHERE date >= :startDate")
    suspend fun getAverageStepsSince(startDate: String): Float?

    @Query("DELETE FROM daily_steps")
    suspend fun deleteAll()

    @Query("SELECT * FROM daily_steps ORDER BY date DESC LIMIT :limit")
    suspend fun getLastNDays(limit: Int): List<DailySteps>

    @Query("SELECT * FROM daily_steps WHERE date >= :sinceDate ORDER BY date ASC")
    suspend fun getStepsSince(sinceDate: String): List<DailySteps>
}


// 8. DAO для погодинної активності
@Dao
interface HourlyActivityDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(activity: HourlyActivity)

    @Query("SELECT * FROM hourly_activity WHERE date = :date ORDER BY hour ASC")
    suspend fun getActivityForDate(date: String): List<HourlyActivity>

    @Query("SELECT * FROM hourly_activity ORDER BY date DESC, hour DESC")
    suspend fun getAllActivity(): List<HourlyActivity>

    @Query("DELETE FROM hourly_activity WHERE date < :date")
    suspend fun deleteOldActivity(date: String)

    @Query("DELETE FROM hourly_activity WHERE date != :today")
    suspend fun deleteAllExceptToday(today: String)

    @Query("SELECT COUNT(*) FROM hourly_activity")
    suspend fun getRecordCount(): Int

    @Query("SELECT DISTINCT date FROM hourly_activity ORDER BY date DESC")
    suspend fun getAllDates(): List<String>
}

// 9. DAO для записів сну
@Dao
interface SleepDao {
    @Query("SELECT * FROM sleep_records ORDER BY sleepStartTime DESC LIMIT 1")
    suspend fun getLatestSleep(): SleepRecord?

    @Query("SELECT * FROM sleep_records ORDER BY sleepStartTime DESC LIMIT 30")
    suspend fun getLast30Sleeps(): List<SleepRecord>

    @Query("SELECT * FROM sleep_records WHERE date = :date")
    suspend fun getSleepForDate(date: String): SleepRecord?

    @Insert
    suspend fun insert(record: SleepRecord): Long

    @Query("DELETE FROM sleep_records WHERE date < :oldestDate")
    suspend fun deleteOldSleeps(oldestDate: String)

    @Query("SELECT AVG(durationMinutes) FROM sleep_records WHERE date >= :startDate")
    suspend fun getAverageSleepDuration(startDate: String): Float?

    @Query("DELETE FROM sleep_records")
    suspend fun deleteAllSleeps()

    @Query("SELECT * FROM sleep_records WHERE date >= :sinceDate ORDER BY date ASC")
    suspend fun getSleepRecordsSince(sinceDate: String): List<SleepRecord>
}


// 10. НОВИЙ DAO для настрою
@Dao
interface MoodDao {
    @Query("SELECT * FROM mood_records ORDER BY timestamp DESC")
    suspend fun getAllMoods(): List<MoodRecord>

    @Query("SELECT * FROM mood_records ORDER BY timestamp DESC LIMIT 100")
    suspend fun getLast100Moods(): List<MoodRecord>  // ЗМІНЕНО з 30 на 100!

    @Query("SELECT * FROM mood_records WHERE date = :date ORDER BY timestamp DESC")
    suspend fun getMoodsForDate(date: String): List<MoodRecord>

    @Insert
    suspend fun insert(mood: MoodRecord): Long

    @Query("DELETE FROM mood_records WHERE date < :oldestDate")
    suspend fun deleteOldMoods(oldestDate: String)

    @Query("SELECT AVG(moodLevel) FROM mood_records WHERE date >= :startDate")
    suspend fun getAverageMood(startDate: String): Float?

    @Query("SELECT * FROM mood_records ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestMood(): MoodRecord?

    @Query("DELETE FROM mood_records")
    suspend fun deleteAllMoods()

    @Query("SELECT * FROM mood_records WHERE date >= :sinceDate ORDER BY date ASC, time ASC")
    suspend fun getMoodsSince(sinceDate: String): List<MoodRecord>
}

@Database(
    entities = [
        PulseRecord::class,
        DailySteps::class,
        HourlyActivity::class,
        SleepRecord::class,
        MoodRecord::class
    ],
    version = 5, // ЗМІНЕНО з 4 на 5!
    exportSchema = false
)
abstract class HealthDatabase : RoomDatabase() {
    abstract fun pulseDao(): PulseDao
    abstract fun dailyStepsDao(): DailyStepsDao
    abstract fun hourlyActivityDao(): HourlyActivityDao
    abstract fun sleepDao(): SleepDao
    abstract fun moodDao(): MoodDao

    companion object {
        @Volatile
        private var INSTANCE: HealthDatabase? = null

        fun getInstance(context: Context): HealthDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HealthDatabase::class.java,
                    "health_database"
                )
                    .fallbackToDestructiveMigration() // Видалить старі дані mood при оновленні
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// 12. ОНОВЛЕНИЙ Клас для бекапу даних
data class BackupData(
    val userName: String? = null,
    val userHeight: Float? = null,
    val userWeight: Float? = null,
    val pulseRecords: List<PulseRecord>? = null,
    val dailySteps: List<DailySteps>? = null,
    val sleepRecords: List<SleepRecord>? = null,  // НОВИЙ!
    val moodRecords: List<MoodRecord>? = null     // НОВИЙ!
)