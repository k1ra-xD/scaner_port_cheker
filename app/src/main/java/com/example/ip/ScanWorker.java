package com.example.ip;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ScanWorker extends Worker {

    public static final String KEY_FILE_URI = "fileUri";
    private static final String TAG = "ScanWorker";

    public ScanWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    private synchronized void appendLog(File logFile, String line) {
        try (FileWriter fw = new FileWriter(logFile, true);
             BufferedWriter bw = new BufferedWriter(fw)) {
            String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            bw.write(time + " " + line + "\n");
        } catch (Exception e) {
            Log.e(TAG, "Ошибка записи в лог: " + e.getMessage());
        }
    }

    @NonNull
    @Override
    public Result doWork() {
        File logFile = new File(getApplicationContext().getExternalFilesDir(null), "scan_log.txt");
        ExecutorService executor = null;

        try {
            setForegroundAsync(NotificationHelper.createForegroundInfo(
                    getApplicationContext(), "Подготовка к сканированию...", 0, 0));

            String fileUriStr = getInputData().getString(KEY_FILE_URI);
            if (fileUriStr == null || fileUriStr.isEmpty()) {
                appendLog(logFile, "❌ Не указан файл Excel");
                return Result.failure();
            }

            Uri uri = Uri.parse(fileUriStr);
            boolean checkPing = getInputData().getBoolean("checkPing", true);
            boolean checkHttp = getInputData().getBoolean("checkHttp", true);
            boolean checkSsh = getInputData().getBoolean("checkSsh", true);
            boolean checkModbus = getInputData().getBoolean("checkModbus", false);
            int startRow = getInputData().getInt("startRow", 1);

            // ✅ Берём IP + имя, если оно есть (ячейка слева)
            Map<String, String> ipNameMap = FileHelper.loadIpsWithNames(getApplicationContext(), uri, startRow);
            if (ipNameMap == null || ipNameMap.isEmpty()) {
                appendLog(logFile, "❌ Список IP пуст (лист 1)");
                return Result.failure();
            }

            // 📊 старые данные для diff (лист 0 = первый)
            Map<String, Map<String, String>> previousResults =
                    ExcelReader.readFile(getApplicationContext().getContentResolver(), uri, true, 0);

            int total = ipNameMap.size();
            appendLog(logFile, "▶ Сканирование начато (" + total + " IP)");
            int THREAD_COUNT = Math.max(8, Math.min(32, Runtime.getRuntime().availableProcessors() * 3));
            executor = Executors.newFixedThreadPool(THREAD_COUNT);

            Map<String, Map<String, String>> allResults = new ConcurrentHashMap<>();
            AtomicInteger done = new AtomicInteger(0);

            for (Map.Entry<String, String> entry : ipNameMap.entrySet()) {
                if (isStopped()) break;

                String ip = entry.getKey();
                String name = entry.getValue();

                executor.submit(() -> {
                    if (isStopped()) return;
                    try {
                        Map<String, String> res = new HashMap<>();
                        res.put("NAME", name); // 👈 имя попадёт в ExcelDiffManager
                        res.put("PING", checkPing ? PortScanner.scanPing(ip).get("PING") : "—");
                        res.put("HTTP", checkHttp ? PortScanner.scanHttp(ip).get("HTTP") : "—");
                        res.put("SSH", checkSsh ? PortScanner.scanSsh(ip).get("SSH") : "—");
                        res.put("Modbus", checkModbus ? PortScanner.scanModbus(ip).get("Modbus") : "—");

                        allResults.put(ip, res);
                        int current = done.incrementAndGet();

                        // лог без имени — чтобы не засорять
                        appendLog(logFile, String.format(
                                "[%d/%d] %s → PING=%s HTTP=%s SSH=%s Modbus=%s",
                                current, total, ip,
                                res.get("PING"), res.get("HTTP"), res.get("SSH"), res.get("Modbus")
                        ));
                        NotificationHelper.updateProgress(getApplicationContext(), current, total, ip);
                    } catch (Exception e) {
                        appendLog(logFile, "⚠️ Ошибка при проверке " + ip + ": " + e.getMessage());
                    }
                });
            }

            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.MINUTES);

            boolean ok = ExcelWriter.updateFile(getApplicationContext().getContentResolver(), uri, allResults);
            appendLog(logFile, ok ? "🧾 Результаты записаны" : "⚠️ Ошибка записи Excel");

            appendLog(logFile, "📊 Добавляю лист изменений...");
            ExcelDiffManager.writeDiffToSameFile(
                    getApplicationContext().getContentResolver(),
                    uri,
                    previousResults,
                    allResults
            );
            appendLog(logFile, "✅ Лист изменений добавлен");

            NotificationHelper.showCompletionNotification(getApplicationContext());
            appendLog(logFile, "🏁 Проверка завершена");
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "Ошибка ScanWorker", e);
            appendLog(logFile, "❌ Ошибка: " + e.getMessage());
            return Result.failure();
        } finally {
            if (executor != null) executor.shutdownNow();
        }
    }
}
