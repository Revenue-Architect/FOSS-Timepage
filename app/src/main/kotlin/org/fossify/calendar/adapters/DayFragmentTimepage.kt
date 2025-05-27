package org.fossify.calendar.fragments

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import org.fossify.calendar.R
import org.fossify.calendar.activities.MainActivity
import org.fossify.calendar.activities.SimpleActivity
import org.fossify.calendar.adapters.DayEventsTimepageAdapter
import org.fossify.calendar.databinding.FragmentDayTimepageBinding
import org.fossify.calendar.extensions.config
import org.fossify.calendar.extensions.eventsHelper
import org.fossify.calendar.extensions.getViewBitmap
import org.fossify.calendar.extensions.printBitmap
import org.fossify.calendar.helpers.*
import org.fossify.calendar.interfaces.NavigationListener
import org.fossify.calendar.models.Event
import org.fossify.commons.extensions.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Modern Day Fragment with Timepage-inspired design
 * Features timeline view, all-day events section, and current time indicator
 */
class DayFragmentTimepage : Fragment() {
    var mListener: NavigationListener? = null
    private var mTextColor = 0
    private var mDayCode = ""
    private var lastHash = 0
    private var currentTimeHandler: Handler? = null
    private var currentTimeRunnable: Runnable? = null

    private lateinit var binding: FragmentDayTimepageBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentDayTimepageBinding.inflate(inflater, container, false)
        mDayCode = requireArguments().getString(DAY_CODE)!!
        
