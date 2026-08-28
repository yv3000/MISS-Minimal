package com.minimalist.launcher

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
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
    private val adapter = NotificationAdapter()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            adapter.refresh()
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
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, IntentFilter(NotificationService.ACTION_NOTIFY_UPDATED))
        adapter.refresh()

        adapter.attachSwipe(binding.rvNotifications)

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

}

class NotificationAdapter : RecyclerView.Adapter<NotificationAdapter.VH>() {
        private var items: List<NotificationService.NotificationItem> = emptyList()

        class VH(val b: RowNotificationBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            return VH(RowNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.b.ivAppIcon.setImageDrawable(item.appIcon)
            holder.b.ivAppIcon.visibility = if (item.appIcon == null) View.GONE else View.VISIBLE
            holder.b.tvAppName.text = item.appLabel
            holder.b.tvTimeCount.text = android.text.format.DateFormat.getTimeFormat(holder.itemView.context)
                .format(java.util.Date(item.postTime))
            holder.b.tvTitle.text = item.title
            holder.b.tvTitle.visibility = if (item.title.isEmpty()) View.GONE else View.VISIBLE
            holder.b.tvText.text = item.text
            holder.b.tvText.visibility = if (item.text.isEmpty()) View.GONE else View.VISIBLE
            holder.b.root.setOnClickListener {
                try {
                    if (item.contentIntent != null) item.contentIntent.send()
                    else openApp(holder.itemView.context, item.packageName)
                } catch (_: PendingIntent.CanceledException) {
                    openApp(holder.itemView.context, item.packageName)
                }
            }
        }

        override fun getItemCount() = items.size

        fun itemAt(position: Int) = items.getOrNull(position)

        fun refresh() {
            submit(NotificationService.notifications)
        }

        fun submit(newItems: List<NotificationService.NotificationItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        fun removeAt(position: Int) {
            items = items.toMutableList().also { it.removeAt(position) }
            notifyItemRemoved(position)
        }

        fun attachSwipe(recyclerView: RecyclerView) {
            ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
                override fun onMove(rv: RecyclerView, holder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false
                override fun getSwipeDirs(rv: RecyclerView, holder: RecyclerView.ViewHolder) =
                    if (itemAt(holder.bindingAdapterPosition)?.clearable == true) super.getSwipeDirs(rv, holder) else 0
                override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) {
                    val position = holder.bindingAdapterPosition
                    val item = itemAt(position)
                    if (item?.clearable == true && NotificationService.instance != null) {
                        NotificationService.instance?.dismissNotification(item.key)
                        removeAt(position)
                    } else if (position != RecyclerView.NO_POSITION) notifyItemChanged(position)
                }
            }).attachToRecyclerView(recyclerView)
        }

        private fun openApp(context: Context, packageName: String) {
            context.packageManager.getLaunchIntentForPackage(packageName)?.let(context::startActivity)
        }
}
