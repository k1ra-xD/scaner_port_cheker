package com.example.ip;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MainActivity: выбирает CSV/XLSX, парсит IP, запускает скан и записывает результаты обратно.
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private TextView tvOutput;
    private Button btnStart, btnPickFile;
    private List<String> ips = new ArrayList<>();
    private Set<String> ipsSet = new HashSet<>();
    private Uri pickedFileUri = null;

    // IPv4 regex
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "\\b((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)\\b");

    // SAF file picker
    private final ActivityResultLauncher<String[]> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), new ActivityResultCallback<Uri>() {
                @Override
                public void onActivityResult(@Nullable Uri uri) {
                    if (uri == null) {
                        showToast("Файл не выбран");
                        return;
                    }
                    pickedFileUri = uri;
                    final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                    try {
                        getContentResolver().takePersistableUriPermission(uri, takeFlags);
                    } catch (Exception ignored) {}

                    tvOutput.setText("");
                    ips.clear();
                    ipsSet.clear();
                    parseFileAndExtractIps(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvOutput = findViewById(R.id.tvOutput);
        btnStart = findViewById(R.id.btnStart);
        btnPickFile = findViewById(R.id.btnPickFile);

        btnPickFile.setOnClickListener(v -> {
            String[] mimeTypes = new String[]{
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-excel",
                    "text/csv",
                    "text/comma-separated-values",
                    "*/*"
            };
            filePickerLauncher.launch(mimeTypes);
        });

        btnStart.setOnClickListener(v -> {
            if (ips.isEmpty() || pickedFileUri == null) {
                showToast("Сначала выберите файл с IP");
                return;
            }
            tvOutput.setText("");
            new ScanAndWriteTask().execute(pickedFileUri);
        });
    }

    private void parseFileAndExtractIps(Uri uri) {
        try {
            ContentResolver cr = getContentResolver();
            String mime = cr.getType(uri);
            String name = getFileName(uri);
            String nameLower = name == null ? "" : name.toLowerCase();
            boolean treatAsCsv = (mime != null && mime.contains("csv")) || nameLower.endsWith(".csv");

            if (treatAsCsv) {
                parseCsv(uri);
            } else {
                if (!parseXlsx(uri)) {
                    parseCsv(uri);
                }
            }

            if (ips.isEmpty()) {
                tvOutput.setText("Не найдено IP в файле.");
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append("Найдено IP: ").append(ips.size()).append("\n");
                for (String ip : ips) sb.append(ip).append("\n");
                tvOutput.setText(sb.toString());
            }
        } catch (Exception e) {
            Log.e(TAG, "parseFileAndExtractIps", e);
            tvOutput.setText("Ошибка чтения файла: " + e.getMessage());
        }
    }

    private void parseCsv(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri);
             BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) {
                extractIpsFromText(line);
            }
        } catch (Exception e) {
            Log.w(TAG, "parseCsv failed", e);
        }
    }

    private boolean parseXlsx(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri);
             XSSFWorkbook wb = new XSSFWorkbook(is)) {
            int numberOfSheets = wb.getNumberOfSheets();
            for (int s = 0; s < numberOfSheets; s++) {
                Sheet sheet = wb.getSheetAt(s);
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        String cellText = getCellText(cell);
                        if (cellText != null && !cellText.isEmpty()) extractIpsFromText(cellText);
                    }
                }
            }
            return true;
        } catch (Exception e) {
            Log.w(TAG, "parseXlsx failed", e);
            return false;
        }
    }

    private String getCellText(Cell cell) {
        try {
            switch (cell.getCellType()) {
                case STRING: return cell.getStringCellValue();
                case NUMERIC:
                    double d = cell.getNumericCellValue();
                    long l = (long) d;
                    return (d == (double) l) ? String.valueOf(l) : String.valueOf(d);
                case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
                case FORMULA:
                    try { return cell.getStringCellValue(); }
                    catch (Exception ex) { return String.valueOf(cell.getNumericCellValue()); }
                default: return "";
            }
        } catch (Exception e) { return ""; }
    }

    private void extractIpsFromText(String text) {
        Matcher m = IPV4_PATTERN.matcher(text);
        while (m.find()) {
            String ip = m.group().trim();
            if (ipsSet.add(ip)) ips.add(ip);
        }
    }

    private void showToast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private String getFileName(Uri uri) {
        String result = null;
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx != -1) result = cursor.getString(idx);
            }
        } catch (Exception ignored) {}
        return result;
    }

    // ------------------------- AsyncTask -------------------------
    private class ScanAndWriteTask extends AsyncTask<Uri, String, Boolean> {
        @Override
        protected void onPreExecute() {
            btnStart.setEnabled(false);
            btnPickFile.setEnabled(false);
            tvOutput.setText("Запуск сканирования...\n");
        }

        @Override
        protected Boolean doInBackground(Uri... uris) {
            Uri uri = uris[0];
            Map<String, Map<String,String>> allResults = new LinkedHashMap<>();

            for (String ip : ips) {
                publishProgress("Сканирую " + ip);
                Map<String,String> res = PortScanner.scanIpSync(ip);
                allResults.put(ip, res);
                publishProgress(String.format(" -> PING=%s HTTP=%s SSH=%s Modbus=%s",
                        res.get("PING"), res.get("HTTP"), res.get("SSH"), res.get("Modbus")));
            }

            publishProgress("Сканирование завершено. Запись...");

            try {
                String name = getFileName(uri);
                String lower = name == null ? "" : name.toLowerCase();
                boolean isXlsx = lower.endsWith(".xlsx");
                if (isXlsx) {
                    return ExcelWriter.writeResultsToXlsx(getContentResolver(), uri, allResults);
                } else {
                    return ExcelWriter.writeResultsToCsv(getContentResolver(), uri, allResults);
                }
            } catch (Exception e) {
                Log.e(TAG, "write error", e);
                return false;
            }
        }

        @Override
        protected void onProgressUpdate(String... values) {
            for (String s : values) tvOutput.append(s + "\n");
        }

        @Override
        protected void onPostExecute(Boolean success) {
            btnStart.setEnabled(true);
            btnPickFile.setEnabled(true);
            tvOutput.append(success ? "Готово — результаты записаны\n" : "Ошибка при записи\n");
        }
    }
}
