package com.example.ip;

import android.content.ContentResolver;
import android.net.Uri;
import android.util.Log;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ExcelWriter {
    private static final String TAG = "ExcelWriter";

    // ==================== XLSX ====================
    public static boolean writeResultsToXlsx(ContentResolver cr, Uri uri, Map<String, Map<String,String>> results) {
        XSSFWorkbook wb;
        try (InputStream in = cr.openInputStream(uri)) {
            wb = new XSSFWorkbook(in); // читаем и закрываем input
        } catch (Exception e) {
            Log.e(TAG, "open xlsx error", e);
            return false;
        }

        try {
            Sheet sheet = wb.getSheetAt(0);

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // пропускаем заголовок

                // IP в колонке F -> индекс 5
                Cell ipCell = row.getCell(5);
                if (ipCell == null) {
                    // ставим крестики, если хотим отмечать пустые IP? сейчас пропускаем
                    continue;
                }

                String ip = ipCell.toString().trim();
                if (ip.isEmpty()) continue;

                if (results.containsKey(ip)) {
                    Map<String,String> res = results.get(ip);
                    setCellValue(row, 6, res.getOrDefault("PING", "❌"));
                    setCellValue(row, 7, res.getOrDefault("HTTP", "❌"));
                    setCellValue(row, 8, res.getOrDefault("SSH", "❌"));
                    setCellValue(row, 9, res.getOrDefault("Modbus", "❌"));
                    Log.d(TAG, "Wrote results for " + ip);
                } else {
                    // если результата нет — ставим крестики
                    setCellValue(row, 6, "❌");
                    setCellValue(row, 7, "❌");
                    setCellValue(row, 8, "❌");
                    setCellValue(row, 9, "❌");
                    Log.d(TAG, "No scan result for " + ip + " -> filled ❌");
                }
            }

            // Запишем книгу обратно (через отдельный output)
            try (OutputStream out = cr.openOutputStream(uri)) {
                wb.write(out);
            }
            wb.close();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "writeResultsToXlsx", e);
            return false;
        }
    }

    private static void setCellValue(Row row, int colIndex, String value) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) cell = row.createCell(colIndex);
        cell.setCellValue(value);
    }

    // ==================== CSV ====================
    public static boolean writeResultsToCsv(ContentResolver cr, Uri uri, Map<String, Map<String,String>> results) {
        try {
            List<String[]> rows = new ArrayList<>();
            try (InputStream in = cr.openInputStream(uri);
                 BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    // простая разбивка, не учитывает кавычки — для простых CSV достаточно
                    rows.add(line.split(","));
                }
            }

            // Обеспечим заголовок минимум до 10 колонок
            if (!rows.isEmpty()) {
                String[] header = rows.get(0);
                if (header.length < 10) {
                    String[] newHeader = new String[10];
                    for (int i = 0; i < header.length; i++) newHeader[i] = header[i];
                    if (newHeader.length > 5) newHeader[5] = "IP";
                    if (newHeader.length > 6) newHeader[6] = "PING";
                    if (newHeader.length > 7) newHeader[7] = "HTTP";
                    if (newHeader.length > 8) newHeader[8] = "SSH";
                    if (newHeader.length > 9) newHeader[9] = "Modbus";
                    rows.set(0, newHeader);
                }
            }

            // обновляем данные: IP ожидаем в колонке 6 (индекс 5) — соответствие XLSX
            for (int i = 1; i < rows.size(); i++) {
                String[] row = rows.get(i);
                if (row.length < 6) continue;
                String ip = row[5].trim();
                if (ip.isEmpty()) continue;

                if (results.containsKey(ip)) {
                    Map<String,String> res = results.get(ip);
                    if (row.length < 10) {
                        String[] newRow = new String[10];
                        for (int j = 0; j < row.length; j++) newRow[j] = row[j];
                        row = newRow;
                        rows.set(i, row);
                    }
                    row[6] = res.getOrDefault("PING", "❌");
                    row[7] = res.getOrDefault("HTTP", "❌");
                    row[8] = res.getOrDefault("SSH", "❌");
                    row[9] = res.getOrDefault("Modbus", "❌");
                } else {
                    if (row.length < 10) {
                        String[] newRow = new String[10];
                        for (int j = 0; j < row.length; j++) newRow[j] = row[j];
                        row = newRow;
                        rows.set(i, row);
                    }
                    row[6] = "❌";
                    row[7] = "❌";
                    row[8] = "❌";
                    row[9] = "❌";
                }
            }

            // Перезапишем CSV обратно
            try (OutputStream out = cr.openOutputStream(uri);
                 BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
                for (String[] row : rows) {
                    // заменим null на пустую строку
                    for (int k = 0; k < row.length; k++) if (row[k] == null) row[k] = "";
                    bw.write(String.join(",", row));
                    bw.newLine();
                }
                bw.flush();
            }

            return true;
        } catch (Exception e) {
            Log.e(TAG, "writeResultsToCsv", e);
            return false;
        }
    }
}
