package com.minimalist.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.minimalist.launcher.databinding.ActivityAppDrawerBinding
import com.minimalist.launcher.databinding.RowAppBinding

class AppDrawerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAppDrawerBinding
    private val allApps = mutableListOf<AppItem>()
    private val filteredApps = mutableListOf<AppItem>()
    private lateinit var adapter: AppAdapter
    
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadApps()
        }
    }

    private lateinit var gestureDetector: android.view.GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppDrawerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        gestureDetector = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val diffY = e2.y - e1.y
                if (diffY > 100 && Math.abs(velocityY) > 100) {
                    finish()
                    return true
                }
                return false
            }
        })

        adapter = AppAdapter()
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        if (isLandscape) {
            binding.rvApps.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 3).apply {
                spanSizeLookup = object : androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int = if (adapter.items[position] is String) 3 else 1
                }
            }
        } else {
            binding.rvApps.layoutManager = LinearLayoutManager(this)
        }
        binding.rvApps.adapter = adapter

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, LauncherSettingsActivity::class.java))
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterApps(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, filter)

        loadApps()
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(packageReceiver)
    }

    private fun loadApps() {
        allApps.clear()
        if (AppDrawerManager.isReady) {
            allApps.addAll(AppDrawerManager.cachedApps)
        } else {
            val pm = packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in apps) {
                if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                    val name = pm.getApplicationLabel(app).toString()
                    allApps.add(AppItem(app.packageName, name))
                }
            }
            allApps.sortBy { it.name.lowercase() }
        }
        filterApps(binding.etSearch.text.toString())
    }

    private fun filterApps(query: String) {
        filteredApps.clear()
        if (query.isEmpty()) {
            filteredApps.addAll(allApps)
        } else {
            filteredApps.addAll(allApps.filter { it.name.contains(query, ignoreCase = true) })
        }

        val withHeaders = mutableListOf<Any>()
        var currentLetter = ""
        for (app in filteredApps) {
            val custom = PrefsManager.getCustomName(app.packageName)
            val displayName = custom ?: app.name
            
            val firstLetter = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
            if (firstLetter != currentLetter) {
                currentLetter = firstLetter
                withHeaders.add(currentLetter)
            }
            withHeaders.add(app)
        }

        adapter.items.clear()
        adapter.items.addAll(withHeaders)
        adapter.notifyDataSetChanged()
    }



    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(0, R.anim.slide_down_exit)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, R.anim.slide_down_exit)
    }

    inner class AppAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        val items = mutableListOf<Any>()

        override fun getItemViewType(position: Int): Int = if (items[position] is String) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val b = RowAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return object : RecyclerView.ViewHolder(b.root) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val b = RowAppBinding.bind(holder.itemView)
            val item = items[position]
            
            b.root.setOnClickListener(null)
            b.root.setOnLongClickListener(null)

            if (item is String) {
                b.tvHeader.visibility = View.VISIBLE
                b.tvHeader.text = item
                b.tvAppName.visibility = View.GONE
            } else if (item is AppItem) {
                b.tvHeader.visibility = View.GONE
                b.tvAppName.visibility = View.VISIBLE
                
                val custom = PrefsManager.getCustomName(item.packageName)
                b.tvAppName.text = custom ?: item.name

                b.root.setOnClickListener {
                    val action = {
                        val limitMs = PrefsManager.getLimitMs(item.packageName)
                        val elapsed = System.currentTimeMillis() - PrefsManager.getLimitStart(item.packageName)
                        if (limitMs > 0 && elapsed >= limitMs) {
                            val intent = Intent(this@AppDrawerActivity, TimeLimitActivity::class.java)
                            intent.putExtra("packageName", item.packageName)
                            intent.putExtra("appName", custom ?: item.name)
                            startActivity(intent)
                        } else {
                            val intent = packageManager.getLaunchIntentForPackage(item.packageName)
                            if (intent != null) startActivity(intent)
                        }
                    }
                    it.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).withEndAction {
                        it.animate().scaleX(1f).scaleY(1f).setDuration(80).withEndAction(action).start()
                    }.start()
                }

                b.root.setOnLongClickListener {
                    showBottomSheet(item)
                    true
                }
            }
            val size = AppFont.get(holder.itemView.context)
            AppFont.applyToAllTextViews(holder.itemView, size)
        }

        override fun getItemCount() = items.size
    }

    private fun showBottomSheet(item: AppItem) {
        val bs = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_app_options, null)
        bs.setContentView(view)

        val custom = PrefsManager.getCustomName(item.packageName)
        val displayName = custom ?: item.name

        view.findViewById<TextView>(R.id.tvBsAppTitle).text = displayName

        view.findViewById<View>(R.id.btnBsAddHome).setOnClickListener {
            var slot = -1
            for (i in 1..3) {
                if (PrefsManager.getHomeApp(i).isNullOrEmpty()) {
                    slot = i
                    break
                }
            }
            if (slot != -1) {
                PrefsManager.setHomeApp(slot, item.packageName)
                Toast.makeText(this, "Added to Home Screen", Toast.LENGTH_SHORT).show()
                bs.dismiss()
            } else {
                Toast.makeText(this, "Home screen is full (max 3)", Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<View>(R.id.btnBsTimeLimit).setOnClickListener {
            bs.dismiss()
            val intent = Intent(this, TimeLimitActivity::class.java)
            intent.putExtra("packageName", item.packageName)
            intent.putExtra("appName", displayName)
            startActivity(intent)
        }

        view.findViewById<View>(R.id.btnBsRename).setOnClickListener {
            bs.dismiss()
            val input = EditText(this)
            input.setText(displayName)
            AlertDialog.Builder(this)
                .setTitle("Rename App")
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    PrefsManager.setCustomName(item.packageName, input.text.toString())
                    filterApps(binding.etSearch.text.toString())
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        view.findViewById<View>(R.id.btnBsAppInfo).setOnClickListener {
            bs.dismiss()
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${item.packageName}")
            }
            startActivity(intent)
        }

        view.findViewById<View>(R.id.btnBsUninstall).setOnClickListener {
            bs.dismiss()
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:${item.packageName}")
            }
            startActivity(intent)
        }

        bs.show()
    }

    override fun onResume() {
        super.onResume()
        AppFont.applyToActivity(this)
    }

    // Moved font logic to AppFont.kt
}

data class AppItem(val packageName: String, val name: String)
