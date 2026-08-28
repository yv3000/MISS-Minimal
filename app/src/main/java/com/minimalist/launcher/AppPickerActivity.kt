package com.minimalist.launcher

import android.content.Intent
import android.content.pm.ApplicationInfo
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
    private var isPomodoroMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isPomodoroMode = intent.getBooleanExtra("pomodoro_mode", false)
        slot = intent.getIntExtra("slot", -1)

        if (!isPomodoroMode && slot == -1) {
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
                if (isPomodoroMode && isLikelyGame(app, name)) continue
                apps.add(AppItem(app.packageName, name))
            }
        }
        apps.sortBy { it.name.lowercase() }
        
        binding.rvApps.adapter = PickerAdapter()
    }

    @Suppress("DEPRECATION")
    private fun isLikelyGame(app: ApplicationInfo, name: String): Boolean {
        if (app.category == ApplicationInfo.CATEGORY_GAME ||
            app.flags and ApplicationInfo.FLAG_IS_GAME != 0
        ) return true

        val packageName = app.packageName.lowercase()
        val launcherIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(app.packageName)
        val gameIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_APP_GAME)
            .setPackage(app.packageName)
        if (packageManager.queryIntentActivities(launcherIntent, PackageManager.GET_RESOLVED_FILTER)
                .any { it.filter?.hasCategory(Intent.CATEGORY_APP_GAME) == true } ||
            packageManager.queryIntentActivities(gameIntent, PackageManager.GET_RESOLVED_FILTER).isNotEmpty()
        ) return true

        if (app.metaData?.keySet()?.any { key ->
                val normalized = key.lowercase()
                GAME_METADATA_PREFIXES.any(normalized::startsWith)
            } == true
        ) return true

        val packageTokens = packageName.split('.', '_', '-')
        val nameTokens = name.lowercase().split(Regex("[^a-z0-9]+"))
        return GAME_PUBLISHER_PACKAGES.any { packageName == it || packageName.startsWith("$it.") } ||
            packageTokens.any(GAME_PACKAGE_TOKENS::contains) ||
            nameTokens.any(GAME_NAME_TOKENS::contains)
    }

    companion object {
        private val GAME_METADATA_PREFIXES = setOf(
            "com.google.android.gms.games.",
            "com.epicgames.unreal."
        )
        private val GAME_PUBLISHER_PACKAGES = setOf(
            "com.activision", "com.ea.gp", "com.electronicarts", "com.gameloft",
            "com.king", "com.miniclip", "com.mojang", "com.nianticlabs", "com.playrix",
            "com.roblox", "com.rovio", "com.scopely", "com.supercell", "com.ubisoft", "com.zynga"
        )
        private val GAME_PACKAGE_TOKENS = setOf("arcade", "game", "games", "gaming")
        private val GAME_NAME_TOKENS = setOf("arcade", "game", "games", "solitaire", "sudoku")
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
                        if (isPomodoroMode) {
                            val data = android.content.Intent()
                            data.putExtra("package_name", app.packageName)
                            setResult(RESULT_OK, data)
                        } else {
                            PrefsManager.setHomeApp(slot, app.packageName)
                        }
                        finish()
                    }.start()
                }.start()
            }
        }

        override fun getItemCount() = apps.size
    }
}
