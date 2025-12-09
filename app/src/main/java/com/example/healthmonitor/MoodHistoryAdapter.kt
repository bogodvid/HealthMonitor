package com.example.healthmonitor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class MoodHistoryAdapter : RecyclerView.Adapter<MoodHistoryAdapter.MoodViewHolder>() {

    private var moodRecords = mutableListOf<MoodRecord>()

    // Мапа для перекладу тегів
    private val tagTranslations = mapOf(
        "stress" to "Стрес",
        "tired" to "Втома",
        "joy" to "Радість",
        "anxiety" to "Тривога",
        "energy" to "Енергія",
        "sad" to "Сум",
        "sick" to "Хвороба",
        "calm" to "Спокій",
        "motivated" to "Мотивація"
    )

    // Мапа для перекладу факторів
    private val factorTranslations = mapOf(
        "weather" to "Погода",
        "work" to "Робота",
        "social" to "Спілкування",
        "food" to "Їжа",
        "coffee" to "Кофеїн",
        "exercise" to "Спорт",
        "medicine" to "Ліки",
        "sleep" to "Сон",
        "alcohol" to "Алкоголь"
    )

    fun submitList(records: List<MoodRecord>) {
        moodRecords.clear()
        moodRecords.addAll(records)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MoodViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_mood_record, parent, false)
        return MoodViewHolder(view)
    }

    override fun onBindViewHolder(holder: MoodViewHolder, position: Int) {
        holder.bind(moodRecords[position])
    }

    override fun getItemCount(): Int = moodRecords.size

    inner class MoodViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMoodEmoji: TextView = itemView.findViewById(R.id.tvMoodEmoji)
        private val tvDateTime: TextView = itemView.findViewById(R.id.tvDateTime)
        private val tvMoodText: TextView = itemView.findViewById(R.id.tvMoodText)
        private val tvNote: TextView = itemView.findViewById(R.id.tvNote)
        private val chipGroupTags: ChipGroup = itemView.findViewById(R.id.chipGroupTags)
        private val chipGroupFactors: ChipGroup = itemView.findViewById(R.id.chipGroupFactors)

        fun bind(record: MoodRecord) {
            tvMoodEmoji.text = record.getMoodEmoji()
            tvDateTime.text = record.getFormattedDateTime()
            tvMoodText.text = record.getMoodText()

            // Нотатка
            if (record.note.isNotEmpty()) {
                tvNote.visibility = View.VISIBLE
                tvNote.text = record.note
            } else {
                tvNote.visibility = View.GONE
            }

            // Теги
            val tags = record.getTagsList()
            if (tags.isNotEmpty()) {
                chipGroupTags.visibility = View.VISIBLE
                chipGroupTags.removeAllViews()
                tags.forEach { tag ->
                    val chip = createChip(tagTranslations[tag] ?: tag, "#E3F2FD")
                    chipGroupTags.addView(chip)
                }
            } else {
                chipGroupTags.visibility = View.GONE
            }

            // Фактори
            val factors = record.getFactorsList()
            if (factors.isNotEmpty()) {
                chipGroupFactors.visibility = View.VISIBLE
                chipGroupFactors.removeAllViews()
                factors.forEach { factor ->
                    val chip = createChip(factorTranslations[factor] ?: factor, "#FFF3E0")
                    chipGroupFactors.addView(chip)
                }
            } else {
                chipGroupFactors.visibility = View.GONE
            }
        }

        private fun createChip(text: String, backgroundColor: String): Chip {
            return Chip(itemView.context).apply {
                this.text = text
                isClickable = false
                isCheckable = false
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor(backgroundColor)
                )
                setTextColor(android.graphics.Color.parseColor("#424242"))
                textSize = 11f
                chipMinHeight = 32f.dpToPx(context)
            }
        }

        private fun Float.dpToPx(context: android.content.Context): Float {
            return this * context.resources.displayMetrics.density
        }
    }
}