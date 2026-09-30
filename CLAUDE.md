# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

Android 二维码/条码扫码库。采用双核心解码（zxing-cpp + zbar），zxing-cpp 内部采用多策略解码（5 种预处理 × 3 种二值化，最多 15 次尝试），覆盖正常光照、暗色、低对比度、高对比度、间断等困难场景。代码注释和提交信息均为中文，新增注释请保持一致。

## 构建与测试命令

```bash
./gradlew assembleDebug          # 构建示例 APK（Windows 下用 gradlew.bat）
./gradlew :app:installDebug      # 安装示例应用到已连接的设备/模拟器
./gradlew test                   # 全部单元测试（JVM）
./gradlew :app:testDebugUnitTest --tests "com.example.east.scanner.ExampleUnitTest"   # 运行单个单元测试
./gradlew connectedAndroidTest   # 设备上的仪器测试（需要连接设备）
```

zbar 与 zxing-cpp 的原生代码依赖 Android NDK/CMake，由各模块的 `externalNativeBuild` 自动构建。ABI 仅限 `armeabi-v7a` 和 `arm64-v8a`（见各模块 `build.gradle` 的 `ndk.abiFilters`）。zxing-cpp 构建启用了 `ZXING_EXPERIMENTAL_API`（`scanner-zxing/src/main/cpp/CMakeLists.txt`）。

构建环境注意（实测）：
- **必须用 JDK 17 构建**：Gradle 8.0.2 不支持 JDK 21（报 `Unsupported class file major version 65`）。本机示例：`JAVA_HOME="C:\Program Files\Java\jdk-17" ./gradlew ...`。
- `scanner-zxing` 在 `build.gradle` 钉了 `ndkVersion '29.0.14206865'`——zxing-cpp 3.x 需要 C++20 `<ranges>`，NDK 25 的 clang 14 编不过。

仓库源使用阿里云 Maven 镜像 + google()/jcenter()（jcenter 已停止服务——新增依赖若解析失败应改用 mavenCentral）。

## 模块架构

- `:scanner-all` — 核心模块：相机预览（旧版 `android.hardware.Camera` API）、扫码界面、解码调度。对外入口是 `com.dtr.zxing.activity.CaptureActivity`。通过 `api` 依赖 `:scanner-zxing` 和 `:scanner-zbar`。
- `:scanner-zxing` — zxing-cpp（C++，v3.1.1）的集成模块。源码 vendor 在 `src/main/cpp/zxing-cpp/`（见该目录 `VENDOR.md`：来源、tag、裁剪说明），经 CMake 编译为静态核心并链接进 `libZXingCppDecoder.so`。Java 侧为 JNI 封装类 `com.phynos.scanner.zxing.ZXingCpp`（decode / decodeBitmap / encodeQr / getDebugImages，格式过滤用格式名字符串如 `"QRCode,DataMatrix"`，由 `BarcodeFormatsFromString` 解析）。构建启用了 `ZXING_EXPERIMENTAL_API` 以使用 `tryDenoise`（形态学闭合）。
- `:scanner-zbar` — zbar 的 Java 绑定（`net.sourceforge.zbar.*`）以及 `src/main/cpp` 下的 JNI C 源码（CMake 子工程：`libiconv-1.14`、`zbar`、`zbarjni`、`phynos`）。产出 `libZBarDecoder.so`，在 `Image`/`ImageScanner` 的 static 块中加载。
- `:app` — 示例应用（`MainActivity` + `DecodeTestActivity`），展示了集成调用方式和图片解码测试功能。测试图片放在 `app/src/main/assets/test_qr/`。

## 解码流程（scanner-all）

`CaptureActivity` → `CaptureActivityHandler` → `DecodeThread`/`DecodeHandler`。每一帧在 `DecodeHandler.decode()` 中：

