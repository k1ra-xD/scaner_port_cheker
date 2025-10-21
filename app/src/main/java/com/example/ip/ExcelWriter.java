package com.example.ip;

import android.content.ContentResolver;
import android.net.Uri;
import android.util.Log;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public class ExcelWriter {

    private static final String TAG = "ExcelWriter";

    public static boolean updateFile(ContentResolver cr, Uri uri, Map<String, Map<String, String>> results) {
        XSSFWorkbook wb = null;
        try (InputStream in = cr.openInputStream(uri)) {

            if (in == null) {
                Log.e(TAG, "Не удалось открыть файл для чтения");
                return false;
            }

            wb = new XSSFWorkbook(in);
            Sheet sheet = wb.getSheetAt(0);

            Row header = sheet.getRow(0);
            if (header == null) {
                Log.e(TAG, "Нет заголовков в таблице");
                return false;
            }

            int ipCol = findColumn(header, "IP");
            int pingCol = findColumn(header, "PING");
            int httpCol = findColumn(header, "HTTP");
            int sshCol = findColumn(header, "SSH");
            int modbusCol = findColumn(header, "Modbus");
            int changeCol = findColumn(header, "Изменения");

            String now = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(new Date());

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Cell ipCell = row.getCell(ipCol);
                if (ipCell == null) continue;
                String ip = ipCell.toString().trim();
                if (ip.isEmpty() || !results.containsKey(ip)) continue;

                Map<String, String> r = results.get(ip);

                setValue(row, pingCol, r.get("PING"));
                setValue(row, httpCol, r.get("HTTP"));
                setValue(row, sshCol, r.get("SSH"));
                setValue(row, modbusCol, r.get("Modbus"));
                setValue(row, changeCol, now);
            }

            try {
                for (int i = 0; i <= changeCol; i++) {
                    sheet.setColumnWidth(i, 20 * 256);
                }
            } catch (Exception ignore) {}

            try (OutputStream out = cr.openOutputStream(uri, "rwt")) {
                if (out == null) throw new Exception("Поток записи не открыт");
                wb.write(out);
                out.flush();
            }

            Log.i(TAG, "✅ Excel обновлён: " + uri);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Ошибка при записи в Excel: " + e.getMessage(), e);
            return false;
        } finally {
            try {
                if (wb != null) wb.close();
            } catch (Exception ignore) {}
        }
    }

    private static int findColumn(Row header, String name) {
        for (Cell c : header) {
            if (c.getStringCellValue().trim().equalsIgnoreCase(name))
                return c.getColumnIndex();
        }
        return -1;
    }

    private static void setValue(Row row, int col, String value) {
        if (col < 0) return;
        if (value == null) value = "—";
        Cell c = row.getCell(col);
        if (c == null) c = row.createCell(col);
        c.setCellValue(value);
    }
}
