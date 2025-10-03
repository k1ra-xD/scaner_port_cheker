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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvOutput = findViewById(R.id.tvOutput);
        btnPickFile = findViewById(R.id.btnPickFile);
        btnStart = findViewById(R.id.btnStart);

        // File Picker
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

        // Запуск проверки
        btnStart.setOnClickListener(v -> {
            if (pickedFileUri == null) {
                tvOutput.setText("Сначала выберите файл!");
                return;
            }

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
                            if (workInfo.getProgress().getBoolean("logUpdated", false)) {
                                tvOutput.setText(readLogTail());
                            }
                            if (workInfo.getState().isFinished()) {
                                tvOutput.append("\n✅ Проверка завершена");
                            }
                        }
                    });
        });

        // 🔥 При старте сразу подгружаем лог (последние 50 строк)
        tvOutput.setText(readLogTail());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 🔥 Каждый раз при возврате в активити тоже обновляем лог
        tvOutput.setText(readLogTail());
    }

    // Чтение последних строк из файла лога
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
