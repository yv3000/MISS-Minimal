package com.minimalist.launcher

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class NotificationService : NotificationListenerService() {
    companion object {
        const val ACTION_NOTIFY_UPDATED = "com.minimalist.launcher.NOTIFY_UPDATED"
        val notifications = mutableListOf<NotificationItem>()
    }

    data class NotificationItem(
        val appName: String,
        val time: Long,
        var count: Int = 1
    )

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        updateList()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        updateList()
    }

    private fun updateList() {
        val active = activeNotifications ?: return
        val newList = mutableListOf<NotificationItem>()
        
        val groups = active.groupBy { it.packageName }
        for ((pkg, sbns) in groups) {
            val appName = try {
                val info = packageManager.getApplicationInfo(pkg, 0)
                packageManager.getApplicationLabel(info).toString()
            } catch (e: Exception) { pkg }
            
            val time = sbns.maxOf { it.postTime }
            newList.add(NotificationItem(appName, time, sbns.size))
        }
        
        notifications.clear()
        notifications.addAll(newList.sortedByDescending { it.time })
        
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_NOTIFY_UPDATED))
    }
}
