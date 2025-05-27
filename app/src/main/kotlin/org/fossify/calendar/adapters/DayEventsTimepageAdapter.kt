package org.fossify.calendar.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.fossify.calendar.R
import org.fossify.calendar.activities.SimpleActivity
import org.fossify.calendar.extensions.config
import org.fossify.calendar.helpers.Formatter
import org.fossify.calendar.models.Event
import org.fossify.commons.extensions.getProperTextColor
import java.text.SimpleDateFormat
import java.util.*

/**
 * Modern adapter for day view events with Timepage design
 * Features event cards with time display, color coding, and clean typography
 */
class DayEventsTimepageAdapter(
    private val activity: SimpleActivity,
    private val events: ArrayList<Event>,
    private val dayCode: String,
    private val itemClick: (Any) -> Unit
) : RecyclerView.Adapter<DayEventsTimepageAdapter.ViewHolder>() {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val replaceDescription = activity.config.replaceDescription

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.event_list_item_timepage, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val event = events[position]
        holder.bind(event)
    }

    override fun getItemCount() = events.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val eventTitle: TextView = itemView.findViewById(R.id.event_title)
        private val eventTime: TextView = itemView.findViewById(R.id.event_time)
        private val eventDescription: TextView = itemView.findViewById(R.id.event_description)
        private val eventColorDot: View = itemView.findViewById(R.id.event_color_dot)

        fun bind(event: Event) {
            // Set event title
            eventTitle.text = event.title
            eventTitle.setTextColor(activity.getColor(R.color.dark_text_primary))

            // Set event time
            val startTime = Date(event.startTS * 1000L)
            val endTime = Date(event.endTS * 1000L)
            eventTime.text = "${timeFormat.format(startTime)} – ${timeFormat.format(endTime)}"
            eventTime.setTextColor(activity.getColor(R.color.dark_text_secondary))

            // Set event description/location
            val description = if (replaceDescription) {
                event.location.ifEmpty { event.description }
            } else {
                event.description.ifEmpty { event.location }
            }
            
            if (description.isNotEmpty()) {
                eventDescription.text = description
                eventDescription.visibility = View.VISIBLE
                eventDescription.setTextColor(activity.getColor(R.color.dark_text_secondary))
            } else {
                eventDescription.visibility = View.GONE
            }

            // Set event color
            val eventColor = getEventColor(event)
            eventColorDot.setBackgroundColor(eventColor)

            // Set click listener
            itemView.setOnClickListener {
                itemClick(event)
            }

            // Apply card styling
            itemView.background = activity.getDrawable(R.drawable.event_card_background_timepage)
            
            // Dim past events if they're from today
            if (isEventInPast(event) && dayCode == Formatter.getTodayCode()) {
                itemView.alpha = 0.6f
            } else {
                itemView.alpha = 1.0f
            }
        }

        private fun getEventColor(event: Event): Int {
            // Cycle through the event color palette
            val colors = arrayOf(
                R.color.event_color_1, // Orange
                R.color.event_color_2, // Purple  
                R.color.event_color_3, // Teal
                R.color.event_color_4, // Green
                R.color.event_color_5, // Blue
                R.color.event_color_6, // Red
                R.color.event_color_7  // Amber
            )
            
            return try {
                val colorIndex = (event.id ?: 0).toInt() % colors.size
                activity.getColor(colors[colorIndex])
            } catch (e: Exception) {
                activity.getColor(R.color.accent_primary)
            }
        }

        private fun isEventInPast(event: Event): Boolean {
            return event.endTS < (System.currentTimeMillis() / 1000)
        }
    }
} 