package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("NotificationReceiver", "Received broadcast for 12-hour reminder.")
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "beesharp_mastery_reminders"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Spelling Mastery Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders to test your mastery of English Words in BeeSharp"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val titles = listOf(
            "🐝 Spelling Bee Time!",
            "🧠 Boost Your Vocabulary!",
            "✨ Test Your Mastery!",
            "🏆 Show your Spelling Power!"
        )
        val messages = listOf(
            "Ready to spell some words and keep your streak alive in BeeSharp?",
            "It's been 12 hours! Perfect time to master new words.",
            "Can you spell today's challenge word perfectly? Open BeeSharp now!",
            "Keep learning and expanding your vocabulary with BeeSharp!"
        )

        val title = titles.random()
        val message = messages.random()

        // Use our app foreground drawable as the small icon
        val smallIcon = try {
            com.example.R.drawable.ic_launcher_foreground
        } catch (e: Exception) {
            android.R.drawable.ic_dialog_info
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, notification)
    }
}