        setupUI()
        setupTimeline()
        setupCurrentTimeIndicator()
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        updateCalendar()
        startCurrentTimeUpdates()
    }

    override fun onPause() {
        super.onPause()
        stopCurrentTimeUpdates()
    }

    private fun setupUI() {
        mTextColor = requireContext().getProperTextColor()
        
        // Setup day title with Timepage styling
        val day = Formatter.getDayTitle(requireContext(), mDayCode)
        binding.dayTitle.apply {
            text = day
            setTextColor(mTextColor)
            setOnClickListener {
                (activity as MainActivity).showGoToDateDialog()
            }
        }

        // Setup floating action button
        binding.fabAddEvent.setOnClickListener {
            val intent = Intent(context, getActivityToOpen(false))
            intent.putExtra(NEW_EVENT_START_TS, Formatter.getDayStartTS(mDayCode))
            startActivity(intent)
        }

        // Apply Timepage color scheme
        binding.root.setBackgroundColor(requireContext().getColor(R.color.dark_primary_background))
        binding.allDaySection.setBackgroundColor(requireContext().getColor(R.color.dark_content_background))
        binding.timelineSection.setBackgroundColor(requireContext().getColor(R.color.dark_content_background))
    }

    private fun setupTimeline() {
        // Setup hourly timeline markers
        binding.timelineContainer.removeAllViews()
        
        for (hour in 0..23) {
            val timeMarker = layoutInflater.inflate(R.layout.timeline_hour_marker, binding.timelineContainer, false)
            val timeText = timeMarker.findViewById<android.widget.TextView>(R.id.hour_text)
            timeText.text = String.format("%02d:00", hour)
            timeText.setTextColor(requireContext().getColor(R.color.dark_text_secondary))
            binding.timelineContainer.addView(timeMarker)
        }
    }

    private fun setupCurrentTimeIndicator() {
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val minute = now.get(Calendar.MINUTE)
        
        binding.currentTimeIndicator.apply {
            // Position indicator based on current time
            val timePercent = (hour * 60 + minute) / (24f * 60f)
            translationY = timePercent * binding.timelineContainer.height
            
            setBackgroundColor(requireContext().getColor(R.color.accent_primary))
            visibility = if (isToday()) View.VISIBLE else View.GONE
        }
        
        binding.currentTimeLabel.apply {
            text = getString(R.string.current_time)
            setTextColor(requireContext().getColor(R.color.accent_primary))
            visibility = if (isToday()) View.VISIBLE else View.GONE
        }
    }

    private fun isToday(): Boolean {
        return mDayCode == Formatter.getTodayCode()
    }

    private fun startCurrentTimeUpdates() {
        if (!isToday()) return
        
        currentTimeRunnable = object : Runnable {
            override fun run() {
                setupCurrentTimeIndicator()
                currentTimeHandler?.postDelayed(this, 60000) // Update every minute
            }
        }
        currentTimeHandler = Handler()
        currentTimeHandler?.post(currentTimeRunnable!!)
    }

    private fun stopCurrentTimeUpdates() {
        currentTimeHandler?.removeCallbacks(currentTimeRunnable ?: return)
        currentTimeHandler = null
        currentTimeRunnable = null
    }

    fun updateCalendar() {
        val startTS = Formatter.getDayStartTS(mDayCode)
        val endTS = Formatter.getDayEndTS(mDayCode)
        context?.eventsHelper?.getEvents(startTS, endTS) {
            receivedEvents(it)
        }
    }

    private fun receivedEvents(events: List<Event>) {
        val newHash = events.hashCode()
        if (newHash == lastHash || !isAdded) {
            return
        }
        lastHash = newHash

        val allDayEvents = events.filter { it.getIsAllDay() }
        val timedEvents = events.filter { !it.getIsAllDay() }
            .sortedWith(compareBy({ it.startTS }, { it.endTS }, { it.title }))

        activity?.runOnUiThread {
            updateAllDayEvents(allDayEvents)
            updateTimedEvents(timedEvents)
        }
    }

    private fun updateAllDayEvents(events: List<Event>) {
        binding.allDayEventsContainer.removeAllViews()
        
        if (events.isEmpty()) {
            binding.allDaySection.visibility = View.GONE
            return
        }
        
        binding.allDaySection.visibility = View.VISIBLE
        binding.allDayHeader.text = getString(R.string.all_day_events)
        binding.allDayHeader.setTextColor(requireContext().getColor(R.color.dark_text_secondary))
        
        events.forEach { event ->
            val eventView = layoutInflater.inflate(R.layout.all_day_event_item_timepage, binding.allDayEventsContainer, false)
            val titleText = eventView.findViewById<android.widget.TextView>(R.id.event_title)
            val colorDot = eventView.findViewById<View>(R.id.event_color_dot)
            
            titleText.text = event.title
            titleText.setTextColor(requireContext().getColor(R.color.dark_text_primary))
            
            // Set event color
            val eventColor = getEventColor(event)
            colorDot.setBackgroundColor(eventColor)
            
            eventView.setOnClickListener {
                editEvent(event)
            }
            
            binding.allDayEventsContainer.addView(eventView)
        }
    }

    private fun updateTimedEvents(events: List<Event>) {
        if (activity == null) return

        val adapter = DayEventsTimepageAdapter(activity as SimpleActivity, ArrayList(events), mDayCode) {
            editEvent(it as Event)
        }
        
        binding.eventsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
        }

        if (requireContext().areSystemAnimationsEnabled) {
            binding.eventsRecyclerView.scheduleLayoutAnimation()
        }
    }

    private fun getEventColor(event: Event): Int {
        // Use event type color or default to accent color
        return try {
            requireContext().getColor(R.color.event_color_1) // Default to orange
        } catch (e: Exception) {
            requireContext().getColor(R.color.accent_primary)
        }
    }

    private fun editEvent(event: Event) {
        Intent(context, getActivityToOpen(event.isTask())).apply {
            putExtra(EVENT_ID, event.id)
            putExtra(EVENT_OCCURRENCE_TS, event.startTS)
            putExtra(IS_TASK_COMPLETED, event.isTaskCompleted())
            startActivity(this)
        }
    }

    fun printCurrentView() {
        // Hide navigation elements for printing
        binding.fabAddEvent.beGone()
        
        Handler().postDelayed({
            requireContext().printBitmap(binding.root.getViewBitmap())
            
            Handler().postDelayed({
                binding.fabAddEvent.beVisible()
            }, 1000)
        }, 1000)
    }
} 