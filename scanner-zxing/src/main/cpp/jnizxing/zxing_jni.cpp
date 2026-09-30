/*
 * zxing-cpp JNI 封装（无状态，无 peer 对象）
 * 对应 Java 类：com.phynos.scanner.zxing.ZXingCpp
 */
#include <jni.h>

#include <cmath>
#include <cstdint>
#include <string>
#include <vector>

#include "BarcodeFormat.h"
#include "CreateBarcode.h"
#include "ImageView.h"
#include "ReadBarcode.h"
#include "WriteBarcode.h"

using namespace ZXing;

// 调试图片缓冲（解码后供 Java 侧获取）
static std::vector<uint8_t> g_debugCropImage;
static std::vector<uint8_t> g_debugGammaImage;
static int g_debugWidth = 0;
static int g_debugHeight = 0;

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

/**
 * 伽马校正查找表。
 * LUT[i] = 255 * (i/255)^gamma，预计算后逐像素映射，无运行时开销。
 */
class GammaLut {
    uint8_t lut[256];
public:
    explicit GammaLut(double gamma) {
        for (int i = 0; i < 256; ++i)
            lut[i] = static_cast<uint8_t>(255.0 * std::pow(i / 255.0, gamma) + 0.5);
    }
    uint8_t operator()(int v) const { return lut[v]; }
};

/** 对 Y 平面数据做伽马校正，返回新缓冲区 */
std::vector<uint8_t> applyGamma(const uint8_t* src, int size, const GammaLut& lut)
{
    std::vector<uint8_t> dst(size);
    for (int i = 0; i < size; ++i)
        dst[i] = lut(src[i]);
    return dst;
}

/**
 * 全局直方图均衡化——拉伸对比度，让像素值覆盖 0~255 全范围。
 * 对整体偏暗或偏亮的图片效果显著。
 */
std::vector<uint8_t> histogramEqualize(const uint8_t* src, int size)
{
    // 统计直方图
    int hist[256] = {};
    for (int i = 0; i < size; ++i)
        ++hist[src[i]];

    // 计算累积分布函数（CDF）
    int cdf[256];
    cdf[0] = hist[0];
    for (int i = 1; i < 256; ++i)
        cdf[i] = cdf[i - 1] + hist[i];

    // 找到第一个非零 CDF
    int cdfMin = 0;
    for (int i = 0; i < 256; ++i) {
        if (cdf[i] > 0) { cdfMin = cdf[i]; break; }
    }

    // 构建映射表
    uint8_t lut[256];
    if (cdfMin == size) {
        // 纯色图像，不做映射
        for (int i = 0; i < 256; ++i) lut[i] = static_cast<uint8_t>(i);
    } else {
        double scale = 255.0 / (size - cdfMin);
        for (int i = 0; i < 256; ++i) {
            int v = static_cast<int>((cdf[i] - cdfMin) * scale + 0.5);
            lut[i] = static_cast<uint8_t>(std::clamp(v, 0, 255));
        }
    }

    std::vector<uint8_t> dst(size);
    for (int i = 0; i < size; ++i)
        dst[i] = lut[src[i]];
    return dst;
}

/**
 * CLAHE（对比度受限的局部自适应直方图均衡化）。
 * 将图像分成 tileRows x tileCols 个块，每块独立做直方图均衡化，
 * 然后用双线性插值融合边界——处理光照不均匀的暗图效果极佳。
 *
 * clipLimit: 直方图裁剪限幅（防止噪声被过度放大），0 表示不限制。
 */
