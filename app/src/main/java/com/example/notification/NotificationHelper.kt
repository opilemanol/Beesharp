package com.example.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log

object NotificationHelper {
    private const val ALARM_REQ_CODE = 4040

    fun schedule12HourReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, NotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQ_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 12 hours in milliseconds = 12 * 60 * 60 * 1000 = 43,200,000 ms
        val intervalMillis = 12 * 60 * 60 * 1000L
        val triggerAtMillis = System.currentTimeMillis() + intervalMillis

        try {
            // Cancel any existing one first
            alarmManager.cancel(pendingIntent)
            
            // Set inexact repeating alarm (battery-friendly & perfect for user reminders)
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                intervalMillis,
                pendingIntent
            )
            Log.d("NotificationHelper", "Successfully scheduled repeating 12-hour reminder alarm.")
        } catch (e: Exception) {
            Log.e("NotificationHelper", "Error scheduling alarm", e)
        }
    }

    // Direct utility to trigger a test notification in 5 seconds
    fun scheduleInstantTestReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, NotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQ_CODE + 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 5000L, // 5 seconds from now
                pendingIntent
            )
            Log.d("NotificationHelper", "Scheduled instant test reminder in 5 seconds.")
        } catch (e: Exception) {
            Log.e("NotificationHelper", "Error scheduling test alarm", e)
        }
    }
}
