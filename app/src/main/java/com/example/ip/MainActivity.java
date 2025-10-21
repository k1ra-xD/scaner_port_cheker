package com.example.ip;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Calendar;
import java.util.LinkedList;

public class MainActivity extends AppCompatActivity {

    private TextView tvOutput;
    private Button btnPickFile, btnStart, btnStop;
    private EditText etStartRow;
    private View wifiIndicator;
    private ImageButton btnSaveWifi;
    private Uri pickedFileUri;
    private ActivityResultLauncher<String[]> filePickerLauncher;

    private Switch switchPing, switchHttp, switchSsh, switchModbus;

    private final Handler logHandler = new Handler(Looper.getMainLooper());
    private Runnable logUpdater;
    private boolean isUpdating = false;
    private OneTimeWorkRequest currentWorkRequest;

    private final Handler wifiHandler = new Handler(Looper.getMainLooper());
    private final Runnable wifiChecker = new Runnable() {
        @Override
        public void run() {
            try {
                boolean connected = WifiUtils.isConnectedToTargetWifi(MainActivity.this);
                if (wifiIndicator != null) {
                    wifiIndicator.setBackgroundColor(
                            connected ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336"));
                }
            } catch (Exception e) {
                if (tvOutput != null)
                    tvOutput.append("\n⚠️ Ошибка проверки Wi-Fi: " + e.getMessage());
            } finally {
                wifiHandler.postDelayed(this, 3000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvOutput = findViewById(R.id.tvOutput);
        btnPickFile = findViewById(R.id.btnPickFile);
        btnStart = findViewById(R.id.btnStart);
        etStartRow = findViewById(R.id.etStartRow);
        btnStop = findViewById(R.id.btnStop);
        wifiIndicator = findViewById(R.id.wifiIndicator);
        btnSaveWifi = findViewById(R.id.btnSaveWifi);

        switchPing = findViewById(R.id.switchPing);
        switchHttp = findViewById(R.id.switchHttp);
        switchSsh = findViewById(R.id.switchSsh);
        switchModbus = findViewById(R.id.switchModbus);

        setupSwitchColor(switchPing);
        setupSwitchColor(switchHttp);
        setupSwitchColor(switchSsh);
        setupSwitchColor(switchModbus);

        if (tvOutput != null)
            tvOutput.setMovementMethod(new ScrollingMovementMethod());

        // 📂 Выбор файла
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        pickedFileUri = uri;
                        tvOutput.setText("Файл выбран: " + uri.toString());

                        try {
                            final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                            getContentResolver().takePersistableUriPermission(uri, takeFlags);
                            tvOutput.append("\n🟢 Разрешение на запись сохранено");
                        } catch (Exception e) {
                            tvOutput.append("\n⚠️ Не удалось сохранить разрешение: " + e.getMessage());
                        }

                        getSharedPreferences("ipscanner_prefs", MODE_PRIVATE)
                                .edit()
                                .putString("last_file_uri", uri.toString())
                                .apply();
                    }
                });

        btnPickFile.setOnClickListener(v -> filePickerLauncher.launch(new String[]{
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-excel",
                "text/csv",
                "text/plain"
        }));

        btnStart.setOnClickListener(v -> startScan());
        btnStop.setOnClickListener(v -> stopScan());

        btnSaveWifi.setOnClickListener(v -> {
            String current = WifiUtils.getCurrentSSID(this);
            if (current.equals("—")) {
                NotificationHelper.showErrorNotification(this, "Не подключено к Wi-Fi");
                tvOutput.append("\n⚠️ Нет подключения к Wi-Fi");
                return;
            }
            WifiUtils.setTargetSSID(this, current);
            NotificationHelper.showCompletionNotification(this);
            tvOutput.append("\n📶 Целевая сеть сохранена: " + current);
        });

        tvOutput.setText(readLogTail());
        scheduleAutoScan();
        wifiHandler.post(wifiChecker);
    }

    /** 🎨 Цветовая индикация свитчей */
    private void setupSwitchColor(Switch sw) {
        if (sw == null) return;
        updateSwitchColor(sw, sw.isChecked());
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> updateSwitchColor(sw, isChecked));
    }

