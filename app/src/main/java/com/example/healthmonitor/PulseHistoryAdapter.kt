package com.example.healthmonitor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PulseHistoryAdapter : RecyclerView.Adapter<PulseHistoryAdapter.ViewHolder>() {

    private var records = listOf<PulseRecord>()

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvBpm: TextView = itemView.findViewById(R.id.tvBpm)
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pulse_record, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = records[position]

        holder.tvBpm.text = "${record.bpm} BPM"
        holder.tvDate.text = record.getFormattedDate()

        // Визначаємо статус на основі пульсу
        val (status, color) = when {
            record.bpm < 60 -> "Низький" to android.R.color.holo_blue_light
            record.bpm in 60..100 -> "Нормальний" to android.R.color.holo_green_light
            record.bpm in 101..120 -> "Підвищений" to android.R.color.holo_orange_light
            else -> "Високий" to android.R.color.holo_red_light
        }

        holder.tvStatus.text = status
        holder.tvStatus.setBackgroundColor(
            holder.itemView.context.getColor(color)
        )
    }

    override fun getItemCount(): Int = records.size

    fun updateData(newRecords: List<PulseRecord>) {
        records = newRecords
        notifyDataSetChanged()
    }
}