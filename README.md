## Android二维码扫码模块
本模块采用双核心（zxing-cpp,zbar）进行解码，zxing-cpp 内部采用多策略解码（多种预处理 × 多种二值化），最大化识别率。
可以直接下载apk测试：https://github.com/phynos/Scanner/releases

## 优点
- zbar弥补了zxing识别慢，倾斜角度的问题
- 解码核心已升级为 zxing-cpp（C++，v3.1.1），在识别率、倾斜容错、反色码上较旧版 Java ZXing 明显提升
- **多策略解码**：依次尝试 5 种预处理 × 3 种二值化，最多 15 次尝试，成功即返回
  - 预处理：原始图像 → 全局直方图均衡化 → CLAHE 局部自适应均衡化 → 伽马校正(γ=0.3) → 伽马+CLAHE 组合
  - 二值化：LocalAverage(Hybrid) / GlobalHistogram / FixedThreshold
- 启用 tryDenoise（形态学闭合），提升间断/噪声二维码识别率
- 支持 tryInvert（黑底白码/屏幕码常见）、tryRotate、tryHarder
- **图片解码测试**：支持从相册选图或内置测试图片（`assets/test_qr/`）直接解码，附带预处理调试图

## 缺点
- 多策略解码在最坏情况下会尝试多次，耗时略增（正常光照下第一次即命中，无额外开销）
- 对比支付宝、微信，无法处理一些背景颜色和二维码颜色相同的情形
- 不支持微信的自动缩放功能

## 调用方法
    调用扫码界面
    Intent intent = new Intent(getActivity(), CaptureActivity.class);
    		intent.putExtra(CaptureActivity.KEY_INPUT_MODE, CaptureActivity.INPUT_MODE_QR);
    		startActivityForResult(intent, 9527);
## 获取扫码结果（在onActivityResult中回调）
    onActivityResult(int requestCode, int resultCode, Intent data)
    String sn = data.getStringExtra("sn");

## 其他说明
- zxing解码/编码核心为 zxing-cpp（https://github.com/zxing-cpp/zxing-cpp，v3.1.1，源码内置在 scanner-zxing 模块，经 NDK/CMake 编译），原先的 Java ZXing jar 已移除
- android摄像头部分的代码由zxing代码和开源中国的代码合并合成
- zbar代码来自网络，so库是直接在工程中编译的
- 主页提供"图片解码测试"入口，支持相册选图或内置测试图片解码，附带裁剪原图和预处理调试图
- 内置测试图片放在 `app/src/main/assets/test_qr/` 目录下（png/jpg），文件名会显示为选择列表的标签

## 注意事项
- 如果有代码混淆，请在app模块里面添加以下
	-keep class net.sourceforge.zbar.** { *; }
	-keep class com.phynos.zbar.** { *; }
	-keep class com.phynos.scanner.zxing.** { *; }
- 关于NDK17版本之后ndk的问题，参考：
  https://www.jianshu.com/p/ed9c3fea3584