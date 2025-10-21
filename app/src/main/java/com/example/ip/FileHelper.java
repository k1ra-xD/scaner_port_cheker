package com.example.ip;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class FileHelper {

    private static final String TAG = "FileHelper";
    private static final String IP_REGEX =
            "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$";

    public static List<String> loadIps(Context ctx, Uri uri, int startRow) {
        List<String> ips = new ArrayList<>();
        if (uri == null) {
            Log.e(TAG, "URI = null");
            return ips;
        }

        try {
            String name = (uri.getLastPathSegment() != null)
                    ? uri.getLastPathSegment().toLowerCase(Locale.ROOT)
                    : "";

            if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
                try (InputStream is = ctx.getContentResolver().openInputStream(uri)) {
                    if (is == null) throw new IllegalArgumentException("InputStream = null");
                    Workbook wb = WorkbookFactory.create(is);

                    Sheet sheet = wb.getSheetAt(0); // ✅ только первый лист
                    if (sheet != null) {
                        for (Row row : sheet) {
                            if (row == null || row.getRowNum() + 1 < startRow) continue;

                            for (Cell cell : row) {
                                if (cell == null) continue;
                                String text = cell.toString().trim();
                                if (text.matches(IP_REGEX)) {
                                    ips.add(text);
                                }
                            }
                        }
                    }

                    wb.close();
                }

            } else {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(ctx.getContentResolver().openInputStream(uri), StandardCharsets.UTF_8))) {
                    String line;
                    int lineNum = 0;
                    while ((line = br.readLine()) != null) {
                        lineNum++;
                        if (lineNum < startRow) continue;
                        if (line.startsWith("#") || line.trim().isEmpty()) continue;

                        String[] parts = line.split("[,;\\s\\t]+");
                        for (String part : parts) {
                            part = part.trim();
                            if (part.matches(IP_REGEX)) {
                                ips.add(part);
                            }
                        }
                    }
                }
            }

            Log.i(TAG, "Загружено IP: " + ips.size());
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при загрузке IP: " + e.getMessage(), e);
        }

        return ips;
    }

    public static List<String> loadIps(Context ctx, Uri uri) {
        return loadIps(ctx, uri, 1);
    }

    // ✅ Метод для чтения Имя (1-й столбец) + IP (2-й столбец)
    public static Map<String, String> loadNameIp(Context context, Uri uri, int startRow) {
        Map<String, String> result = new LinkedHashMap<>();
        if (uri == null) {
            Log.e(TAG, "URI = null");
            return result;
        }

        try {
            String fileName = (uri.getLastPathSegment() != null)
                    ? uri.getLastPathSegment().toLowerCase(Locale.ROOT)
                    : "";

            if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
                try (InputStream is = context.getContentResolver().openInputStream(uri)) {
                    if (is == null) throw new IllegalArgumentException("InputStream = null");
                    Workbook wb = WorkbookFactory.create(is);
                    Sheet sheet = wb.getSheetAt(0);

                    for (Row row : sheet) {
                        if (row == null || row.getRowNum() + 1 < startRow) continue;

                        Cell nameCell = row.getCell(0); // Имя
                        Cell ipCell = row.getCell(1);   // IP

                        if (ipCell == null) continue;
                        String ip = ipCell.toString().trim();
                        if (!ip.matches(IP_REGEX)) continue;

                        String name = (nameCell != null) ? nameCell.toString().trim() : "";
                        result.put(ip, name);
                    }

                    wb.close();
                }

            } else {
                // CSV / TXT
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(context.getContentResolver().openInputStream(uri), StandardCharsets.UTF_8))) {
                    String line;
                    int lineNum = 0;
                    while ((line = br.readLine()) != null) {
                        lineNum++;
                        if (lineNum < startRow) continue;
                        if (line.startsWith("#") || line.trim().isEmpty()) continue;

                        String[] parts = line.split("[,;\\t]+");
                        if (parts.length < 2) continue;

                        String name = parts[0].trim();
                        String ip = parts[1].trim();
                        if (!ip.matches(IP_REGEX)) continue;

                        result.put(ip, name);
                    }
                }
            }

            Log.i(TAG, "Загружено Имя+IP: " + result.size());
        } catch (Exception e) {
            Log.e(TAG, "Ошибка при загрузке Имя+IP: " + e.getMessage(), e);
        }

        return result;
    }

    // ✅ Теперь ищет колонку с "СКВ"/"Name"/"Название" и IP, берёт данные из них
    public static Map<String, String> loadIpsWithNames(Context ctx, Uri uri, int startRow) {
        Map<String, String> result = new LinkedHashMap<>();
        try (InputStream is = ctx.getContentResolver().openInputStream(uri)) {
            Workbook wb = WorkbookFactory.create(is);
            Sheet sheet = wb.getSheetAt(0);

            int nameCol = -1;
            int ipCol = -1;

            // ищем заголовки в первой строке
            Row header = sheet.getRow(0);
            if (header != null) {
                for (Cell c : header) {
                    String text = c.toString().trim().toLowerCase(Locale.ROOT);
                    if (text.contains("скв") || text.contains("name") || text.contains("название"))
                        nameCol = c.getColumnIndex();
                    if (text.contains("ip") || text.contains("адрес") || text.contains("address"))
                        ipCol = c.getColumnIndex();
                }
            }

            for (Row row : sheet) {
                if (row == null || row.getRowNum() + 1 < startRow) continue;
                if (row.getRowNum() == 0) continue; // пропускаем заголовок

                String ip = "";
                String name = "";

                if (ipCol != -1) {
                    Cell c = row.getCell(ipCol);
                    if (c != null) ip = c.toString().trim();
                } else {
                    // если не нашли колонку IP — ищем везде
                    for (Cell c : row) {
                        String val = c.toString().trim();
                        if (val.matches(IP_REGEX)) {
                            ip = val;
                            break;
                        }
                    }
                }

                if (!ip.matches(IP_REGEX)) continue;

                if (nameCol != -1) {
                    Cell nc = row.getCell(nameCol);
                    if (nc != null) name = nc.toString().trim();
                }

                result.put(ip, name);
            }

            wb.close();
        } catch (Exception e) {
            Log.e(TAG, "Ошибка loadIpsWithNames: " + e.getMessage(), e);
        }
        return result;
    }

    public static boolean copyFileFromUri(Context context, Uri sourceUri, File targetFile) {
        try (InputStream in = context.getContentResolver().openInputStream(sourceUri);
             OutputStream out = new FileOutputStream(targetFile)) {

            if (in == null) {
                Log.e(TAG, "Не удалось открыть поток для URI: " + sourceUri);
                return false;
            }

            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            out.flush();

            Log.i(TAG, "✅ Копия файла создана: " + targetFile.getAbsolutePath());
            return true;

        } catch (Exception e) {
            Log.e(TAG, "❌ Ошибка копирования: " + e.getMessage(), e);
            return false;
        }
    }
}
