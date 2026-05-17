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
}
