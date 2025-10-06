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
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ScanWorker extends Worker {

    public static final String KEY_FILE_URI = "fileUri";
    private static final String TAG = "ScanWorker";

    public ScanWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

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
        long startTime = System.currentTimeMillis();

        try {
            // ✅ создаём foreground уведомление сразу, чтобы Android не убил задачу
            setForegroundAsync(
                    NotificationHelper.createForegroundInfo(
                            getApplicationContext(),
                            "Подготовка к сканированию...",
                            0,
                            0
                    )
            );

            Uri uri = Uri.parse(getInputData().getString(KEY_FILE_URI));
            List<String> ips = FileHelper.loadIps(getApplicationContext(), uri);

            if (ips == null || ips.isEmpty()) {
                Log.w(TAG, "Список IP пуст!");
                return Result.failure();
            }

            int startRow = getInputData().getInt("startRow", 1);
            File logFile = new File(getApplicationContext().getExternalFilesDir(null), "scan_log.txt");

            int total = (startRow <= ips.size()) ? (ips.size() - startRow + 1) : 0;
            if (total <= 0) {
                appendLog(logFile, "❌ Нет IP-адресов для проверки (startRow > размер файла)");
                return Result.success();
            }

            try (BufferedWriter clear = new BufferedWriter(new FileWriter(logFile, false))) {
                clear.write("=== Начало проверки (" + total + " адресов, с " + startRow + " строки) ===\n");
                if (startRow > 1) clear.write("▶ Пропущено " + (startRow - 1) + " строк\n");
            }

            int THREAD_COUNT = 40;
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

            Map<String, Map<String, String>> allResults = new ConcurrentHashMap<>();
            AtomicInteger done = new AtomicInteger(0);

            Map<String, String> meta = new HashMap<>();
            meta.put("startRow", String.valueOf(startRow));
            allResults.put("__meta__", meta);

            // 🔥 повторно обновляем foreground уведомление перед основной загрузкой
            setForegroundAsync(
                    NotificationHelper.createForegroundInfo(
                            getApplicationContext(),
                            "Сканирование начато...",
                            0,
                            total
                    )
            );

            for (int i = 0; i < ips.size(); i++) {
                String ip = ips.get(i);
                if ((i + 1) < startRow) continue;

                executor.submit(() -> {
                    try {
                        Map<String, String> res = PortScanner.scanIpSync(ip);
                        allResults.put(ip, res);

                        int current = done.incrementAndGet();
                        appendLog(logFile, "[" + current + "/" + total + "] " + ip +
                                " -> PING=" + res.get("PING") +
                                " HTTP=" + res.get("HTTP") +
                                " SSH=" + res.get("SSH") +
                                " Modbus=" + res.get("Modbus"));

                        // 🔥 стабильно обновляем уведомление
                        NotificationHelper.updateProgress(getApplicationContext(), current, total, "Сканирую " + ip);
                        setProgressAsync(new Data.Builder().putBoolean("logUpdated", true).build());
                    } catch (Exception e) {
                        Log.e(TAG, "Ошибка при проверке " + ip, e);
                    }
                });
            }

            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.MINUTES);

            try {
                boolean ok = ExcelWriter.updateFile(
                        getApplicationContext().getContentResolver(),
                        uri,
                        allResults
                );
                if (!ok) {
                    Log.e(TAG, "Ошибка записи в Excel/CSV");
                    appendLog(logFile, "⚠️ Ошибка записи в Excel/CSV");
                } else {
                    appendLog(logFile, "✅ Результаты записаны в Excel");
                }
            } catch (Exception e) {
                Log.e(TAG, "ExcelWriter.updateFile упал: " + e.getMessage());
                appendLog(logFile, "⚠️ Ошибка ExcelWriter: " + e.getMessage());
            }

            appendLog(logFile, "=== Проверка завершена ===");

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

            // ✅ финальное уведомление
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
