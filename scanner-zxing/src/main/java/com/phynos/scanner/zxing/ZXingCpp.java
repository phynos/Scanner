package com.phynos.scanner.zxing;

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
    public static int[] encodeQr(String content, String ecLevel, boolean addQuietZones) {
        return encodeQrNative(content.getBytes(StandardCharsets.UTF_8),
                ecLevel == null ? null : ecLevel.getBytes(StandardCharsets.UTF_8),
                addQuietZones);
    }

    private static native byte[] decodeNative(byte[] yPlane, int width, int height,
                                              int cropLeft, int cropTop, int cropWidth, int cropHeight,
                                              byte[] formatsUtf8, int binarizer);

    private static native int[] encodeQrNative(byte[] contentUtf8, byte[] ecLevelUtf8, boolean addQuietZones);
}
