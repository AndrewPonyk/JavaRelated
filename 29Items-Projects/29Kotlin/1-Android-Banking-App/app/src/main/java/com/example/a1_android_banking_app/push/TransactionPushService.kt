package com.example.a1_android_banking_app.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM entry point for transaction push notifications.
 * Requires app/google-services.json + the google-services plugin to actually receive
 * messages — see docs/TECH-NOTES.md 3.6 #3 for why the plugin is not applied yet.
 */
class TransactionPushService : FirebaseMessagingService() {

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Transaction notifications",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "Incoming and completed transfers" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title
            ?: message.data["title"]
            ?: return // data messages we don't understand stay silent
        val body = message.notification?.body ?: message.data["body"].orEmpty()

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            Log.w(TAG, "Dropping push notification — POST_NOTIFICATIONS not granted")
            return
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat) // replace with a branded icon in Phase 3
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    override fun onNewToken(token: String) {
        // TODO(Phase 2): POST the token to the backend registration endpoint so pushes
        //   reach this device (endpoint is scoped in PROJECT-PLAN Phase 2 — FCM row).
        Log.i(TAG, "FCM registration token rotated (registration endpoint lands in Phase 2)")
    }

    private companion object {
        const val TAG = "TransactionPush"
        const val CHANNEL_ID = "transactions"
        const val NOTIFICATION_ID = 1001
    }
}
