package com.example.ip;

import android.content.Context;
import android.net.Uri;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class FileHelper {

    public static List<String> loadIps(Context ctx, Uri uri) {
        List<String> ips = new ArrayList<>();
        try {
            String name = uri.getLastPathSegment().toLowerCase();
            if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
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
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(ctx.getContentResolver().openInputStream(uri)))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (line.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                            ips.add(line.trim());
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ips;
    }
}
