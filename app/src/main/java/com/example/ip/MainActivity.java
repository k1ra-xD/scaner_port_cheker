package com.example.ip;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.BufferedWriter;

public class MainActivity extends AppCompatActivity {

    private TextView tvOutput;
    private Button btnPickFile, btnStart, btnStop; // 🔥 добавлена кнопка
    private EditText etStartRow;
    private Uri pickedFileUri;
    private ActivityResultLauncher<String[]> filePickerLauncher;

    private Handler logHandler = new Handler(Looper.getMainLooper());
    private Runnable logUpdater;
    private boolean isUpdating = false;
    private OneTimeWorkRequest currentWorkRequest; // 🔥 храним активный воркер

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvOutput = findViewById(R.id.tvOutput);
        btnPickFile = findViewById(R.id.btnPickFile);
        btnStart = findViewById(R.id.btnStart);
        etStartRow = findViewById(R.id.etStartRow);
        btnStop = findViewById(R.id.btnStop); // 🔥 инициализация новой кнопки

        tvOutput.setMovementMethod(new ScrollingMovementMethod());

        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        pickedFileUri = uri;
                        tvOutput.setText("Файл выбран: " + uri.toString());
                    }
                });

        btnPickFile.setOnClickListener(v -> {
            filePickerLauncher.launch(new String[]{
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-excel",
                    "text/csv",
                    "text/plain"
            });
        });

        btnStart.setOnClickListener(v -> {
            if (pickedFileUri == null) {
                tvOutput.setText("Сначала выберите файл!");
                return;
            }

            int startRow = 1;
            String startRowStr = etStartRow.getText().toString().trim();
            if (!startRowStr.isEmpty()) {
                try {
                    startRow = Integer.parseInt(startRowStr);
                } catch (NumberFormatException e) {
                    startRow = 1;
                }
            }

            Data inputData = new Data.Builder()
                    .putString(ScanWorker.KEY_FILE_URI, pickedFileUri.toString())
                    .putInt("startRow", startRow)
                    .build();

            currentWorkRequest = new OneTimeWorkRequest.Builder(ScanWorker.class)
                    .setInputData(inputData)
                    .build();

            WorkManager.getInstance(this).enqueue(currentWorkRequest);

            WorkManager.getInstance(this).getWorkInfoByIdLiveData(currentWorkRequest.getId())
                    .observe(this, new Observer<WorkInfo>() {
                        @Override
                        public void onChanged(WorkInfo workInfo) {
                            if (workInfo == null) return;
                            if (workInfo.getState().isFinished()) {
                                tvOutput.append("\n✅ Проверка завершена");
                                stopLogUpdates();
                            }
                        }
                    });

            startLogUpdates();
        });

        // 🔥 обработка кнопки "Остановить проверку"
        btnStop.setOnClickListener(v -> {
            if (currentWorkRequest != null) {
                WorkManager.getInstance(this).cancelWorkById(currentWorkRequest.getId());
                tvOutput.append("\n⛔ Проверка остановлена пользователем");
                stopLogUpdates();

                // 🧾 также добавляем запись в файл scan_log.txt
                File logFile = new File(getExternalFilesDir(null), "scan_log.txt");
                try (FileWriter fw = new FileWriter(logFile, true);
                     BufferedWriter bw = new BufferedWriter(fw)) {
                    bw.write("⛔ Проверка остановлена пользователем\n");
                } catch (Exception e) {
                    e.printStackTrace();
                }

            } else {
                tvOutput.append("\n⚠️ Нет активной проверки");
            }
        });

        tvOutput.setText(readLogTail());
    }

    @Override
    protected void onResume() {
        super.onResume();
        startLogUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLogUpdates();
    }

    private void startLogUpdates() {
        if (isUpdating) return;
        isUpdating = true;

        if (logUpdater == null) {
            logUpdater = () -> {
                tvOutput.setText(readLogTail());
                tvOutput.post(() -> {
                    int scrollAmount = tvOutput.getLayout().getLineTop(tvOutput.getLineCount()) - tvOutput.getHeight();
                    if (scrollAmount > 0) tvOutput.scrollTo(0, scrollAmount);
                    else tvOutput.scrollTo(0, 0);
                });
                logHandler.postDelayed(logUpdater, 1000);
            };
        }
        logHandler.post(logUpdater);
    }

    private void stopLogUpdates() {
        if (logUpdater != null) {
            logHandler.removeCallbacks(logUpdater);
        }
        isUpdating = false;
    }

    private String readLogTail() {
        File logFile = new File(getExternalFilesDir(null), "scan_log.txt");
        if (!logFile.exists()) return "Лог пока пуст";

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(logFile))) {
            String line;
            int maxLines = 50;
            java.util.LinkedList<String> buffer = new java.util.LinkedList<>();
            while ((line = br.readLine()) != null) {
                buffer.add(line);
                if (buffer.size() > maxLines) buffer.removeFirst();
            }
            for (String s : buffer) sb.append(s).append("\n");
        } catch (Exception e) {
            return "Ошибка чтения лога: " + e.getMessage();
        }
        return sb.toString();
    }
}
