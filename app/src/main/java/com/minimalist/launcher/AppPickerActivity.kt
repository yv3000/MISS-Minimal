package com.minimalist.launcher

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.minimalist.launcher.databinding.ActivityAppPickerBinding
import com.minimalist.launcher.databinding.RowAppBinding

class AppPickerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAppPickerBinding
    private val apps = mutableListOf<AppItem>()
    private var slot = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        slot = intent.getIntExtra("slot", -1)
        if (slot == -1) {
            finish()
            return
        }

        binding.rvApps.layoutManager = LinearLayoutManager(this)
        loadApps()
    }

    private fun loadApps() {
        apps.clear()
        val pm = packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in installedApps) {
            if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                val name = pm.getApplicationLabel(app).toString()
                apps.add(AppItem(app.packageName, name))
            }
        }
        apps.sortBy { it.name.lowercase() }
        
        binding.rvApps.adapter = PickerAdapter()
    }

    inner class PickerAdapter : RecyclerView.Adapter<PickerAdapter.ViewHolder>() {
        inner class ViewHolder(val b: RowAppBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = RowAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = apps[position]
            holder.b.tvHeader.visibility = android.view.View.GONE
            holder.b.tvAppName.visibility = android.view.View.VISIBLE
            
            val custom = PrefsManager.getCustomName(app.packageName)
            holder.b.tvAppName.text = custom ?: app.name
            
            holder.b.root.setOnClickListener {
                it.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).withEndAction {
                    it.animate().scaleX(1f).scaleY(1f).setDuration(80).withEndAction {
                        PrefsManager.setHomeApp(slot, app.packageName)
                        finish()
                    }.start()
                }.start()
            }
        }

        override fun getItemCount() = apps.size
    }
}
