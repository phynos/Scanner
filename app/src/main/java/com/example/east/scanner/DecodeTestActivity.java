package com.example.east.scanner;

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

import com.example.east.scanner.databinding.ActivityDecodeTestBinding;
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
 * 图片解码测试界面：支持从相册选图或选择内置测试图片进行解码。
 * 可切换解码器：混合（zxing-cpp + zbar）/ 仅 zxing-cpp / 仅 zbar。
 */
public class DecodeTestActivity extends AppCompatActivity {

    private ActivityResultLauncher<String> galleryLauncher;
    private ActivityDecodeTestBinding binding;

    /** 当前选中的解码器模式 */
    private DecoderMode decoderMode = DecoderMode.HYBRID;

    private enum DecoderMode {
        HYBRID, ZXING, ZBAR
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDecodeTestBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        // 解码器切换
        binding.toggleDecoder.check(R.id.btnDecHybrid);
        binding.toggleDecoder.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btnDecHybrid) decoderMode = DecoderMode.HYBRID;
            else if (checkedId == R.id.btnDecZxing) decoderMode = DecoderMode.ZXING;
            else if (checkedId == R.id.btnDecZbar) decoderMode = DecoderMode.ZBAR;
        });

        // 相册选图
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(), uri -> {
                    if (uri != null) decodeFromUri(uri);
                });

        binding.btnPickGallery.setOnClickListener(v -> galleryLauncher.launch("image/*"));
        binding.btnBuiltIn.setOnClickListener(v -> showBuiltInDialog());
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

    /**
     * 执行解码并显示结果
     */
    private void doDecode(Bitmap bitmap) {
        // 清理上一次的结果
        binding.ivCrop.setImageBitmap(null);
        binding.ivCrop.setVisibility(View.GONE);
        binding.labelCrop.setVisibility(View.GONE);
        binding.ivGamma.setImageBitmap(null);
        binding.ivGamma.setVisibility(View.GONE);
        binding.labelGamma.setVisibility(View.GONE);
        binding.ivSource.setImageBitmap(bitmap);
        binding.tvResult.setText("解码中...");

        new Thread(() -> {
            String result = null;
            String decoderName = null;
            String strategy = null;
            long start = System.currentTimeMillis();

            switch (decoderMode) {
                case HYBRID: {
                    // 先尝试 zxing-cpp（多策略）
                    result = ZXingCpp.decodeBitmap(bitmap, null);
                    strategy = ZXingCpp.getLastStrategy();
                    if (result != null) {
                        decoderName = "zxing-cpp";
                    } else {
                        // 回退到 zbar
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
                    StringBuilder info = new StringBuilder();
                    info.append("✓ 解码成功 (").append(fElapsed).append("ms)");
                    info.append("\n解码器: ").append(fDecoder);
                    if (fStrategy != null) {
                        info.append("\n策略: ").append(fStrategy);
                    }
                    info.append("\n\n").append(fResult);
                    binding.tvResult.setText(info);
                } else {
                    String modeName = decoderMode == DecoderMode.HYBRID ? "混合"
                            : decoderMode == DecoderMode.ZXING ? "zxing-cpp" : "zbar";
                    binding.tvResult.setText("✗ 解码失败 (" + fElapsed + "ms)\n解码器: " + modeName);
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

    /**
     * 使用 zbar 解码 Bitmap
     */
    private String decodeByZbar(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);

        // 转灰度 Y800 格式
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
                if (data != null && !data.isEmpty()) {
                    return data;
                }
            }
        }
        return null;
    }
}