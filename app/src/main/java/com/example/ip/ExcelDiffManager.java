package com.example.ip;

import android.content.ContentResolver;
import android.net.Uri;
import android.util.Log;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * ExcelDiffManager — добавляет в тот же Excel новый лист "Изменения N"
 * с различиями между предыдущими и новыми результатами.
 * Теперь корректно ищет колонку с "Название" или "Name" по заголовку.
 */
public class ExcelDiffManager {

    private static final String TAG = "ExcelDiffManager";

    public static void writeDiffToSameFile(
            ContentResolver cr,
            Uri uri,
            Map<String, Map<String, String>> oldData,
            Map<String, Map<String, String>> newData
    ) {
        try (InputStream in = cr.openInputStream(uri)) {
            if (in == null) {
                Log.e(TAG, "Не удалось открыть Excel");
                return;
            }

            XSSFWorkbook wb = new XSSFWorkbook(in);

            // 🟢 Считываем имена из первого листа (ищем колонку "Название" или "Name")
            Map<String, String> nameMap = new HashMap<>();
            try {
                Sheet firstSheet = wb.getSheetAt(0);
                if (firstSheet != null) {
                    Row headerRow = firstSheet.getRow(0);
                    int nameCol = -1;
                    int ipCol = -1;

                    if (headerRow != null) {
                        for (Cell c : headerRow) {
                            String text = c.toString().trim().toLowerCase(Locale.ROOT);
                            if (text.contains("название") || text.contains("name"))
                                nameCol = c.getColumnIndex();
                            if (text.contains("ip") || text.contains("адрес") || text.contains("address"))
                                ipCol = c.getColumnIndex();
                        }
                    }

                    // если не нашли IP колонку — пусть будет вторая по умолчанию
                    if (ipCol == -1) ipCol = 1;

                    for (Row row : firstSheet) {
                        if (row == null || row.getRowNum() == 0) continue;

                        Cell ipCell = row.getCell(ipCol);
                        String ip = ipCell != null ? ipCell.toString().trim() : "";
                        if (ip.isEmpty()) continue;

                        String name = "—";
                        if (nameCol != -1) {
                            Cell nameCell = row.getCell(nameCol);
                            if (nameCell != null) {
                                name = nameCell.toString().trim();
                                if (name.isEmpty()) name = "—";
                            }
                        }

                        nameMap.put(ip, name);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "⚠️ Не удалось считать названия: " + e.getMessage());
            }

            // 🟣 Если в новых данных (из ScanWorker) есть NAME — он заменяет Excel-имя
            for (Map.Entry<String, Map<String, String>> entry : newData.entrySet()) {
                String ip = entry.getKey();
                Map<String, String> row = entry.getValue();
                if (row != null && row.containsKey("NAME")) {
                    String nm = row.get("NAME");
                    if (nm != null && !nm.trim().isEmpty()) {
                        nameMap.put(ip, nm.trim());
                    }
                }
            }

            // 🧾 создаём новый лист "Изменения N"
            int changeIndex = 1;
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                if (wb.getSheetName(i).startsWith("Изменения")) changeIndex++;
            }

            Sheet diff = wb.createSheet("Изменения " + changeIndex);
            Row header = diff.createRow(0);
            header.createCell(0).setCellValue("Имя");
            header.createCell(1).setCellValue("IP");
            header.createCell(2).setCellValue("Поле");
            header.createCell(3).setCellValue("Старое");
            header.createCell(4).setCellValue("Новое (время)");

            String now = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(new Date());
            int rowIndex = 1;
            String[] fields = {"PING", "HTTP", "SSH", "Modbus"};

            for (String ip : newData.keySet()) {
                Map<String, String> oldRow = oldData.get(ip);
                Map<String, String> newRow = newData.get(ip);
                if (newRow == null) continue;

                String name = nameMap.getOrDefault(ip, "—");

                for (String key : fields) {
                    String oldVal = oldRow != null ? oldRow.getOrDefault(key, "—") : "—";
                    String newVal = newRow.getOrDefault(key, "—");
                    if (!Objects.equals(oldVal, newVal)) {
                        Row r = diff.createRow(rowIndex++);
                        r.createCell(0).setCellValue(name);
                        r.createCell(1).setCellValue(ip);
                        r.createCell(2).setCellValue(key);
                        r.createCell(3).setCellValue(oldVal);
                        r.createCell(4).setCellValue(newVal + " (" + now + ")");
                    }
                }
            }

            for (int i = 0; i <= 4; i++) diff.setColumnWidth(i, 22 * 256);

            // 💾 сохраняем изменения в тот же файл
            try (OutputStream out = cr.openOutputStream(uri, "rwt")) {
                wb.write(out);
                out.flush();
            }

            wb.close();
            Log.i(TAG, "✅ Добавлен лист Изменения " + changeIndex + " (с названиями по заголовку)");

        } catch (Exception e) {
            Log.e(TAG, "Ошибка добавления листа: " + e.getMessage(), e);
        }
    }
}
