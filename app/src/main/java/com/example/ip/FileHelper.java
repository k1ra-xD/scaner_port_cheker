package com.example.ip;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FileHelper {

    private static final String TAG = "FileHelper";

    public static List<String> loadIps(Context ctx, Uri uri) {
        Set<String> ips = new HashSet<>(); // чтобы не было дубликатов
        try {
            String name = uri.getLastPathSegment().toLowerCase();

            if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
                // Читаем Excel
                try (InputStream is = ctx.getContentResolver().openInputStream(uri);
                     Workbook wb = new XSSFWorkbook(is)) {

                    for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                        Sheet sheet = wb.getSheetAt(s);
                        for (Row row : sheet) {
                            for (Cell cell : row) {
                                String text = cell.toString().trim();
                                if (text.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                                    ips.add(text);
                                }
                            }
                        }
                    }
                }
            } else {
                // Читаем CSV или TXT
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(ctx.getContentResolver().openInputStream(uri)))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        // Разделяем по запятой, точке с запятой или пробелу
                        String[] parts = line.split("[,;\\s]+");
                        for (String part : parts) {
                            part = part.trim();
                            if (part.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                                ips.add(part);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при загрузке IP", e);
        }

        return new ArrayList<>(ips);
    }
}