    private void updateSwitchColor(Switch sw, boolean enabled) {
        if (enabled) {
            sw.setThumbTintList(ColorStateList.valueOf(Color.parseColor("#00E676")));
            sw.setTrackTintList(ColorStateList.valueOf(Color.parseColor("#303030")));
        } else {
            sw.setThumbTintList(ColorStateList.valueOf(Color.parseColor("#777777")));
            sw.setTrackTintList(ColorStateList.valueOf(Color.parseColor("#202020")));
        }
    }

    /** 📅 Планировщик автопроверки (с поддержкой Android 12+) */
    private void scheduleAutoScan() {
        try {
            if (!WifiUtils.isConnectedToTargetWifi(this)) {
                tvOutput.append("\n⚠️ Автопроверка не установлена — не подключено к нужному Wi-Fi");
                return;
            }

            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) {
                tvOutput.append("\n⚠️ AlarmManager недоступен");
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    if (!alarmManager.canScheduleExactAlarms()) {
                        tvOutput.append("\n⚠️ Разрешение SCHEDULE_EXACT_ALARM не выдано. Автозапуск не установлен.");
                        return;
                    }
                } catch (SecurityException se) {
                    tvOutput.append("\n⚠️ SecurityException при проверке разрешений: " + se.getMessage());
                    return;
                } catch (Exception e) {
                    tvOutput.append("\n⚠️ Ошибка проверки canScheduleExactAlarms: " + e.getMessage());
                }
            }

            Calendar runTime = Calendar.getInstance();
            runTime.set(Calendar.HOUR_OF_DAY, 15);
            runTime.set(Calendar.MINUTE, 45);
            runTime.set(Calendar.SECOND, 0);
            runTime.set(Calendar.MILLISECOND, 0);
            if (runTime.before(Calendar.getInstance()))
                runTime.add(Calendar.DAY_OF_MONTH, 1);

            Intent scanIntentRaw = new Intent(this, ScanReceiver.class);
            int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                pendingFlags |= PendingIntent.FLAG_IMMUTABLE;

