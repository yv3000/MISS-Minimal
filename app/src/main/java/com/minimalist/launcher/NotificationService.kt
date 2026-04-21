package com.minimalist.launcher

import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class NotificationService : NotificationListenerService() {
    companion object {
        const val ACTION_NOTIFY_UPDATED = "com.minimalist.launcher.NOTIFY_UPDATED"
        val notifications = mutableListOf<NotificationItem>()
        var instance: NotificationService? = null
    }

    data class NotificationItem(
        val appName: String,
        val time: Long,
        var count: Int = 1,
        val keys: List<String> = listOf()
    )

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        updateList()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
    }

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
            val keys = sbns.map { it.key }
            newList.add(NotificationItem(appName, time, sbns.size, keys))
        }
        
        notifications.clear()
        notifications.addAll(newList.sortedByDescending { it.time })
        
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_NOTIFY_UPDATED))
    }

    fun dismissNotifications(keys: List<String>) {
        if (Build.VERSION.SDK_INT >= 21) {
            keys.forEach { cancelNotification(it) }
        } else {
            // Deprecated fallback for very old APIs
            cancelAllNotifications()
        }
    }
}