std::vector<uint8_t> applyCLAHE(const uint8_t* src, int width, int height,
                                int tileRows = 8, int tileCols = 8, int clipLimit = 40)
{
    const int size = width * height;
    std::vector<uint8_t> dst(size);

    const int tileH = height / tileRows;
    const int tileW = width / tileCols;
    if (tileH < 2 || tileW < 2) {
        // 图像太小，退化为全局均衡化
        return histogramEqualize(src, size);
    }

    // 为每个 tile 计算裁剪后的 CDF 映射表
    // tileMaps[tileRow * tileCols + tileCol][pixelValue] = mappedValue
    std::vector<std::vector<uint8_t>> tileMaps(tileRows * tileCols, std::vector<uint8_t>(256));

    for (int tr = 0; tr < tileRows; ++tr) {
        for (int tc = 0; tc < tileCols; ++tc) {
            int y0 = tr * tileH;
            int x0 = tc * tileW;

            // 统计这个 tile 的直方图
            int hist[256] = {};
            for (int y = y0; y < y0 + tileH; ++y)
                for (int x = x0; x < x0 + tileW; ++x)
                    ++hist[src[y * width + x]];

            // 裁剪直方图（CLAHE 核心：限制对比度放大）
            if (clipLimit > 0) {
                int excess = 0;
                for (int i = 0; i < 256; ++i) {
                    if (hist[i] > clipLimit) {
                        excess += hist[i] - clipLimit;
                        hist[i] = clipLimit;
                    }
                }
                // 将多余的部分均匀分配
                int redistrib = excess / 256;
                int residual = excess - redistrib * 256;
                for (int i = 0; i < 256; ++i) {
                    hist[i] += redistrib;
                    if (residual > 0) { ++hist[i]; --residual; }
                }
            }

            // 计算 CDF 映射
            const int tilePixels = tileH * tileW;
            int cdf[256];
            cdf[0] = hist[0];
            for (int i = 1; i < 256; ++i)
                cdf[i] = cdf[i - 1] + hist[i];

            int cdfMin = 0;
            for (int i = 0; i < 256; ++i) {
                if (cdf[i] > 0) { cdfMin = cdf[i]; break; }
            }

            if (cdfMin == tilePixels) {
                for (int i = 0; i < 256; ++i)
                    tileMaps[tr * tileCols + tc][i] = static_cast<uint8_t>(i);
            } else {
                double scale = 255.0 / (tilePixels - cdfMin);
                for (int i = 0; i < 256; ++i) {
                    int v = static_cast<int>((cdf[i] - cdfMin) * scale + 0.5);
                    tileMaps[tr * tileCols + tc][i] = static_cast<uint8_t>(std::clamp(v, 0, 255));
                }
            }
        }
    }

    // 双线性插值融合
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            // 计算像素所在的 tile 坐标（浮点）
            double fy = (y + 0.5) / tileH - 0.5;
            double fx = (x + 0.5) / tileW - 0.5;
            int tr0 = std::clamp(static_cast<int>(std::floor(fy)), 0, tileRows - 1);
            int tc0 = std::clamp(static_cast<int>(std::floor(fx)), 0, tileCols - 1);
            int tr1 = std::min(tr0 + 1, tileRows - 1);
            int tc1 = std::min(tc0 + 1, tileCols - 1);
            double ry = fy - tr0;
            double rx = fx - tc0;

            uint8_t pix = src[y * width + x];
            double v00 = tileMaps[tr0 * tileCols + tc0][pix];
            double v01 = tileMaps[tr0 * tileCols + tc1][pix];
            double v10 = tileMaps[tr1 * tileCols + tc0][pix];
            double v11 = tileMaps[tr1 * tileCols + tc1][pix];
            double val = v00 * (1 - ry) * (1 - rx)
                       + v01 * (1 - ry) * rx
                       + v10 * ry * (1 - rx)
                       + v11 * ry * rx;
            dst[y * width + x] = static_cast<uint8_t>(std::clamp(static_cast<int>(val + 0.5), 0, 255));
        }
    }
    return dst;
}

/**
 * 用指定参数尝试解码，成功返回解码文本，失败返回空字符串。
 */
std::string tryDecode(const uint8_t* data, int width, int height,
                      const std::string& formats, ZXing::Binarizer binarizer)
{
    using namespace ZXing;
    ImageView iv(data, width, height, ImageFormat::Lum);

    ReaderOptions opts;
    if (!formats.empty())
        opts.setFormats(BarcodeFormatsFromString(formats));
    opts.setBinarizer(binarizer);
    opts.setTryInvert(true);
    opts.setTryRotate(true);
    opts.setTryHarder(true);
    opts.setTryDenoise(true);   // 形态学闭合，填补间断定位图案

    Barcode barcode = ReadBarcode(iv, opts);
    if (barcode.isValid())
        return barcode.text();
    return {};
}

