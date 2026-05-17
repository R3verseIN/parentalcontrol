package com.parentalcontrol.ui

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.widget.SwitchCompat
import com.parentalcontrol.R
import com.parentalcontrol.security.BlocklistManager

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable,
    var isBlocked: Boolean
)

class AppListAdapter(private var appList: List<AppInfo>) :
    RecyclerView.Adapter<AppListAdapter.AppViewHolder>() {

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.ivAppIcon)
        val tvName: TextView = view.findViewById(R.id.tvAppName)
        val tvPackage: TextView = view.findViewById(R.id.tvAppPackage)
        val tvScheduleStatus: TextView = view.findViewById(R.id.tvAppScheduleStatus)
        val btnConfigureSchedule: android.widget.ImageButton = view.findViewById(R.id.btnConfigureSchedule)
        val switchBlocked: SwitchCompat = view.findViewById(R.id.switchBlocked)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_info, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = appList[position]
        holder.ivIcon.setImageDrawable(app.icon)
        holder.tvName.text = app.name
        holder.tvPackage.text = app.packageName

        // Render dynamic schedules and status colors
        val schedules = BlocklistManager.getBlockedSchedules(app.packageName)
        if (app.isBlocked) {
            holder.tvScheduleStatus.text = "Blocked Completely"
            holder.tvScheduleStatus.setTextColor(android.graphics.Color.parseColor("#FF453A")) // Premium Crimson
        } else if (schedules.isNotEmpty()) {
            holder.tvScheduleStatus.text = "Schedule: Active (${schedules.size} windows)"
            holder.tvScheduleStatus.setTextColor(android.graphics.Color.parseColor("#5E5CE6")) // Premium Indigo
        } else {
            holder.tvScheduleStatus.text = "Always Allowed"
            holder.tvScheduleStatus.setTextColor(android.graphics.Color.parseColor("#30D158")) // Premium Green
        }

        // Clear checking listener first to prevent binding trigger loops
        holder.switchBlocked.setOnCheckedChangeListener(null)
        holder.switchBlocked.isChecked = app.isBlocked

        // Update block status inside persistent storage in real-time
        holder.switchBlocked.setOnCheckedChangeListener { _, isChecked ->
            app.isBlocked = isChecked
            if (isChecked) {
                BlocklistManager.blockPackage(app.packageName)
            } else {
                BlocklistManager.unblockPackage(app.packageName)
            }
            notifyItemChanged(position)
        }

        // Bind schedule setup dialog trigger click
        holder.btnConfigureSchedule.setOnClickListener {
            showScheduleDialog(holder.itemView.context, app) {
                notifyItemChanged(position)
            }
        }
    }

    override fun getItemCount(): Int = appList.size

    /**
     * Rebind the adapter data when search criteria shifts.
     */
    fun updateList(newList: List<AppInfo>) {
        this.appList = newList
        notifyDataSetChanged()
    }

    /**
     * Helper to render dynamic schedule range editor dialog.
     */
    private fun showScheduleDialog(context: android.content.Context, app: AppInfo, onUpdated: () -> Unit) {
        val activity = context as? androidx.fragment.app.FragmentActivity ?: return
        val activeSchedules = BlocklistManager.getBlockedSchedules(app.packageName).toMutableList()

        val dialog = androidx.appcompat.app.AlertDialog.Builder(context)
            .create()

        // Dynamic DP to PX conversion
        val density = context.resources.displayMetrics.density
        fun dpToPx(dp: Int): Int = (dp * density).toInt()

        // Sleek Material container
        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20))
            setBackgroundColor(android.graphics.Color.parseColor("#1E1E2C")) // Sleek deep glass-dark background
        }

        val titleTv = android.widget.TextView(context).apply {
            text = "Schedule: ${app.name}"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dpToPx(16))
        }
        container.addView(titleTv)

        val listLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }

        val scrollView = android.widget.ScrollView(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(160)
            )
            isFillViewport = true
        }
        scrollView.addView(listLayout)
        container.addView(scrollView)

        fun formatTimeTo12Hour(time: String): String {
            val parts = time.split(":")
            if (parts.size != 2) return time
            val hour = parts[0].toIntOrNull() ?: return time
            val min = parts[1].toIntOrNull() ?: return time
            val suffix = if (hour >= 12) "PM" else "AM"
            val displayHour = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }
            return String.format("%02d:%02d %s", displayHour, min, suffix)
        }

        fun formatRangeTo12Hour(range: String): String {
            val parts = range.split("-")
            if (parts.size != 2) return range
            return "${formatTimeTo12Hour(parts[0])} - ${formatTimeTo12Hour(parts[1])}"
        }

        fun refreshList() {
            listLayout.removeAllViews()
            if (activeSchedules.isEmpty()) {
                val emptyTv = android.widget.TextView(context).apply {
                    text = "No custom time restrictions set."
                    setTextColor(android.graphics.Color.parseColor("#8E8E93"))
                    textSize = 13f
                    setPadding(0, dpToPx(8), 0, dpToPx(8))
                }
                listLayout.addView(emptyTv)
            } else {
                for (range in activeSchedules) {
                    val row = android.widget.LinearLayout(context).apply {
                        orientation = android.widget.LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setPadding(0, dpToPx(6), 0, dpToPx(6))
                    }

                    val labelTv = android.widget.TextView(context).apply {
                        layoutParams = android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        text = formatRangeTo12Hour(range)
                        setTextColor(android.graphics.Color.WHITE)
                        textSize = 14f
                    }
                    row.addView(labelTv)

                    val deleteBtn = android.widget.ImageButton(context).apply {
                        setImageResource(android.R.drawable.ic_menu_delete) // Standard delete trash vector
                        background = null
                        setColorFilter(android.graphics.Color.parseColor("#FF453A"))
                        setOnClickListener {
                            activeSchedules.remove(range)
                            refreshList()
                        }
                    }
                    row.addView(deleteBtn)
                    listLayout.addView(row)
                }
            }
        }
        refreshList()

        val addBtn = com.google.android.material.button.MaterialButton(context).apply {
            text = "Add Blocked Range"
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.parseColor("#5E5CE6")) // Modern Indigo button accent
            setIconResource(android.R.drawable.ic_menu_add) // Crisp native vector plus icon
            iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START
            iconTint = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
            setPadding(0, dpToPx(8), 0, dpToPx(8))
            val params = android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dpToPx(16), 0, 0)
            }
            layoutParams = params
            setOnClickListener {
                val startPicker = com.google.android.material.timepicker.MaterialTimePicker.Builder()
                    .setTimeFormat(com.google.android.material.timepicker.TimeFormat.CLOCK_12H)
                    .setTitleText("Select Block Start Time")
                    .build()

                startPicker.addOnPositiveButtonClickListener {
                    val startHour = startPicker.hour
                    val startMin = startPicker.minute

                    val endPicker = com.google.android.material.timepicker.MaterialTimePicker.Builder()
                        .setTimeFormat(com.google.android.material.timepicker.TimeFormat.CLOCK_12H)
                        .setTitleText("Select Block End Time")
                        .build()

                    endPicker.addOnPositiveButtonClickListener {
                        val endHour = endPicker.hour
                        val endMin = endPicker.minute

                        val rangeStr = String.format("%02d:%02d-%02d:%02d", startHour, startMin, endHour, endMin)
                        activeSchedules.add(rangeStr)
                        refreshList()
                    }
                    endPicker.show(activity.supportFragmentManager, "END_PICKER")
                }
                startPicker.show(activity.supportFragmentManager, "START_PICKER")
            }
        }
        container.addView(addBtn)

        val buttonLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.END
            setPadding(0, dpToPx(16), 0, 0)
        }

        val cancelBtn = android.widget.Button(context).apply {
            text = "Cancel"
            setTextColor(android.graphics.Color.WHITE)
            background = null
            setOnClickListener { dialog.dismiss() }
        }
        buttonLayout.addView(cancelBtn)

        val saveBtn = android.widget.Button(context).apply {
            text = "Save"
            setTextColor(android.graphics.Color.parseColor("#30D158")) // Neon green save action text
            background = null
            setOnClickListener {
                BlocklistManager.saveBlockedSchedules(app.packageName, activeSchedules.toSet())
                dialog.dismiss()
                onUpdated()
            }
        }
        buttonLayout.addView(saveBtn)
        container.addView(buttonLayout)

        dialog.setView(container)
        dialog.show()
    }
}
