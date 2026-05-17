package com.parentalcontrol.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.parentalcontrol.R
import com.parentalcontrol.security.BlocklistManager

class AppBlockerActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var etSearch: EditText
    private lateinit var rvApps: RecyclerView

    private lateinit var adapter: AppListAdapter
    private var allApps = listOf<AppInfo>()
    private var filteredApps = listOf<AppInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_blocker)

        // Initialize our persistent block storage
        BlocklistManager.init(this)

        // Bind layout views
        btnBack = findViewById(R.id.btnBack)
        etSearch = findViewById(R.id.etSearch)
        rvApps = findViewById(R.id.rvApps)

        // Setup back navigation
        btnBack.setOnClickListener {
            finish()
        }

        // Fetch installed applications from PackageManager
        allApps = fetchInstalledLauncherApps()
        filteredApps = allApps

        // Set adapter onto recycler view
        adapter = AppListAdapter(filteredApps)
        rvApps.adapter = adapter

        // Setup real-time search filtering
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterAppList(s?.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    /**
     * Query PackageManager to retrieve only interactive launcher-facing applications.
     */
    private fun fetchInstalledLauncherApps(): List<AppInfo> {
        val appList = mutableListOf<AppInfo>()
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        
        val activities = packageManager.queryIntentActivities(mainIntent, 0)
        for (activity in activities) {
            val appPackageName = activity.activityInfo.packageName
            
            // Safety: Skip our own parental control package to prevent locking the parent out
            if (appPackageName == packageName) continue

            val appLabel = activity.loadLabel(packageManager).toString()
            val appIcon = activity.loadIcon(packageManager)
            val isBlocked = BlocklistManager.isBlocked(appPackageName)

            // Prevent redundant duplicates for apps with multiple launch vectors
            if (appList.none { it.packageName == appPackageName }) {
                appList.add(AppInfo(appLabel, appPackageName, appIcon, isBlocked))
            }
        }

        // Sort alphabetically by name
        return appList.sortedBy { it.name.lowercase() }
    }

    /**
     * Filters the cached application list based on parent's search text.
     */
    private fun filterAppList(query: String?) {
        filteredApps = if (query.isNullOrEmpty()) {
            allApps
        } else {
            allApps.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
            }
        }
        adapter.updateList(filteredApps)
    }
}
