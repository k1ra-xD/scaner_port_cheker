package com.example.ip;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.ForegroundInfo;

public class NotificationHelper {
    public static final String CHANNEL_ID = "scan_channel";
    private static final int NOTIFICATION_ID = 4242;

    // Создание канала уведомлений (Android 8+)
    public static void createNotificationChannel(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Сканирование IP",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Уведомления о процессе сканирования");
            manager.createNotificationChannel(channel);
        }
    }

    // Foreground-уведомление с прогрессом
    public static ForegroundInfo createForegroundInfo(Context context, String text, int done, int total) {
        createNotificationChannel(context);

        int progress = (total > 0) ? (done * 100 / total) : 0;

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Сканирование сети")
                .setContentText(text + " (" + done + "/" + total + ")")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setProgress(100, progress, false)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW) // 📌 тихое уведомление
                .setOnlyAlertOnce(true)
                .build();

        return new ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        );
    }

    // Финальное уведомление
    public static void showCompletionNotification(Context context) {
        createNotificationChannel(context);

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Сканирование завершено")
                .setContentText("✅ Все IP были проверены")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT) // 📌 явное уведомление
                .setAutoCancel(true)
                .build();

        NotificationManagerCompat manager = NotificationManagerCompat.from(context);

        try {
            // 📌 Проверяем разрешение на Android 13+ (POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT < 33 || manager.areNotificationsEnabled()) {
                manager.notify(NOTIFICATION_ID + 1, notification);
            }
        } catch (SecurityException e) {
            e.printStackTrace(); // не упадём, если пользователь запретил уведомления
        }
    }
}
