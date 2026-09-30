package com.example.east.scanner;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.east.scanner.databinding.ActivityDecodeTestBinding;
import com.phynos.scanner.zxing.ZXingCpp;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 图片解码测试界面：支持从相册选图或选择内置测试图片进行解码。
 */
public class DecodeTestActivity extends AppCompatActivity {

    private ActivityResultLauncher<String> galleryLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityDecodeTestBinding binding = ActivityDecodeTestBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        // 相册选图
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(), uri -> {
                    if (uri != null) {
                        decodeFromUri(binding, uri);
                    }
                });

        binding.btnPickGallery.setOnClickListener(v ->
                galleryLauncher.launch("image/*"));

        // 内置测试图
        binding.btnBuiltIn.setOnClickListener(v -> showBuiltInDialog(binding));
    }

    /**
     * 弹出内置测试图片列表（从 assets/test_qr/ 读取）
     */
    private void showBuiltInDialog(ActivityDecodeTestBinding binding) {
        String[] files;
        try {
            files = getAssets().list("test_qr");
        } catch (Exception e) {
            Toast.makeText(this, "无法读取测试图片目录", Toast.LENGTH_SHORT).show();
            return;
        }

        // 过滤图片文件
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
                .setItems(images.toArray(new String[0]), (dialog, which) -> {
                    String fileName = images.get(which);
                    decodeFromAssets(binding, "test_qr/" + fileName);
                })
                .show();
    }

    /**
     * 从 assets 加载图片并解码
     */
    private void decodeFromAssets(ActivityDecodeTestBinding binding, String assetPath) {
        try {
            InputStream is = getAssets().open(assetPath);
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            is.close();
            if (bitmap == null) {
                Toast.makeText(this, "图片加载失败: " + assetPath, Toast.LENGTH_SHORT).show();
                return;
            }
            doDecode(binding, bitmap, assetPath);
        } catch (Exception e) {
            Toast.makeText(this, "加载异常: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 从 URI 加载图片并解码
     */
    private void decodeFromUri(ActivityDecodeTestBinding binding, Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            if (is != null) is.close();
            if (bitmap == null) {
                Toast.makeText(this, "图片加载失败", Toast.LENGTH_SHORT).show();
                return;
            }
            doDecode(binding, bitmap, uri.getLastPathSegment());
        } catch (Exception e) {
            Toast.makeText(this, "加载异常: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 执行解码并显示结果
     */
    private void doDecode(ActivityDecodeTestBinding binding, Bitmap bitmap, String label) {
        // 清理上一次的结果
        binding.ivCrop.setImageBitmap(null);
        binding.ivCrop.setVisibility(View.GONE);
        binding.labelCrop.setVisibility(View.GONE);
        binding.ivGamma.setImageBitmap(null);
        binding.ivGamma.setVisibility(View.GONE);
        binding.labelGamma.setVisibility(View.GONE);

        // 显示原始图片
        binding.ivSource.setImageBitmap(bitmap);

        // 在后台线程解码（避免阻塞 UI）
        binding.tvResult.setText("解码中...");
        new Thread(() -> {
            long start = System.currentTimeMillis();
            String result = ZXingCpp.decodeBitmap(bitmap, null);
            long elapsed = System.currentTimeMillis() - start;

            // 获取策略信息和调试图
            String strategy = ZXingCpp.getLastStrategy();
            ZXingCpp.DebugImages debug = ZXingCpp.getDebugImages();

            runOnUiThread(() -> {
                if (result != null) {
                    String info = "✓ 解码成功 (" + elapsed + "ms)";
                    if (strategy != null) {
                        info += "\n策略: " + strategy;
                    }
                    info += "\n\n" + result;
                    binding.tvResult.setText(info);
                } else {
                    binding.tvResult.setText("✗ 解码失败 (" + elapsed + "ms)");
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
}