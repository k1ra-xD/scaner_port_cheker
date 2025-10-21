package com.example.ip;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.InputStream;
import java.util.*;

public class ExcelReader {

    private static final String TAG = "ExcelReader";

    /**
     * 📘 Читает Sheet1/Лист1/Main (или первый), пишет статус в tvOutput.
     */
    public static Map<String, Map<String, String>> readFile(
            Context context, ContentResolver cr, Uri uri, TextView tvOutput) {

        Map<String, Map<String, String>> data = new HashMap<>();
        try (InputStream in = cr.openInputStream(uri)) {
            if (in == null) return data;

            XSSFWorkbook wb = new XSSFWorkbook(in);
            Sheet targetSheet = null;

            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                String name = wb.getSheetName(i).trim().toLowerCase(Locale.ROOT);
                if (name.equals("sheet1") || name.equals("лист1") || name.equals("main")) {
                    targetSheet = wb.getSheetAt(i);
                    break;
                }
            }

            if (targetSheet == null) targetSheet = wb.getSheetAt(0);

            String selectedName = targetSheet.getSheetName();
            Log.i(TAG, "📄 Используется лист: " + selectedName);

            String msg = "📄 Используется лист: " + selectedName + "\n";
            new Handler(Looper.getMainLooper()).post(() -> tvOutput.append(msg));

            Row header = targetSheet.getRow(0);
            if (header == null) {
                wb.close();
                return data;
            }

            Map<String, Integer> col = new HashMap<>();
            for (Cell c : header)
                col.put(c.getStringCellValue().trim().toLowerCase(Locale.ROOT), c.getColumnIndex());

            int ipCol = col.getOrDefault("ip", 0);
            int pingCol = col.getOrDefault("ping", 1);
            int httpCol = col.getOrDefault("http", 2);
            int sshCol = col.getOrDefault("ssh", 3);
            int modbusCol = col.getOrDefault("modbus", 4);

            for (int i = 1; i <= targetSheet.getLastRowNum(); i++) {
                Row r = targetSheet.getRow(i);
                if (r == null) continue;
                Cell ipCell = r.getCell(ipCol);
                if (ipCell == null) continue;
                String ip = ipCell.toString().trim();
                if (ip.isEmpty()) continue;

                Map<String, String> row = new HashMap<>();
                row.put("PING", getCell(r, pingCol));
                row.put("HTTP", getCell(r, httpCol));
                row.put("SSH", getCell(r, sshCol));
                row.put("Modbus", getCell(r, modbusCol));
                data.put(ip, row);
            }

            wb.close();
            Log.i(TAG, "✅ Прочитано IP: " + data.size() + " из листа " + selectedName);

            String countMsg = "✅ Прочитано IP: " + data.size() + " из листа " + selectedName + "\n";
            new Handler(Looper.getMainLooper()).post(() -> tvOutput.append(countMsg));

        } catch (Exception e) {
            Log.e(TAG, "Ошибка чтения Excel: " + e.getMessage(), e);
            new Handler(Looper.getMainLooper()).post(() ->
                    tvOutput.append("❌ Ошибка чтения Excel: " + e.getMessage() + "\n"));
        }
        return data;
    }

    private static String getCell(Row r, int col) {
        try {
            Cell c = r.getCell(col);
            if (c == null) return "—";
            c.setCellType(CellType.STRING);
            return c.getStringCellValue().trim();
        } catch (Exception e) {
            return "—";
        }
    }

    // ✅ Перегрузка для фонового чтения из конкретного листа по индексу
    public static Map<String, Map<String, String>> readFile(
            ContentResolver cr, Uri uri, boolean ignoreUi, int sheetIndex) {
        Map<String, Map<String, String>> data = new HashMap<>();
        try (InputStream in = cr.openInputStream(uri)) {
            if (in == null) return data;

            XSSFWorkbook wb = new XSSFWorkbook(in);

            int n = wb.getNumberOfSheets();
            if (n == 0) {
                wb.close();
                return data;
            }
            int idx = (sheetIndex >= 0 && sheetIndex < n) ? sheetIndex : 0;

            Sheet sheet = wb.getSheetAt(idx);
            Row header = sheet.getRow(0);
            if (header == null) {
                wb.close();
                return data;
            }

            Map<String, Integer> col = new HashMap<>();
            for (Cell c : header)
                col.put(c.getStringCellValue().trim().toLowerCase(Locale.ROOT), c.getColumnIndex());

            int ipCol = col.getOrDefault("ip", 0);
            int pingCol = col.getOrDefault("ping", 1);
            int httpCol = col.getOrDefault("http", 2);
            int sshCol = col.getOrDefault("ssh", 3);
            int modbusCol = col.getOrDefault("modbus", 4);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row r = sheet.getRow(i);
                if (r == null) continue;
                Cell ipCell = r.getCell(ipCol);
                if (ipCell == null) continue;
                String ip = ipCell.toString().trim();
                if (ip.isEmpty()) continue;

                Map<String, String> row = new HashMap<>();
                row.put("PING", getCell(r, pingCol));
                row.put("HTTP", getCell(r, httpCol));
                row.put("SSH", getCell(r, sshCol));
                row.put("Modbus", getCell(r, modbusCol));
                data.put(ip, row);
            }

            wb.close();
            Log.i(TAG, "✅ (BG) Прочитано IP: " + data.size() + " из листа #" + idx);

        } catch (Exception e) {
            Log.e(TAG, "Ошибка чтения Excel (background): " + e.getMessage(), e);
        }
        return data;
    }
}
