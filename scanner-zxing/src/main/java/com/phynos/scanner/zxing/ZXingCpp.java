package com.phynos.scanner.zxing;

import android.graphics.Bitmap;

import java.nio.charset.StandardCharsets;

/**
 * zxing-cpp 的 JNI 封装（无状态工具类）。
 * <p>
 * 解码传 Y 平面亮度数据（如 NV21 旋转后的数据）+ 裁剪区域；编码返回二维码模块矩阵。
 * 格式过滤使用 zxing-cpp 的格式名字符串，例如 {@link #FORMATS_QR}、{@link #FORMATS_ALL}，
 * 语法见 BarcodeFormatsFromString：分隔符 '|' 或 ','，大小写、'-' 均可选。
 */
public final class ZXingCpp {

    static {
        System.loadLibrary("ZXingCppDecoder");
    }

    private ZXingCpp() {
    }

    /** 二维码相关（与 DecodeFormatManager 的 QR 组一致） */
    public static final String FORMATS_QR = "QRCode,DataMatrix,Aztec,MaxiCode";
    /** 条形码（与 DecodeFormatManager 的 ONE_D 组一致） */
    public static final String FORMATS_BARCODE = "UPCA,UPCE,EAN13,EAN8,DataBar,DataBarExp,Code39,Code93,Code128,ITF,Codabar";
    /** 全部 */
    public static final String FORMATS_ALL = FORMATS_QR + "," + FORMATS_BARCODE + ",PDF417";

    /** LocalAverage，约等于旧 HybridBinarizer */
    public static final int BINARIZER_LOCAL_AVERAGE = 0;
    /** GlobalHistogram，约等于旧 GlobalHistogramBinarizer */
    public static final int BINARIZER_GLOBAL_HISTOGRAM = 1;

    /**
     * 解码 Y 平面亮度数据，失败返回 null。
     *
     * @param yPlane    Y 平面数据（width*height 至少）
     * @param width     图像宽
     * @param height    图像高
     * @param cropLeft/cropTop/cropWidth/cropHeight
     *                  裁剪区域；cropWidth/cropHeight &lt;=0 表示不裁剪
     * @param formats   格式过滤，null/空 表示全部格式
     * @param binarizer {@link #BINARIZER_LOCAL_AVERAGE} 或 {@link #BINARIZER_GLOBAL_HISTOGRAM}
     */
    public static String decode(byte[] yPlane, int width, int height,
                                int cropLeft, int cropTop, int cropWidth, int cropHeight,
                                String formats, int binarizer) {
        byte[] text = decodeNative(yPlane, width, height,
                cropLeft, cropTop, cropWidth, cropHeight,
                formats == null ? null : formats.getBytes(StandardCharsets.UTF_8),
                binarizer);
        return text == null ? null : new String(text, StandardCharsets.UTF_8);
    }

    /**
     * 生成二维码模块矩阵。
     *
     * @param content       内容（UTF-8）
     * @param ecLevel       纠错等级 "L"/"M"/"Q"/"H"，null 表示默认
     * @param addQuietZones 是否加静区
     * @return int[] = {cols, rows, 0/1...}，1 表示黑模块；失败返回 null
     */
    /**
     * 解码 Bitmap 图片（从文件/相册加载的图片），内部转换为 Y 平面后调用 native 解码。
     *
     * @param bitmap  待解码的图片
     * @param formats 格式过滤，null/空 表示全部格式
     * @return 解码文本，失败返回 null
     */
    public static String decodeBitmap(Bitmap bitmap, String formats) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        // 缩放到合理尺寸（最大边 1024），避免超大图导致内存问题和速度慢
        int maxDim = Math.max(w, h);
        if (maxDim > 1024) {
            float scale = 1024f / maxDim;
            w = Math.round(w * scale);
            h = Math.round(h * scale);
            bitmap = Bitmap.createScaledBitmap(bitmap, w, h, true);
        }
        // 提取 Y 平面灰度数据
        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        byte[] yPlane = new byte[w * h];
        for (int i = 0; i < pixels.length; i++) {
            int px = pixels[i];
            int r = (px >> 16) & 0xFF;
            int g = (px >> 8) & 0xFF;
            int b = px & 0xFF;
            // YUV 公式：Y = 0.299R + 0.587G + 0.114B
            yPlane[i] = (byte) ((306 * r + 601 * g + 117 * b + 512) >> 10);
        }
        return decode(yPlane, w, h, 0, 0, 0, 0, formats, BINARIZER_LOCAL_AVERAGE);
    }

    public static int[] encodeQr(String content, String ecLevel, boolean addQuietZones) {
        return encodeQrNative(content.getBytes(StandardCharsets.UTF_8),
                ecLevel == null ? null : ecLevel.getBytes(StandardCharsets.UTF_8),
                addQuietZones);
    }

    private static native byte[] decodeNative(byte[] yPlane, int width, int height,
                                              int cropLeft, int cropTop, int cropWidth, int cropHeight,
                                              byte[] formatsUtf8, int binarizer);

    private static native int[] encodeQrNative(byte[] contentUtf8, byte[] ecLevelUtf8, boolean addQuietZones);

    private static native Object[] getDebugImagesNative();

    private static native String getLastStrategyNative();

    /**
     * 获取最近一次解码命中的策略名称，例如 "直方图均衡化 + LocalAverage"。
     * 仅在 decode/decodeBitmap 调用后立即调用有效。
     *
     * @return 策略名称，解码失败时返回 null
     */
    public static String getLastStrategy() {
        return getLastStrategyNative();
    }

    /**
     * 调试图片数据，包含裁剪后的原始图和伽马校正后的预处理图。
     */
    public static class DebugImages {
        public final Bitmap cropImage;
        public final Bitmap gammaImage;

        DebugImages(Bitmap cropImage, Bitmap gammaImage) {
            this.cropImage = cropImage;
            this.gammaImage = gammaImage;
        }
    }

    /**
     * 获取最近一次 {@link #decode} 调用中的调试图片（裁剪图 + 伽马校正图）。
     * 仅在 decode 调用后立即调用有效，下次 decode 会覆盖。
     *
     * @return 调试图片，若无数据返回 null
     */
    public static DebugImages getDebugImages() {
        Object[] data = getDebugImagesNative();
        if (data == null || data[0] == null || data[2] == null || data[3] == null)
            return null;

        byte[] cropBytes = (byte[]) data[0];
        byte[] gammaBytes = (byte[]) data[1];
        int w = (Integer) data[2];
        int h = (Integer) data[3];
        if (w <= 0 || h <= 0)
            return null;

        Bitmap cropBmp = yuvToBitmap(cropBytes, w, h);
        Bitmap gammaBmp = gammaBytes != null ? yuvToBitmap(gammaBytes, w, h) : null;
        return new DebugImages(cropBmp, gammaBmp);
    }

    /**
     * 将 Y 平面灰度数据转换为 ARGB Bitmap。
     */
    private static Bitmap yuvToBitmap(byte[] yData, int width, int height) {
        int[] pixels = new int[width * height];
        for (int i = 0; i < pixels.length; i++) {
            int y = yData[i] & 0xFF;
            pixels[i] = 0xFF000000 | (y << 16) | (y << 8) | y;
        }
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bmp.setPixels(pixels, 0, width, 0, 0, width, height);
        return bmp;
    }
}
