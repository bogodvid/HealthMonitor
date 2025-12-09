package com.example.healthmonitor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SleepHistoryAdapter : RecyclerView.Adapter<SleepHistoryAdapter.SleepViewHolder>() {

    private var sleepRecords = listOf<SleepRecord>()

    fun submitList(records: List<SleepRecord>) {
        sleepRecords = records
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SleepViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sleep_record, parent, false)
        return SleepViewHolder(view)
    }

    override fun onBindViewHolder(holder: SleepViewHolder, position: Int) {
        holder.bind(sleepRecords[position])
    }

    override fun getItemCount(): Int = sleepRecords.size

    class SleepViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
        private val tvQuality: TextView = itemView.findViewById(R.id.tvQuality)

        fun bind(record: SleepRecord) {
            tvDate.text = record.getFormattedDate()
            tvTime.text = "${record.getFormattedStartTime()} → ${record.getFormattedEndTime()}"
            tvDuration.text = record.getFormattedDuration()
            tvQuality.text = "Якість: ${record.quality.toInt()}%"
        }
    }
}