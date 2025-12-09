package com.example.healthmonitor

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.DecimalFormat

class StepsHistoryAdapter(private val context: Context) : RecyclerView.Adapter<StepsHistoryAdapter.ViewHolder>() {

    private var records = listOf<DailySteps>()
    private val decimalFormat = DecimalFormat("#.##")
    private var stepsGoal = 10000 // Дефолтна ціль

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val tvSteps: TextView = itemView.findViewById(R.id.tvSteps)
        val tvDistance: TextView = itemView.findViewById(R.id.tvDistance)
        val tvCalories: TextView = itemView.findViewById(R.id.tvCalories)
        val tvGoalIndicator: TextView = itemView.findViewById(R.id.tvGoalIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_steps_record, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = records[position]

        holder.tvDate.text = record.getFormattedDate()
        holder.tvSteps.text = "${record.steps} кроків"
        holder.tvDistance.text = "${decimalFormat.format(record.distance)} км"
        holder.tvCalories.text = "${record.calories} ккал"

        // Індикатор досягнення цілі (використовуємо динамічну ціль)
        when {
            record.steps >= stepsGoal -> {
                holder.tvGoalIndicator.text = "🎯 Ціль досягнута!"
                holder.tvGoalIndicator.setTextColor(
                    holder.itemView.context.getColor(android.R.color.holo_green_dark)
                )
            }
            record.steps >= stepsGoal * 0.75 -> {
                holder.tvGoalIndicator.text = "💪 ${((record.steps.toFloat() / stepsGoal) * 100).toInt()}% від цілі"
                holder.tvGoalIndicator.setTextColor(
                    holder.itemView.context.getColor(android.R.color.holo_orange_light)
                )
            }
            else -> {
                holder.tvGoalIndicator.text = "${((record.steps.toFloat() / stepsGoal) * 100).toInt()}% від цілі"
                holder.tvGoalIndicator.setTextColor(
                    holder.itemView.context.getColor(android.R.color.darker_gray)
                )
            }
        }
    }

    override fun getItemCount(): Int = records.size

    fun updateData(newRecords: List<DailySteps>, goal: Int = 10000) {
        records = newRecords
        stepsGoal = goal
        notifyDataSetChanged()
    }
}