package com.example.ip;

import android.content.ContentResolver;
import android.net.Uri;
import android.util.Log;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ExcelWriter {
    private static final String TAG = "ExcelWriter";

    // Универсальный метод
    public static boolean updateFile(ContentResolver cr, Uri uri, Map<String, Map<String,String>> results) {
        String path = uri.toString().toLowerCase();
        if (path.endsWith(".xlsx")) {
            return writeResultsToXlsx(cr, uri, results);
        } else {
            return writeResultsToCsv(cr, uri, results);
        }
    }

    // ==================== XLSX ====================
    public static boolean writeResultsToXlsx(ContentResolver cr, Uri uri, Map<String, Map<String,String>> results) {
        try {
            XSSFWorkbook wb;
            try (InputStream in = cr.openInputStream(uri)) {
                wb = new XSSFWorkbook(in);
            } catch (Exception e) {
                // Если файл пустой — создаём новый
                wb = new XSSFWorkbook();
                Sheet sheet = wb.createSheet("Sheet1");
                Row header = sheet.createRow(0);
                header.createCell(0).setCellValue("NAME");
                header.createCell(1).setCellValue("IP");
                header.createCell(2).setCellValue("PING");
                header.createCell(3).setCellValue("HTTP");
                header.createCell(4).setCellValue("HTTPS");
                header.createCell(5).setCellValue("SSH");
                header.createCell(6).setCellValue("Modbus");
            }

            Sheet sheet = wb.getSheetAt(0);

            // ==== ищем колонки по заголовкам ====
            Row header = sheet.getRow(0);
            if (header == null) {
                Log.e(TAG, "Файл без заголовка");
                return false;
            }

            Map<String,Integer> colIndex = new HashMap<>();
            for (Cell c : header) {
                String name = c.getStringCellValue().trim().toLowerCase();
                colIndex.put(name, c.getColumnIndex());
            }

            Integer ipCol = colIndex.get("ip");
            if (ipCol == null) {
                Log.e(TAG, "Не найдена колонка IP");
                return false;
            }

            // ==== получаем startRow (если есть) ====
            int startRow = 1;
            if (results.containsKey("__meta__")) {
                try {
                    startRow = Integer.parseInt(results.get("__meta__").get("startRow"));
                } catch (Exception ignore) {}
            }

            // ==== обновляем строки ====
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // пропускаем заголовок
                if (row.getRowNum() < startRow) continue; // 🔥 пропускаем до startRow

                Cell ipCell = row.getCell(ipCol);
                if (ipCell == null) continue;

                String ip = ipCell.toString().trim();
                if (results.containsKey(ip)) {
                    Map<String,String> res = results.get(ip);

                    if (colIndex.containsKey("ping"))
                        setCellValue(row, colIndex.get("ping"), res.get("PING"));
                    if (colIndex.containsKey("http"))
                        setCellValue(row, colIndex.get("http"), res.get("HTTP"));
                    if (colIndex.containsKey("https"))
                        setCellValue(row, colIndex.get("https"), res.get("HTTPS"));
                    if (colIndex.containsKey("ssh"))
                        setCellValue(row, colIndex.get("ssh"), res.get("SSH"));
                    if (colIndex.containsKey("modbus"))
                        setCellValue(row, colIndex.get("modbus"), res.get("Modbus"));
                }
            }

            try (OutputStream out = cr.openOutputStream(uri, "rwt")) {
                wb.write(out);
            }
            wb.close();
            return true;

        } catch (Exception e) {
            Log.e(TAG, "writeResultsToXlsx error", e);
            return false;
        }
    }

    private static void setCellValue(Row row, int colIndex, String value) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) cell = row.createCell(colIndex);

        // ✅ только галочка или крестик
        if (value == null || value.isEmpty()) {
            cell.setCellValue("");
        } else if (value.contains("❌") || value.toLowerCase().contains("закрыт")) {
            cell.setCellValue("❌");
        } else {
            cell.setCellValue("✅");
        }
    }

    // ==================== CSV ====================
    public static boolean writeResultsToCsv(ContentResolver cr, Uri uri, Map<String, Map<String,String>> results) {
        try {
            List<String[]> rows = new ArrayList<>();

            try (InputStream in = cr.openInputStream(uri);
                 BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    rows.add(line.split(","));
                }
            } catch (Exception e) {
                rows.add(new String[]{"NAME","IP","PING","HTTP","HTTPS","SSH","Modbus"});
            }

            // ==== ищем колонки по заголовкам ====
            Map<String,Integer> colIndex = new HashMap<>();
            if (!rows.isEmpty()) {
                String[] header = rows.get(0);
                for (int i = 0; i < header.length; i++) {
                    colIndex.put(header[i].trim().toLowerCase(), i);
                }
            }

            Integer ipCol = colIndex.get("ip");
            if (ipCol == null) return false;

            // ==== получаем startRow ====
            int startRow = 1;
            if (results.containsKey("__meta__")) {
                try {
                    startRow = Integer.parseInt(results.get("__meta__").get("startRow"));
                } catch (Exception ignore) {}
            }

            for (int i = 1; i < rows.size(); i++) {
                if (i < startRow) continue; // 🔥 пропускаем до startRow

                String[] row = rows.get(i);
                if (row.length <= ipCol) continue;

                String ip = row[ipCol].trim();
                if (results.containsKey(ip)) {
                    Map<String,String> res = results.get(ip);

                    if (colIndex.containsKey("ping")) row[colIndex.get("ping")] = normalize(res.get("PING"));
                    if (colIndex.containsKey("http")) row[colIndex.get("http")] = normalize(res.get("HTTP"));
                    if (colIndex.containsKey("https")) row[colIndex.get("https")] = normalize(res.get("HTTPS"));
                    if (colIndex.containsKey("ssh")) row[colIndex.get("ssh")] = normalize(res.get("SSH"));
                    if (colIndex.containsKey("modbus")) row[colIndex.get("modbus")] = normalize(res.get("Modbus"));
                }
            }

            try (OutputStream out = cr.openOutputStream(uri, "rwt");
                 BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
                for (String[] row : rows) {
                    bw.write(String.join(",", row));
                    bw.newLine();
                }
                bw.flush();
            }

            return true;
        } catch (Exception e) {
            Log.e(TAG, "writeResultsToCsv error", e);
            return false;
        }
    }

    // 🔥 для CSV — тоже нормализуем
    private static String normalize(String value) {
        if (value == null || value.isEmpty()) return "";
        if (value.contains("❌") || value.toLowerCase().contains("закрыт")) return "❌";
        return "✅";
    }
}
