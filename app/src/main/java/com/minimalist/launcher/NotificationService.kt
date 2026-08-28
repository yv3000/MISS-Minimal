package com.minimalist.launcher

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Drawable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class NotificationService : NotificationListenerService() {
    companion object {
        const val ACTION_NOTIFY_UPDATED = "com.minimalist.launcher.NOTIFY_UPDATED"

        @Volatile
        var notifications: List<NotificationItem> = emptyList()
            private set

        @Volatile
        var instance: NotificationService? = null
            private set
    }

    data class NotificationItem(
        val key: String,
        val packageName: String,
        val appLabel: String,
        val appIcon: Drawable?,
        val title: String,
        val text: String,
        val postTime: Long,
        val contentIntent: PendingIntent?,
        val clearable: Boolean
    )

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        updateList()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        clearSnapshot()
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
        val active = try { activeNotifications.orEmpty() } catch (_: SecurityException) { emptyArray() }
        notifications = active.map { sbn ->
            val appLabel = try {
                val info = packageManager.getApplicationInfo(sbn.packageName, 0)
                packageManager.getApplicationLabel(info).toString()
            } catch (_: Exception) {
                sbn.packageName
            }
            NotificationItem(
                key = sbn.key,
                packageName = sbn.packageName,
                appLabel = appLabel,
                appIcon = try { packageManager.getApplicationIcon(sbn.packageName) } catch (_: Exception) { null },
                title = sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                text = sbn.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
                postTime = sbn.postTime,
                contentIntent = sbn.notification.contentIntent,
                clearable = sbn.isClearable
            )
        }.sortedByDescending { it.postTime }

        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_NOTIFY_UPDATED))
    }

    fun dismissNotification(key: String) {
        cancelNotification(key)
    }

    override fun onDestroy() {
        clearSnapshot()
        super.onDestroy()
    }

    private fun clearSnapshot() {
        if (instance === this) instance = null
        notifications = emptyList()
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_NOTIFY_UPDATED))
    }
}
