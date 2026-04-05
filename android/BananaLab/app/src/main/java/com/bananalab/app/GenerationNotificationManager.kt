package com.bananalab.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlin.random.Random

object GenerationNotificationManager {
    private const val CHANNEL_ID = "banana_lab_generation_v2"
    private const val CHANNEL_NAME = "生成状态"
    private const val CHANNEL_DESCRIPTION = "显示生成成功和失败通知"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = CHANNEL_DESCRIPTION
            enableVibration(true)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun showSuccess(context: Context, message: String) {
        postNotification(
            context = context,
            title = "生成成功",
            message = message.ifBlank { "图片已生成完成。" },
        )
    }

    fun showFailure(context: Context, message: String) {
        postNotification(
            context = context,
            title = "生成失败",
            message = message.ifBlank { "生成过程中发生了错误。" },
        )
    }

    private fun postNotification(context: Context, title: String, message: String) {
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        // Use a unique ID so each generation result can alert independently.
        manager.notify(Random.nextInt(100_000, 999_999), buildNotification(context, title, message))
    }

    private fun buildNotification(context: Context, title: String, message: String): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentFlags(),
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(android.app.Notification.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun pendingIntentFlags(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
    }
}
