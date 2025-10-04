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
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public class MainActivity extends AppCompatActivity {

    private TextView tvOutput;
    private Button btnPickFile, btnStart;
    private Uri pickedFileUri;
    private ActivityResultLauncher<String[]> filePickerLauncher;

    // 🔥 handler для автообновления логов
    private Handler logHandler = new Handler(Looper.getMainLooper());
    private Runnable logUpdater;
    private boolean isUpdating = false; // чтобы не запускалось повторно

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvOutput = findViewById(R.id.tvOutput);
        btnPickFile = findViewById(R.id.btnPickFile);
        btnStart = findViewById(R.id.btnStart);

        // разрешаем прокрутку текста руками
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

            // запуск воркера
            Data inputData = new Data.Builder()
                    .putString(ScanWorker.KEY_FILE_URI, pickedFileUri.toString())
                    .build();

            OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ScanWorker.class)
                    .setInputData(inputData)
                    .build();

            WorkManager.getInstance(this).enqueue(request);

            WorkManager.getInstance(this).getWorkInfoByIdLiveData(request.getId())
                    .observe(this, new Observer<WorkInfo>() {
                        @Override
                        public void onChanged(WorkInfo workInfo) {
                            if (workInfo == null) return;
                            if (workInfo.getState().isFinished()) {
                                tvOutput.append("\n✅ Проверка завершена");
                                stopLogUpdates(); // останавливаем таймер после завершения
                            }
                        }
                    });

            // 🔥 запускаем автообновление логов раз в 1 сек
            startLogUpdates();
        });

        // при старте показываем последние записи
        tvOutput.setText(readLogTail());
    }

    @Override
    protected void onResume() {
        super.onResume();
        startLogUpdates(); // 🔥 возобновляем обновление логов
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLogUpdates(); // 🔥 чтобы не утекал Handler
    }

    private void startLogUpdates() {
        if (isUpdating) return; // уже работает
        isUpdating = true;

        if (logUpdater == null) {
            logUpdater = () -> {
                tvOutput.setText(readLogTail());
                // автопрокрутка вниз
                tvOutput.post(() -> {
                    int scrollAmount = tvOutput.getLayout().getLineTop(tvOutput.getLineCount()) - tvOutput.getHeight();
                    if (scrollAmount > 0) tvOutput.scrollTo(0, scrollAmount);
                    else tvOutput.scrollTo(0, 0);
                });
                logHandler.postDelayed(logUpdater, 1000); // обновление каждую секунду
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

    // Чтение последних строк из файла
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