1. 将 NV21 预览数据旋转 90°（竖屏），裁剪取景框区域。
2. 先用 zxing-cpp 解码（`decodeByZxingCpp` → `ZXingCpp.decode`）。JNI 内部实现**多策略解码**，依次尝试（成功即返回）：
   - **策略 1**：原始图像 × 三种二值化（LocalAverage / GlobalHistogram / FixedThreshold）
   - **策略 2**：全局直方图均衡化 × 三种二值化（拉伸对比度，处理整体偏暗/偏亮）
   - **策略 3**：CLAHE 局部自适应均衡化 × 三种二值化（8×8 分块，处理光照不均匀的暗图）
   - **策略 4**：伽马校正 γ=0.3 × 三种二值化（强提亮，处理极暗图像）
   - **策略 5**：伽马 0.3 + CLAHE 组合 × 三种二值化（极暗 + 不均匀光照）
   - 所有策略均启用 `tryDenoise`（形态学闭合）、`tryInvert`、`tryRotate`、`tryHarder`
3. 失败后回退到 zbar（`decodeByZbar`），按取景框区域裁剪——适合倾斜、识别慢的二维码。

**图片解码测试**：`app/src/main/java/com/example/east/scanner/DecodeTestActivity.java` 支持从相册选图或内置测试图片（`app/src/main/assets/test_qr/`）直接解码，附带裁剪原图和预处理调试图。

解码成功后通过 `R.id.decode_succeeded`（String + 缩略图 Bundle，zxing-cpp 命中）或 `R.id.decode_succeeded_zbar`（String，zbar 命中）消息回传给 `CaptureActivityHandler`——两个 ID 保留是为了区分命中核心，便于日后统计识别率。`DecodeThread` 通过 `BARCODE_MODE`/`QRCODE_MODE`/`ALL_MODE` 选择条码/二维码/全部格式（格式集合见 `DecodeFormatManager`/`ZXingCpp.FORMATS_*`）。缩略图由 `PreviewFrame`（纯 Y 平面像素运算）渲染。

编码：`QRCodeUtil.createQRImage`（scanner-all）→ `ZXingCpp.encodeQr`（zxing-cpp writer，纠错级别 H，带标准静区）→ Java 侧最近邻缩放绘制 + logo 合成。

图片解码测试：`ZXingCpp.decodeBitmap(Bitmap, formats)` 接受 Android Bitmap，内部转 Y 平面灰度后调用多策略解码；`ZXingCpp.getDebugImages()` 返回解码过程中的裁剪原图和预处理图（用于调试显示）。

## 对外 API 约定（见 MainActivity / README）

调起：向 `CaptureActivity` 发 `Intent`，extra `CaptureActivity.KEY_INPUT_MODE` 设为 `INPUT_MODE_QR`（相机扫码）或 `INPUT_MODE_TEXT`（手动输入 SN）。结果在 `onActivityResult` 中通过 `data.getStringExtra("sn")` 获取（另有 `"isSame"` 标志）。

## 修改代码时的约束

- ProGuard：使用方必须 keep `net.sourceforge.zbar.**`、`com.phynos.zbar.**` 和 `com.phynos.scanner.zxing.**`（JNI 绑定被重命名会崩溃）。`scanner-zxing` 的 consumer-rules.pro 已自动合并；README 中有说明。
- minSdk 15、targetSdk 30——不要在无兼容处理的情况下使用更高版本 API。相机代码使用已废弃的 Camera API 是有意为之；迁移到 CameraX/Camera2 属于全局改造，不应局部修改。
- `CaptureActivity` 在清单中锁定 `screenOrientation="portrait"`，且解码逻辑假设预览数据按竖屏旋转——若改动方向处理，两者必须保持同步。
- `scanner-zxing/src/main/cpp/zxing-cpp/` 是第三方源码快照，不要直接改其内部代码；升级时整目录替换并同步 `VENDOR.md`。构建必须 `ZXING_C_API=OFF`（vendor 时裁剪了 `wrappers/` 目录）。
