package com.example.ip;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

public class ScanReceiver extends BroadcastReceiver {

    private static final String CH_ID = "auto_scan_channel";
    private static final int NID_INFO = 2001;

    @Override
    public void onReceive(Context context, Intent intent) {
        boolean isPreNotify = intent != null && intent.getBooleanExtra("notify", false);

        if (isPreNotify) {
            boolean connected = WifiUtils.isConnectedToTargetWifi(context);
            showInfo(context,
                    connected
                            ? "Через 5 мин автопроверка (Wi-Fi подключён)"
                            : "Через 5 мин автопроверка (Wi-Fi не подключён)");
            appendLog(context, "⏰ Напоминание: автопроверка через 5 мин\n");
            return;
        }

        boolean connected = WifiUtils.isConnectedToTargetWifi(context);
        if (!connected) {
            showInfo(context, "⚠️ Автопроверка пропущена — не подключено к нужному Wi-Fi");
            appendLog(context, "⚠️ Автопроверка пропущена — не тот Wi-Fi\n");
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences("ipscanner_prefs", Context.MODE_PRIVATE);
        String fileUriStr = prefs.getString("last_file_uri", null);
        boolean checkPing = prefs.getBoolean("checkPing", true);
        boolean checkHttp = prefs.getBoolean("checkHttp", true);
        boolean checkSsh = prefs.getBoolean("checkSsh", true);
        boolean checkModbus = prefs.getBoolean("checkModbus", false);
        int startRow = prefs.getInt("startRow", 1);

        if (fileUriStr == null || fileUriStr.isEmpty()) {
            showInfo(context, "⚠️ Автопроверка пропущена — не выбран файл Excel/CSV");
            appendLog(context, "⚠️ Автопроверка пропущена — не выбран файл\n");
            return;
        }

        Data input = new Data.Builder()
                .putString(ScanWorker.KEY_FILE_URI, fileUriStr)
                .putInt("startRow", startRow)
                .putBoolean("checkPing", checkPing)
                .putBoolean("checkHttp", checkHttp)
                .putBoolean("checkSsh", checkSsh)
                .putBoolean("checkModbus", checkModbus)
                .build();

        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(ScanWorker.class)
                .setInputData(input)
                .build();

        WorkManager.getInstance(context).enqueue(work);

        showInfo(context, "🔍 Автоматическая проверка началась");
        appendLog(context, "🔍 Автоматическая проверка началась\n");

        WorkManager.getInstance(context).getWorkInfoByIdLiveData(work.getId())
                .observeForever(workInfo -> {
                    if (workInfo != null && workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                        showInfo(context, "✅ Автопроверка завершена");
                        appendLog(context, "✅ Автопроверка завершена\n");
                    } else if (workInfo != null && workInfo.getState().isFinished()) {
                        showInfo(context, "⚠️ Автопроверка завершена с ошибками");
                        appendLog(context, "⚠️ Автопроверка завершена с ошибками\n");
                    }
                });
    }

    private void appendLog(Context context, String text) {
        try {
            File logFile = new File(context.getExternalFilesDir(null), "scan_log.txt");
            try (FileWriter fw = new FileWriter(logFile, true);
                 BufferedWriter bw = new BufferedWriter(fw)) {
                bw.write(text);
            }
        } catch (Exception ignored) {}
    }

    private void showInfo(Context ctx, String text) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CH_ID, "IP Scanner Автопроверка", NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(ch);
        }

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CH_ID)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("IP Scanner")
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true);

        nm.notify((int) System.currentTimeMillis(), b.build());
    }
}
