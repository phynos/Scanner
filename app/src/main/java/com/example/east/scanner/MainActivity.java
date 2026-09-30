package com.example.east.scanner;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.east.scanner.databinding.ActivityMainBinding;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.snackbar.Snackbar;

import com.dtr.zxing.activity.CaptureActivity;

/**
 * 示例主页：演示 CaptureActivity 的调起（扫码 / 手动输入）与结果回传。
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    /**
     * 扫码结果回传。
     * CaptureActivity 通过自定义结果码 {@link CaptureActivity#RESULT_CODE} + extra "sn" 回传识别内容，
     * extra "isSame" 表示本次结果与上一次相同（手动输入模式下由输入框内容比对得出）。
     */
    private final ActivityResultLauncher<Intent> scanLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != CaptureActivity.RESULT_CODE || result.getData() == null) {
                    // 用户取消（RESULT_CANCELED），保留上一次结果，静默返回
                    return;
                }
                String sn = result.getData().getStringExtra("sn");
                if (sn == null || sn.length() == 0) {
                    return;
                }
                boolean isSame = result.getData().getBooleanExtra("isSame", false);
                showResult(sn, isSame);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnScan.setOnClickListener(v -> openCapture(CaptureActivity.INPUT_MODE_QR));
        binding.btnManual.setOnClickListener(v -> openCapture(CaptureActivity.INPUT_MODE_TEXT));
        binding.btnCopy.setOnClickListener(v -> copyResult());
    }

    /** 调起扫码界面，inputMode 取 {@link CaptureActivity#INPUT_MODE_QR} 或 {@link CaptureActivity#INPUT_MODE_TEXT} */
    private void openCapture(int inputMode) {
        Intent intent = new Intent(this, CaptureActivity.class);
        intent.putExtra(CaptureActivity.KEY_INPUT_MODE, inputMode);
        scanLauncher.launch(intent);
    }

    /** 展示识别结果 */
    private void showResult(String sn, boolean isSame) {
        binding.tvResult.setText(sn);
        // 有结果时用正文色（空态的弱化色见布局默认值）
        binding.tvResult.setTextColor(MaterialColors.getColor(binding.tvResult,
                com.google.android.material.R.attr.colorOnSurface));
        binding.tvSameHint.setVisibility(isSame ? View.VISIBLE : View.GONE);
        binding.btnCopy.setVisibility(View.VISIBLE);
    }

    /** 复制结果到剪贴板 */
    private void copyResult() {
        CharSequence text = binding.tvResult.getText();
        if (text.length() == 0) {
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.result_label), text));
        Snackbar.make(binding.getRoot(), R.string.copied, Snackbar.LENGTH_SHORT).show();
    }
}
