package com.example.ip;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.ForegroundInfo;

public class NotificationHelper {

    public static final String CHANNEL_ID = "scan_channel";
    private static final int NOTIFICATION_ID = 4242;

    public static void createNotificationChannel(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel existing = manager.getNotificationChannel(CHANNEL_ID);
            if (existing != null) return;

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Сканирование IP",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Уведомления о процессе сканирования IP-адресов");
            manager.createNotificationChannel(channel);
        }
    }

    public static ForegroundInfo createForegroundInfo(Context context, String text, int done, int total) {
        createNotificationChannel(context);

        int progress = (total > 0) ? (done * 100 / total) : 0;

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Сканирование сети")
                .setContentText(text + (total > 0 ? " (" + done + "/" + total + ")" : ""))
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setProgress(100, progress, total == 0)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .build();

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

    public static void updateProgress(Context context, int done, int total, String ip) {
        createNotificationChannel(context);

        int progress = (total > 0) ? (done * 100 / total) : 0;
        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Сканирование сети")
                .setContentText("Сканирую: " + ip + " (" + done + "/" + total + ")")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setProgress(100, progress, total == 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        try {
            NotificationManagerCompat manager = NotificationManagerCompat.from(context);
            if (Build.VERSION.SDK_INT < 33 ||
                    ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                            == PackageManager.PERMISSION_GRANTED) {
                manager.notify(NOTIFICATION_ID, notification);
            }
        } catch (Exception ignored) {}
    }

    public static void showCompletionNotification(Context context) {
        createNotificationChannel(context);

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Сканирование завершено")
                .setContentText("✅ Все IP успешно проверены")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build();

        try {
            NotificationManagerCompat manager = NotificationManagerCompat.from(context);
            if (Build.VERSION.SDK_INT < 33 ||
                    ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                            == PackageManager.PERMISSION_GRANTED) {
                manager.notify(NOTIFICATION_ID + 1, notification);
            }
        } catch (Exception ignored) {}
    }

    public static void showPreRunNotification(Context context) {
        createNotificationChannel(context);

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Напоминание о проверке")
                .setContentText("Через 5 минут начнётся автоматическая проверка IP")
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build();

        try {
            NotificationManagerCompat manager = NotificationManagerCompat.from(context);
            if (Build.VERSION.SDK_INT < 33 ||
                    ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                            == PackageManager.PERMISSION_GRANTED) {
                manager.notify(NOTIFICATION_ID + 2, notification);
            }
        } catch (Exception ignored) {}
    }

    public static void showErrorNotification(Context context, String reason) {
        createNotificationChannel(context);

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Ошибка сканирования")
                .setContentText("❌ Скан не запущен: " + reason)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build();

        try {
            NotificationManagerCompat manager = NotificationManagerCompat.from(context);
            if (Build.VERSION.SDK_INT < 33 ||
                    ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                            == PackageManager.PERMISSION_GRANTED) {
                manager.notify(NOTIFICATION_ID + 3, notification);
            }
        } catch (Exception ignored) {}
    }

    public static void cancelForegroundNotification(Context context) {
        try {
            NotificationManager manager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.cancelAll();
                Log.i("NotificationHelper", "🧹 Все уведомления удалены");
            }
        } catch (Exception e) {
            Log.e("NotificationHelper", "Ошибка отмены уведомлений: " + e.getMessage());
        }
    }
}
