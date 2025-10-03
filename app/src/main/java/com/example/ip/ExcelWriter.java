package com.example.ip;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

public class ExcelWriter {

    public static void updateFile(Context ctx, Uri uri, String ip, Map<String, String> results) {
        try (InputStream is = ctx.getContentResolver().openInputStream(uri);
             Workbook wb = new XSSFWorkbook(is)) {

            Sheet sheet = wb.getSheetAt(0);

            for (Row row : sheet) {
                for (Cell cell : row) {
                    if (cell.toString().trim().equals(ip)) {
                        setCell(row, 2, results.get("PING"));
                        setCell(row, 3, results.get("HTTP"));
                        setCell(row, 4, results.get("SSH"));
                        setCell(row, 5, results.get("Modbus"));
                    }
                }
            }

            try (OutputStream out = ctx.getContentResolver().openOutputStream(uri, "rwt")) {
                wb.write(out);
            }
            wb.close();
        } catch (Exception e) {
            Log.e("ExcelWriter", "Ошибка при записи", e);
        }
    }

    private static void setCell(Row row, int col, String value) {
        Cell cell = row.getCell(col);
        if (cell == null) cell = row.createCell(col);
        cell.setCellValue(value);
    }
}
