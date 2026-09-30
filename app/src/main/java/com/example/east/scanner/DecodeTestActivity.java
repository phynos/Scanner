package com.example.east.scanner;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.dtr.zxing.activity.CaptureActivity;
import com.example.east.scanner.databinding.ActivityDecodeTestBinding;
import com.google.android.material.snackbar.Snackbar;
import com.phynos.scanner.zxing.ZXingCpp;

import net.sourceforge.zbar.Config;
import net.sourceforge.zbar.Image;
import net.sourceforge.zbar.ImageScanner;
import net.sourceforge.zbar.Symbol;
import net.sourceforge.zbar.SymbolSet;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 主界面：图片解码测试 + 相机扫码。
 * 支持切换解码器（混合/zxing-cpp/zbar），显示预处理调试图，复制结果。
 */
public class DecodeTestActivity extends AppCompatActivity {

    private ActivityDecodeTestBinding binding;
    private ActivityResultLauncher<String> galleryLauncher;
    private ActivityResultLauncher<Intent> scanLauncher;

    private DecoderMode decoderMode = DecoderMode.HYBRID;
    /** 当前解码结果文本（用于复制） */
    private String currentResult;

    private enum DecoderMode {
        HYBRID, ZXING, ZBAR
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDecodeTestBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 解码器切换
        binding.toggleDecoder.check(R.id.btnDecHybrid);
        binding.toggleDecoder.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btnDecHybrid) decoderMode = DecoderMode.HYBRID;
            else if (checkedId == R.id.btnDecZxing) decoderMode = DecoderMode.ZXING;
            else if (checkedId == R.id.btnDecZbar) decoderMode = DecoderMode.ZBAR;
        });

        // 相机扫码：直接展示结果和调试信息，不重跑解码
        scanLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == CaptureActivity.RESULT_CODE && result.getData() != null) {
                        Intent data = result.getData();
                        String sn = data.getStringExtra("sn");
                        if (sn != null && !sn.isEmpty()) {
                            showCameraResult(sn, data);
                        }
                    }
                });
        binding.btnCamera.setOnClickListener(v -> {
            Intent intent = new Intent(this, CaptureActivity.class);
            intent.putExtra(CaptureActivity.KEY_INPUT_MODE, CaptureActivity.INPUT_MODE_QR);
            scanLauncher.launch(intent);
        });

        // 相册选图
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(), uri -> {
                    if (uri != null) decodeFromUri(uri);
                });
        binding.btnPickGallery.setOnClickListener(v -> galleryLauncher.launch("image/*"));

        // 内置测试图
        binding.btnBuiltIn.setOnClickListener(v -> showBuiltInDialog());

        // 复制按钮
        binding.btnCopy.setOnClickListener(v -> copyResult());
    }

    /** 显示相机扫码结果（直接使用 Intent 中的调试数据，不重跑解码） */
    private void showCameraResult(String text, Intent data) {
        currentResult = text;
        clearDebugImages();

        // 原始图片（缩略图）
        byte[] thumbBytes = data.getByteArrayExtra("thumbnail");
        if (thumbBytes != null) {
            Bitmap thumb = BitmapFactory.decodeByteArray(thumbBytes, 0, thumbBytes.length);
            binding.ivSource.setImageBitmap(thumb);
        }

        // 策略和解码器
        String strategy = data.getStringExtra("debug_strategy");
        StringBuilder info = new StringBuilder();
        info.append("✓ 扫码成功");
        info.append("\n解码器: zxing-cpp");
        if (strategy != null) {
            info.append("\n策略: ").append(strategy);
        }
        info.append("\n\n").append(text);
        binding.tvResult.setText(info);
        binding.btnCopy.setVisibility(View.VISIBLE);

        // 裁剪原图
        byte[] cropBytes = data.getByteArrayExtra("debug_crop_image");
        if (cropBytes != null) {
            Bitmap crop = BitmapFactory.decodeByteArray(cropBytes, 0, cropBytes.length);
            binding.ivCrop.setImageBitmap(crop);
            binding.ivCrop.setVisibility(View.VISIBLE);
            binding.labelCrop.setVisibility(View.VISIBLE);
        }

        // 预处理图
        byte[] gammaBytes = data.getByteArrayExtra("debug_gamma_image");
        if (gammaBytes != null) {
            Bitmap gamma = BitmapFactory.decodeByteArray(gammaBytes, 0, gammaBytes.length);
            binding.ivGamma.setImageBitmap(gamma);
            binding.ivGamma.setVisibility(View.VISIBLE);
            binding.labelGamma.setVisibility(View.VISIBLE);
        }
    }

    /** 弹出内置测试图片列表 */
    private void showBuiltInDialog() {
        String[] files;
        try {
            files = getAssets().list("test_qr");
        } catch (Exception e) {
            Toast.makeText(this, "无法读取测试图片目录", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> images = new ArrayList<>();
        if (files != null) {
            for (String f : files) {
                String lower = f.toLowerCase();
                if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
                    images.add(f);
                }
            }
        }

        if (images.isEmpty()) {
            Toast.makeText(this, "test_qr 目录下无图片，请先放入测试图片", Toast.LENGTH_LONG).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("选择测试图片")
                .setItems(images.toArray(new String[0]), (dialog, which) ->
                        decodeFromAssets("test_qr/" + images.get(which)))
                .show();
    }

    private void decodeFromAssets(String assetPath) {
        try {
            InputStream is = getAssets().open(assetPath);
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            is.close();
            if (bitmap == null) {
                Toast.makeText(this, "图片加载失败: " + assetPath, Toast.LENGTH_SHORT).show();
                return;
            }
            doDecode(bitmap);
        } catch (Exception e) {
            Toast.makeText(this, "加载异常: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void decodeFromUri(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            if (is != null) is.close();
            if (bitmap == null) {
                Toast.makeText(this, "图片加载失败", Toast.LENGTH_SHORT).show();
                return;
            }
            doDecode(bitmap);
        } catch (Exception e) {
            Toast.makeText(this, "加载异常: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /** 清理调试图 */
    private void clearDebugImages() {
        binding.ivCrop.setImageBitmap(null);
        binding.ivCrop.setVisibility(View.GONE);
        binding.labelCrop.setVisibility(View.GONE);
        binding.ivGamma.setImageBitmap(null);
        binding.ivGamma.setVisibility(View.GONE);
        binding.labelGamma.setVisibility(View.GONE);
    }

    /** 复制当前结果到剪贴板 */
    private void copyResult() {
        if (currentResult == null || currentResult.isEmpty()) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("decode_result", currentResult));
        Snackbar.make(binding.getRoot(), "已复制到剪贴板", Snackbar.LENGTH_SHORT).show();
    }

    /**
     * 执行解码并显示结果
     */
    private void doDecode(Bitmap bitmap) {
        clearDebugImages();
        binding.ivSource.setImageBitmap(bitmap);
        binding.tvResult.setText("解码中...");
        binding.btnCopy.setVisibility(View.GONE);
        currentResult = null;

        new Thread(() -> {
            String result = null;
            String decoderName = null;
            String strategy = null;
            long start = System.currentTimeMillis();

            switch (decoderMode) {
                case HYBRID: {
                    result = ZXingCpp.decodeBitmap(bitmap, null);
                    strategy = ZXingCpp.getLastStrategy();
                    if (result != null) {
                        decoderName = "zxing-cpp";
                    } else {
                        result = decodeByZbar(bitmap);
                        if (result != null) decoderName = "zbar（回退）";
                    }
                    break;
                }
                case ZXING: {
                    result = ZXingCpp.decodeBitmap(bitmap, null);
                    strategy = ZXingCpp.getLastStrategy();
                    if (result != null) decoderName = "zxing-cpp";
                    break;
                }
                case ZBAR: {
                    result = decodeByZbar(bitmap);
                    if (result != null) decoderName = "zbar";
                    break;
                }
            }

            long elapsed = System.currentTimeMillis() - start;
            ZXingCpp.DebugImages debug = (decoderMode != DecoderMode.ZBAR)
                    ? ZXingCpp.getDebugImages() : null;

            final String fResult = result;
            final String fDecoder = decoderName;
            final String fStrategy = strategy;
            final long fElapsed = elapsed;

            runOnUiThread(() -> {
                if (fResult != null) {
                    currentResult = fResult;
                    StringBuilder info = new StringBuilder();
                    info.append("✓ 解码成功 (").append(fElapsed).append("ms)");
                    info.append("\n解码器: ").append(fDecoder);
                    if (fStrategy != null) {
                        info.append("\n策略: ").append(fStrategy);
                    }
                    info.append("\n\n").append(fResult);
                    binding.tvResult.setText(info);
                    binding.btnCopy.setVisibility(View.VISIBLE);
                } else {
                    String modeName = decoderMode == DecoderMode.HYBRID ? "混合"
                            : decoderMode == DecoderMode.ZXING ? "zxing-cpp" : "zbar";
                    binding.tvResult.setText("✗ 解码失败 (" + fElapsed + "ms)\n解码器: " + modeName);
                    binding.btnCopy.setVisibility(View.GONE);
                }

                // 显示调试图
                if (debug != null) {
                    if (debug.cropImage != null) {
                        binding.ivCrop.setVisibility(View.VISIBLE);
                        binding.labelCrop.setVisibility(View.VISIBLE);
                        binding.ivCrop.setImageBitmap(debug.cropImage);
                    }
                    if (debug.gammaImage != null) {
                        binding.ivGamma.setVisibility(View.VISIBLE);
                        binding.labelGamma.setVisibility(View.VISIBLE);
                        binding.ivGamma.setImageBitmap(debug.gammaImage);
                    }
                }
            });
        }).start();
    }

    /** 使用 zbar 解码 Bitmap */
    private String decodeByZbar(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);

        byte[] yData = new byte[w * h];
        for (int i = 0; i < pixels.length; i++) {
            int px = pixels[i];
            int r = (px >> 16) & 0xFF;
            int g = (px >> 8) & 0xFF;
            int b = px & 0xFF;
            yData[i] = (byte) ((306 * r + 601 * g + 117 * b + 512) >> 10);
        }

        Image barcode = new Image(w, h, "Y800");
        barcode.setData(yData);

        ImageScanner scanner = new ImageScanner();
        scanner.setConfig(0, Config.X_DENSITY, 3);
        scanner.setConfig(0, Config.Y_DENSITY, 3);

        int scanResult = scanner.scanImage(barcode);
        if (scanResult != 0) {
            SymbolSet syms = scanner.getResults();
            for (Symbol sym : syms) {
                String data = sym.getData();
                if (data != null && !data.isEmpty()) return data;
            }
        }
        return null;
    }
}