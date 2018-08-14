## Android二维码扫码模块
本模块采用双核心（zxing,zbar）进行解码，同时zxing在二值化算法上也采用双算法

## 优点
- zbar弥补了zxing识别慢，倾斜角度的问题
- zxing解码增加了直方图二值化算法，有助于识别高对比度的二维码（特别是一些单色LCD屏幕，比如一些嵌入式设备屏幕）
- zxing在识别一些曝光不一致导致有渐变色的二维码上优势

## 缺点
- 由于采用双核心3次解码，效率上会有影响
- 对比支付宝、微信，无法处理一些背景颜色和二维码颜色形同的情形
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
- zxing核心部分是由官网代码自己编译
- android摄像头部分的代码由zxing代码和开源中国的代码合并合成
- zbar代码来自网络，so库是直接在工程中编译的

## 注意事项
- 如果有代码混淆，请在app模块里面添加以下
	-keep class net.sourceforge.zbar.** { *; }