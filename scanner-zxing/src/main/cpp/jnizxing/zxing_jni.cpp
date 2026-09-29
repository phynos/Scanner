/*
 * zxing-cpp JNI 封装（无状态，无 peer 对象）
 * 对应 Java 类：com.phynos.scanner.zxing.ZXingCpp
 */
#include <jni.h>

#include <cstdint>
#include <string>
#include <vector>

#include "BarcodeFormat.h"
#include "CreateBarcode.h"
#include "ImageView.h"
#include "ReadBarcode.h"
#include "WriteBarcode.h"

using namespace ZXing;

namespace {

std::string JBytesToString(JNIEnv* env, jbyteArray arr)
{
    if (!arr)
        return {};
    jsize len = env->GetArrayLength(arr);
    std::string s(static_cast<size_t>(len), '\0');
    if (len > 0)
        env->GetByteArrayRegion(arr, 0, len, reinterpret_cast<jbyte*>(s.data()));
    return s;
}

} // namespace

extern "C" {

/**
 * 解码 Y 平面亮度数据（可带裁剪区域）。
 * 失败返回 null。binarizer: 0=LocalAverage(Hybrid), 1=GlobalHistogram。
 */
JNIEXPORT jbyteArray JNICALL
Java_com_phynos_scanner_zxing_ZXingCpp_decodeNative(JNIEnv* env, jclass,
        jbyteArray yPlane, jint width, jint height,
        jint cropLeft, jint cropTop, jint cropWidth, jint cropHeight,
        jbyteArray formatsUtf8, jint binarizer)
{
    if (!yPlane || width <= 0 || height <= 0)
        return nullptr;

    jsize len = env->GetArrayLength(yPlane);
    if (len < width * height)
        return nullptr;

    jbyte* data = env->GetByteArrayElements(yPlane, nullptr);
    if (!data)
        return nullptr;

    jbyteArray result = nullptr;
    try {
        ImageView iv(reinterpret_cast<const uint8_t*>(data), width, height, ImageFormat::Lum);
        if (cropWidth > 0 && cropHeight > 0)
            iv = iv.cropped(cropLeft, cropTop, cropWidth, cropHeight);

        ReaderOptions opts;
        std::string formats = JBytesToString(env, formatsUtf8);
        if (!formats.empty())
            opts.setFormats(BarcodeFormatsFromString(formats));
        opts.setBinarizer(binarizer == 1 ? Binarizer::GlobalHistogram : Binarizer::LocalAverage);
        // 反色码（白底黑码/黑底白码）在屏幕码场景常见
        opts.setTryInvert(true);
        opts.setTryRotate(true);
        opts.setTryHarder(true);

        Barcode barcode = ReadBarcode(iv, opts);
        if (barcode.isValid()) {
            std::string text = barcode.text();
            result = env->NewByteArray(static_cast<jsize>(text.size()));
            if (result)
                env->SetByteArrayRegion(result, 0, static_cast<jsize>(text.size()),
                                        reinterpret_cast<const jbyte*>(text.data()));
        }
    } catch (...) {
        // 参数非法等异常不外抛，按解码失败处理
    }

    env->ReleaseByteArrayElements(yPlane, data, JNI_ABORT);
    return result;
}

/**
 * 生成二维码模块矩阵（1 字节/模块，UTF-8 内容、UTF-8 ecLevel）。
 * 返回 int[] = {cols, rows, 0/1...}，1 表示黑模块；失败返回 null。
 */
JNIEXPORT jintArray JNICALL
Java_com_phynos_scanner_zxing_ZXingCpp_encodeQrNative(JNIEnv* env, jclass,
        jbyteArray contentUtf8, jbyteArray ecLevelUtf8, jboolean addQuietZones)
{
    jintArray result = nullptr;
    try {
        std::string content = JBytesToString(env, contentUtf8);
        std::string ecLevel = JBytesToString(env, ecLevelUtf8);

        std::string options;
        if (!ecLevel.empty())
            options = "ecLevel=" + ecLevel;

        Barcode barcode = CreateBarcodeFromText(content, CreatorOptions(BarcodeFormat::QRCode, options));
        if (!barcode.isValid())
            return nullptr;

        // scale(1) => zint 1 像素/模块，黑白极性：黑≈0、白≈255
        Image img = WriteBarcodeToImage(barcode,
                WriterOptions().scale(1).addQuietZones(addQuietZones == JNI_TRUE));
        if (!img.data() || img.width() <= 0 || img.height() <= 0)
            return nullptr;

        int cols = img.width();
        int rows = img.height();
        std::vector<jint> out(static_cast<size_t>(cols) * rows + 2);
        out[0] = cols;
        out[1] = rows;
        for (int y = 0; y < rows; ++y) {
            const uint8_t* line = img.data(0, y);
            for (int x = 0; x < cols; ++x)
                out[2 + y * cols + x] = line[x] < 128 ? 1 : 0;
        }

        result = env->NewIntArray(static_cast<jsize>(out.size()));
        if (result)
            env->SetIntArrayRegion(result, 0, static_cast<jsize>(out.size()), out.data());
    } catch (...) {
        // 内容/参数非法不外抛，返回 null
    }
    return result;
}

} // extern "C"
