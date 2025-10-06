package com.example.ip;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.ForegroundInfo;

public class NotificationHelper {
    public static final String CHANNEL_ID = "scan_channel";
    private static final int NOTIFICATION_ID = 4242;

    // Создаём канал уведомлений (обязательно для Android 8+)
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

    // 🔥 Foreground-уведомление с прогрессом (живое, обновляемое)
    public static ForegroundInfo createForegroundInfo(Context context, String text, int done, int total) {
        createNotificationChannel(context);

        int progress = (total > 0) ? (done * 100 / total) : 0;

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Сканирование сети")
                .setContentText(text + " (" + done + "/" + total + ")")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setProgress(100, progress, total == 0)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .build();

        // ⚙️ Для Android 14 нужен тип FOREGROUND_SERVICE_TYPE_DATA_SYNC
        if (Build.VERSION.SDK_INT >= 34) {
            return new ForegroundInfo(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            );
        } else {
            return new ForegroundInfo(NOTIFICATION_ID, notification);
        }
    }

    // 🔄 Обновление уведомления при каждом IP без мерцания
    public static void updateProgress(Context context, int done, int total, String ip) {
        createNotificationChannel(context);

        int progress = (total > 0) ? (done * 100 / total) : 0;

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Сканирование сети")
                .setContentText("Сканирую: " + ip + " (" + done + "/" + total + ")")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setProgress(100, progress, total == 0)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .build();

        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        try {
            if (Build.VERSION.SDK_INT < 33 ||
                    ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                            == PackageManager.PERMISSION_GRANTED) {
                manager.notify(NOTIFICATION_ID, notification);
            }
        } catch (SecurityException ignored) {
        }
    }

    // ✅ Финальное уведомление после завершения сканирования
    public static void showCompletionNotification(Context context) {
        createNotificationChannel(context);

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Сканирование завершено")
                .setContentText("✅ Все IP проверены")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build();

        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        try {
            if (Build.VERSION.SDK_INT < 33 ||
                    ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                            == PackageManager.PERMISSION_GRANTED) {
                manager.notify(NOTIFICATION_ID + 1, notification);
            }
        } catch (SecurityException ignored) {
        }
    }
}