            PendingIntent scanIntent = PendingIntent.getBroadcast(this, 1001, scanIntentRaw, pendingFlags);

            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, runTime.getTimeInMillis(), scanIntent);
                tvOutput.append("\n📅 Автозапуск установлен на " + runTime.getTime());
            } catch (SecurityException se) {
                tvOutput.append("\n⚠️ Нет прав на установку точного Alarm: " + se.getMessage());
            } catch (Exception e) {
                tvOutput.append("\n⚠️ Ошибка установки Alarm: " + e.getMessage());
            }

        } catch (Exception e) {
            tvOutput.append("\n❌ scheduleAutoScan() завершился с ошибкой: " + e.getMessage());
        }
    }

    /** 🚀 Запуск проверки */
    private void startScan() {
        if (pickedFileUri == null) {
            tvOutput.setText("Сначала выберите файл!");
            NotificationHelper.showErrorNotification(this, "файл с IP не выбран");
            return;
        }

        // 🧹 Очистка старого лога перед новой проверкой + отметка даты запуска
        try {
            File logFile = new File(getExternalFilesDir(null), "scan_log.txt");
            if (logFile.exists()) {
                if (logFile.delete()) {
                    tvOutput.setText("🧹 Старый лог очищен\n");
                } else {
                    new FileWriter(logFile, false).close();
                    tvOutput.setText("🧹 Старый лог перезаписан\n");
                }
            }
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(logFile, true))) {
                String date = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
                bw.write("📅 Запуск проверки: " + date + "\n");
            }
        } catch (Exception e) {
            tvOutput.setText("⚠️ Не удалось очистить лог: " + e.getMessage() + "\n");
        }

        int startRow = 1;
        try {
            String startRowStr = etStartRow.getText().toString().trim();
            if (!startRowStr.isEmpty())
                startRow = Integer.parseInt(startRowStr);
        } catch (Exception ignored) {}

        boolean checkPing = switchPing.isChecked();
        boolean checkHttp = switchHttp.isChecked();
        boolean checkSsh = switchSsh.isChecked();
        boolean checkModbus = switchModbus.isChecked();

        // ✅ создаём резервную копию Excel перед проверкой
        try {
            File oldCopy = new File(getFilesDir(), "old_copy.xlsx");
            FileHelper.copyFileFromUri(this, pickedFileUri, oldCopy);
            tvOutput.append("\n📄 Создана резервная копия перед проверкой: " + oldCopy.getName());
        } catch (Exception e) {
            tvOutput.append("\n⚠️ Ошибка создания копии перед сканом: " + e.getMessage());
        }

        SharedPreferences prefs = getSharedPreferences("ipscanner_prefs", MODE_PRIVATE);
        prefs.edit()
                .putBoolean("checkPing", checkPing)
                .putBoolean("checkHttp", checkHttp)
                .putBoolean("checkSsh", checkSsh)
                .putBoolean("checkModbus", checkModbus)
                .putString("last_file_uri", pickedFileUri.toString())
                .putInt("startRow", startRow)
                .apply();

        Data inputData = new Data.Builder()
                .putString(ScanWorker.KEY_FILE_URI, pickedFileUri.toString())
                .putInt("startRow", startRow)
                .putBoolean("checkPing", checkPing)
                .putBoolean("checkHttp", checkHttp)
                .putBoolean("checkSsh", checkSsh)
                .putBoolean("checkModbus", checkModbus)
                .build();

        currentWorkRequest = new OneTimeWorkRequest.Builder(ScanWorker.class)
                .setInputData(inputData)
                .build();

        WorkManager.getInstance(this).enqueue(currentWorkRequest);

        WorkManager.getInstance(this)
                .getWorkInfoByIdLiveData(currentWorkRequest.getId())
                .observe(this, workInfo -> {
                    if (workInfo != null && workInfo.getState().isFinished()) {
                        tvOutput.append("\n✅ Проверка завершена");
                        stopLogUpdates();
                        currentWorkRequest = null;
                    }
                });

        startLogUpdates();
    }

    private void stopScan() {
        if (currentWorkRequest != null) {
            try {
                WorkManager wm = WorkManager.getInstance(this);
                wm.cancelWorkById(currentWorkRequest.getId());
                wm.pruneWork();
                NotificationHelper.cancelForegroundNotification(this);
                tvOutput.append("\n⛔ Проверка остановлена пользователем");

                // ✏️ добавляем запись в лог
                try (BufferedWriter bw = new BufferedWriter(
                        new FileWriter(new File(getExternalFilesDir(null), "scan_log.txt"), true))) {
                    bw.write("⛔ Проверка остановлена пользователем\n");
                } catch (Exception ignored) {}

                stopLogUpdates();
                currentWorkRequest = null;
            } catch (Exception e) {
                tvOutput.append("\n⚠️ Ошибка при остановке: " + e.getMessage());
            }
        } else {
            tvOutput.append("\n⚠️ Нет активной проверки");
        }
    }

    private void startLogUpdates() {
        if (isUpdating) return;
        isUpdating = true;
        if (logUpdater == null) {
            logUpdater = () -> {
                String tail = readLogTail();
                tvOutput.setText(tail);
                tvOutput.post(() -> {
                    if (tvOutput.getLayout() != null) {
                        int scroll = tvOutput.getLayout().getLineTop(tvOutput.getLineCount()) - tvOutput.getHeight();
                        tvOutput.scrollTo(0, Math.max(scroll, 0));
                    }
                });
                logHandler.postDelayed(logUpdater, 1000);
            };
        }
        logHandler.post(logUpdater);
    }

    private void stopLogUpdates() {
        if (logUpdater != null)
            logHandler.removeCallbacks(logUpdater);
        isUpdating = false;
    }

    private String readLogTail() {
        File logFile = new File(getExternalFilesDir(null), "scan_log.txt");
        if (!logFile.exists())
            return "Лог пока пуст";

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(logFile))) {
            String line;
            int maxLines = 50;
            LinkedList<String> buffer = new LinkedList<>();
            while ((line = br.readLine()) != null) {
                buffer.add(line);
                if (buffer.size() > maxLines)
                    buffer.removeFirst();
            }
            for (String s : buffer)
                sb.append(s).append("\n");
        } catch (Exception e) {
            return "Ошибка чтения лога: " + e.getMessage();
        }
        return sb.toString();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopLogUpdates();
        wifiHandler.removeCallbacks(wifiChecker);
    }
}
