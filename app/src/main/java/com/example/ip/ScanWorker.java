package com.example.ip;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.List;
import java.util.Map;

public class ScanWorker extends Worker {

    public static final String KEY_FILE_URI = "fileUri";

    public ScanWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    private void appendLog(File logFile, String line) {
        try (FileWriter fw = new FileWriter(logFile, true);
             BufferedWriter bw = new BufferedWriter(fw)) {
            bw.write(line + "\n");
        } catch (Exception ignored) {}
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Uri uri = Uri.parse(getInputData().getString(KEY_FILE_URI));
            List<String> ips = FileHelper.loadIps(getApplicationContext(), uri);

            File logFile = new File(getApplicationContext().getExternalFilesDir(null), "scan_log.txt");
            try (BufferedWriter clear = new BufferedWriter(new FileWriter(logFile, false))) {
                clear.write("=== Начало проверки ===\n");
            }

            int total = ips.size();
            int done = 0;

            for (String ip : ips) {
                done++;
                setForegroundAsync(createForegroundInfo("Сканирую " + ip, done, total));

                Map<String, String> res = PortScanner.scanIpSync(ip);
                String logLine = ip + " -> PING=" + res.get("PING") +
                        " HTTP=" + res.get("HTTP") +
                        " SSH=" + res.get("SSH") +
                        " Modbus=" + res.get("Modbus");
                appendLog(logFile, logLine);

                // запись в Excel/CSV
                ExcelWriter.updateFile(getApplicationContext(), uri, ip, res);

                setProgressAsync(new Data.Builder().putBoolean("logUpdated", true).build());
            }

            appendLog(logFile, "=== Проверка завершена ===");
            setForegroundAsync(createForegroundInfo("✅ Проверка завершена", total, total));

            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Result.failure();
        }
    }

    private ForegroundInfo createForegroundInfo(String text, int done, int total) {
        return NotificationHelper.createForegroundInfo(getApplicationContext(), text, done, total);
    }
}
