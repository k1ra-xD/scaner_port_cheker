package com.example.ip;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public class JsonAnalyzer {

    private static final String TAG = "JsonAnalyzer";

    public static void analyze(Context context, Map<String, Map<String, String>> newResults) {
        File dir = context.getExternalFilesDir(null);
        if (dir == null) {
            Log.e(TAG, "Не удалось получить externalFilesDir");
            return;
        }
        if (!dir.exists()) dir.mkdirs();

        File oldJsonFile = new File(dir, "results.json");
        File reportFile = new File(dir, "changes_" + getDate() + ".txt");

        try {
            JSONObject oldData = new JSONObject();
            if (oldJsonFile.exists() && oldJsonFile.length() > 2) {
                try {
                    oldData = new JSONObject(readFile(oldJsonFile));
                } catch (Exception ex) {
                    Log.w(TAG, "⚠️ Старый JSON повреждён, создаётся заново");
                    oldData = new JSONObject();
                }
            }

            StringBuilder report = new StringBuilder();
            int changes = 0;
            int newCount = 0;

            for (String ip : newResults.keySet()) {
                if ("__meta__".equals(ip)) continue;

                JSONObject newObj = new JSONObject(newResults.get(ip));
                JSONObject oldObj = oldData.optJSONObject(ip);

                if (oldObj == null) {
                    report.append("🆕 Новый IP: ").append(ip).append("\n");
                    newCount++;
                    continue;
                }

                for (String key : newResults.get(ip).keySet()) {
                    String newVal = newResults.get(ip).get(key);
                    String oldVal = oldObj.optString(key, "");
                    if (!newVal.equals(oldVal)) {
                        report.append("⚠️ ").append(ip)
                                .append(" — ").append(key)
                                .append(": ").append(oldVal).append(" → ").append(newVal)
                                .append("\n");
                        changes++;
                    }
                }
            }

            try (OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(reportFile, false), StandardCharsets.UTF_8)) {
                writer.write("=== Отчёт об изменениях — " + getDateTime() + " ===\n");
                if (changes > 0 || newCount > 0) {
                    if (newCount > 0) writer.write("Добавлено новых IP: " + newCount + "\n");
                    if (changes > 0) writer.write("Изменений параметров: " + changes + "\n\n");
                    writer.write(report.toString());
                    writer.write("\n=== Конец отчёта ===\n");
                    Log.d(TAG, "📊 Найдено изменений: " + changes + " (новых: " + newCount + ")");
                } else {
                    writer.write("✅ Изменений не найдено\n");
                    Log.d(TAG, "✅ Изменений не найдено");
                }
            }

            JSONObject newJson = new JSONObject(newResults);
            try (OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(oldJsonFile, false), StandardCharsets.UTF_8)) {
                writer.write(newJson.toString(2));
            }

        } catch (Exception e) {
            Log.e(TAG, "Ошибка анализа JSON: " + e.getMessage(), e);
        }
    }

    private static String readFile(File file) throws IOException {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    private static String getDate() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    private static String getDateTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
    }
}
