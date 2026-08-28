package com.minimalist.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import com.minimalist.launcher.databinding.ActivityNotificationPanelBinding
import com.minimalist.launcher.databinding.RowNotificationBinding

class NotificationPanelActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNotificationPanelBinding
    private val adapter = NotifAdapter()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            adapter.notifyDataSetChanged()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationPanelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)) {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }

        binding.rvNotifications.layoutManager = LinearLayoutManager(this)
        binding.rvNotifications.adapter = adapter

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position >= 0 && position < NotificationService.notifications.size) {
                    val item = NotificationService.notifications[position]
                    NotificationService.instance?.dismissNotifications(item.keys)
                    // The updateList broadcast will refresh the RecyclerView anyway,
                    // but we can proactively notify item removal for smoother anims
                    NotificationService.notifications.removeAt(position)
                    adapter.notifyItemRemoved(position)
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.rvNotifications)

        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, IntentFilter(NotificationService.ACTION_NOTIFY_UPDATED))
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(0, R.anim.slide_up_exit)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, R.anim.slide_up_exit)
    }

    inner class NotifAdapter : RecyclerView.Adapter<NotifAdapter.VH>() {
        inner class VH(val b: RowNotificationBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            return VH(RowNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = NotificationService.notifications[position]
            val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(item.time))
            holder.b.tvAppName.text = item.appName
            holder.b.tvTimeCount.text = if (item.count > 1) "${time} · ${item.count}" else time
        }

        override fun getItemCount() = NotificationService.notifications.size
    }
}