/** 对单张图像尝试所有二值化策略，成功即返回 */
std::string tryAllBinarizers(const uint8_t* data, int w, int h,
                             const std::string& formats, int preferredBin)
{
    const Binarizer bins[] = {
        preferredBin == 1 ? Binarizer::GlobalHistogram : Binarizer::LocalAverage,
        preferredBin == 1 ? Binarizer::LocalAverage : Binarizer::GlobalHistogram,
        Binarizer::FixedThreshold,
    };
    for (auto b : bins) {
        std::string r = tryDecode(data, w, h, formats, b);
        if (!r.empty()) return r;
    }
    return {};
}

} // namespace

extern "C" {

/**
 * 解码 Y 平面亮度数据（可带裁剪区域）。
 * 失败返回 null。
 *
 * 多策略解码：依次尝试原始图像和伽马校正图像 × 三种二值化算法，
 * 成功即返回，覆盖正常光照、暗色、低对比度、高对比度（屏幕码）等场景。
 * 所有策略均启用 tryDenoise（形态学闭合），提升间断/噪声二维码识别率。
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
        const uint8_t* imgData = reinterpret_cast<const uint8_t*>(data);
        int imgWidth = width;
        int imgHeight = height;

        // 如果有裁剪区域，提取裁剪后的数据
        std::vector<uint8_t> croppedBuf;
        if (cropWidth > 0 && cropHeight > 0) {
            croppedBuf.resize(static_cast<size_t>(cropWidth) * cropHeight);
            for (int y = 0; y < cropHeight; ++y)
                for (int x = 0; x < cropWidth; ++x)
                    croppedBuf[y * cropWidth + x] = imgData[(cropTop + y) * width + (cropLeft + x)];
            imgData = croppedBuf.data();
            imgWidth = cropWidth;
            imgHeight = cropHeight;
        }

        const int imgSize = imgWidth * imgHeight;
        std::string formats = JBytesToString(env, formatsUtf8);

        // 保存裁剪后的原始图像供调试显示
        g_debugCropImage.assign(imgData, imgData + imgSize);
        g_debugWidth = imgWidth;
        g_debugHeight = imgHeight;

        // === 策略 1：原始图像 × 三种二值化 ===
        std::string text = tryAllBinarizers(imgData, imgWidth, imgHeight, formats, binarizer);
        if (!text.empty()) {
            result = env->NewByteArray(static_cast<jsize>(text.size()));
            if (result)
                env->SetByteArrayRegion(result, 0, static_cast<jsize>(text.size()),
                                        reinterpret_cast<const jbyte*>(text.data()));
            env->ReleaseByteArrayElements(yPlane, data, JNI_ABORT);
            return result;
        }

        // === 策略 2：全局直方图均衡化（拉伸对比度）× 三种二值化 ===
        auto heData = histogramEqualize(imgData, imgSize);
        text = tryAllBinarizers(heData.data(), imgWidth, imgHeight, formats, binarizer);
        if (!text.empty()) {
            g_debugGammaImage = std::move(heData);  // 保存供调试显示
            result = env->NewByteArray(static_cast<jsize>(text.size()));
            if (result)
                env->SetByteArrayRegion(result, 0, static_cast<jsize>(text.size()),
                                        reinterpret_cast<const jbyte*>(text.data()));
            env->ReleaseByteArrayElements(yPlane, data, JNI_ABORT);
            return result;
        }

        // === 策略 3：CLAHE 局部自适应均衡化（不均匀光照）× 三种二值化 ===
        auto claheData = applyCLAHE(imgData, imgWidth, imgHeight);
        text = tryAllBinarizers(claheData.data(), imgWidth, imgHeight, formats, binarizer);
        if (!text.empty()) {
            // CLAHE 作为调试图（对间断/不均匀光照最有代表性）
            g_debugGammaImage = std::move(claheData);
            result = env->NewByteArray(static_cast<jsize>(text.size()));
            if (result)
                env->SetByteArrayRegion(result, 0, static_cast<jsize>(text.size()),
                                        reinterpret_cast<const jbyte*>(text.data()));
            env->ReleaseByteArrayElements(yPlane, data, JNI_ABORT);
            return result;
        }

        // === 策略 4：伽马校正(γ=0.3 强提亮)× 三种二值化 ===
        GammaLut gamma(0.3);
        auto gammaData = applyGamma(imgData, imgSize, gamma);
        g_debugGammaImage = gammaData;  // 保存供调试显示

        text = tryAllBinarizers(gammaData.data(), imgWidth, imgHeight, formats, binarizer);
        if (!text.empty()) {
            result = env->NewByteArray(static_cast<jsize>(text.size()));
            if (result)
                env->SetByteArrayRegion(result, 0, static_cast<jsize>(text.size()),
                                        reinterpret_cast<const jbyte*>(text.data()));
            env->ReleaseByteArrayElements(yPlane, data, JNI_ABORT);
            return result;
        }

        // === 策略 5：伽马校正 + CLAHE 组合（最暗+最不均匀）× 三种二值化 ===
        auto gammaClahe = applyCLAHE(gammaData.data(), imgWidth, imgHeight);
        text = tryAllBinarizers(gammaClahe.data(), imgWidth, imgHeight, formats, binarizer);
        if (!text.empty()) {
            g_debugGammaImage = std::move(gammaClahe);
            result = env->NewByteArray(static_cast<jsize>(text.size()));
            if (result)
                env->SetByteArrayRegion(result, 0, static_cast<jsize>(text.size()),
                                        reinterpret_cast<const jbyte*>(text.data()));
            env->ReleaseByteArrayElements(yPlane, data, JNI_ABORT);
            return result;
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

/**
 * 获取解码过程中的调试图片。
 * 返回 Object[4]：{cropImage(byte[]), gammaImage(byte[]), width(int), height(int)}。
 * 若某张图不存在则对应位置为 null。
 */
JNIEXPORT jobjectArray JNICALL
Java_com_phynos_scanner_zxing_ZXingCpp_getDebugImagesNative(JNIEnv* env, jclass)
{
    jobjectArray result = env->NewObjectArray(4, env->FindClass("java/lang/Object"), nullptr);
    if (!result)
        return nullptr;

    if (!g_debugCropImage.empty()) {
        jbyteArray cropArr = env->NewByteArray(static_cast<jsize>(g_debugCropImage.size()));
        if (cropArr)
            env->SetByteArrayRegion(cropArr, 0, static_cast<jsize>(g_debugCropImage.size()),
                                    reinterpret_cast<const jbyte*>(g_debugCropImage.data()));
        env->SetObjectArrayElement(result, 0, cropArr);
    }

    if (!g_debugGammaImage.empty()) {
        jbyteArray gammaArr = env->NewByteArray(static_cast<jsize>(g_debugGammaImage.size()));
        if (gammaArr)
            env->SetByteArrayRegion(gammaArr, 0, static_cast<jsize>(g_debugGammaImage.size()),
                                    reinterpret_cast<const jbyte*>(g_debugGammaImage.data()));
        env->SetObjectArrayElement(result, 1, gammaArr);
    }

    jclass intClass = env->FindClass("java/lang/Integer");
    jmethodID intCtor = env->GetMethodID(intClass, "<init>", "(I)V");

    jobject widthObj = env->NewObject(intClass, intCtor, g_debugWidth);
    jobject heightObj = env->NewObject(intClass, intCtor, g_debugHeight);
    env->SetObjectArrayElement(result, 2, widthObj);
    env->SetObjectArrayElement(result, 3, heightObj);

    // 用完清空，避免下次解码前残留旧数据
    g_debugCropImage.clear();
    g_debugGammaImage.clear();
    g_debugWidth = 0;
    g_debugHeight = 0;

    return result;
}

} // extern "C"
