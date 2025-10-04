package com.example.ip;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScanWorker extends Worker {

    public static final String KEY_FILE_URI = "fileUri";
    private static final String TAG = "ScanWorker";
    public static final String KEY_START_ROW = "startRow";

    public ScanWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    // запись строки в лог
    private void appendLog(File logFile, String line) {
        try (FileWriter fw = new FileWriter(logFile, true);
             BufferedWriter bw = new BufferedWriter(fw)) {
            bw.write(line + "\n");
        } catch (Exception e) {
            Log.e(TAG, "Ошибка записи в лог: " + e.getMessage());
        }
    }

    @NonNull
    @Override
    public Result doWork() {
        long startTime = System.currentTimeMillis(); // 🚀 замер времени старта

        try {
            Uri uri = Uri.parse(getInputData().getString(KEY_FILE_URI));
            int startRow = getInputData().getInt(KEY_START_ROW, 1);
            List<String> ips = FileHelper.loadIps(getApplicationContext(), uri, startRow);

            if (ips == null || ips.isEmpty()) {
                Log.w(TAG, "Список IP пуст!");
                return Result.failure();
            }

            File logFile = new File(getApplicationContext().getExternalFilesDir(null), "scan_log.txt");

            // 🔥 очищаем лог и пишем заголовок
            try (BufferedWriter clear = new BufferedWriter(new FileWriter(logFile, false))) {
                clear.write("=== Начало проверки (" + ips.size() + " адресов, старт с строки " + startRow + ") ===\n");
            }

            int total = ips.size();
            int done = 0;

            // собираем все результаты
            Map<String, Map<String, String>> allResults = new HashMap<>();

            for (String ip : ips) {
                done++;

                // foreground уведомление
                setForegroundAsync(createForegroundInfo("Сканирую " + ip, done, total));

                // сканируем
                Map<String, String> res = PortScanner.scanIpSync(ip);
                allResults.put(ip, res);

                // логируем
                String logLine = "[" + done + "/" + total + "] " + ip +
                        " -> PING=" + res.get("PING") +
                        " HTTP=" + res.get("HTTP") +
                        " SSH=" + res.get("SSH") +
                        " Modbus=" + res.get("Modbus");
                appendLog(logFile, logLine);

                // сообщаем UI
                setProgressAsync(new Data.Builder().putBoolean("logUpdated", true).build());
            }

            // итоговая запись в Excel/CSV (одним разом)
            try {
                boolean ok = ExcelWriter.updateFile(
                        getApplicationContext().getContentResolver(),
                        uri,
                        allResults
                );
                if (!ok) {
                    Log.e(TAG, "Ошибка записи в Excel/CSV");
                }
            } catch (Exception e) {
                Log.e(TAG, "ExcelWriter.updateFile упал: " + e.getMessage());
            }

            // финальная строка в лог
            appendLog(logFile, "=== Проверка завершена ===");

            // ⏱️ добавляем итоговое время
            long elapsed = System.currentTimeMillis() - startTime;
            String elapsedStr;
            if (elapsed > 3600000) {
                elapsedStr = String.format("Время выполнения: %d ч %d мин %d сек",
                        elapsed / 3600000,
                        (elapsed % 3600000) / 60000,
                        (elapsed % 60000) / 1000);
            } else if (elapsed > 60000) {
                elapsedStr = String.format("Время выполнения: %d мин %d сек",
                        elapsed / 60000,
                        (elapsed % 60000) / 1000);
            } else {
                elapsedStr = String.format("Время выполнения: %d сек", elapsed / 1000);
            }
            appendLog(logFile, elapsedStr);

            // уведомление в статус-баре
            NotificationHelper.showCompletionNotification(getApplicationContext());

            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "doWork exception", e);
            return Result.failure();
        }
    }

    private ForegroundInfo createForegroundInfo(String text, int done, int total) {
        return NotificationHelper.createForegroundInfo(getApplicationContext(), text, done, total);
    }
}
