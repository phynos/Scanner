/*
 * Copyright (C) 2010 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dtr.zxing.decode;

import java.io.ByteArrayOutputStream;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import com.dtr.zxing.activity.CaptureActivity;
import com.phynos.scanner.all.R;
import com.phynos.scanner.zxing.ZXingCpp;

import net.sourceforge.zbar.Config;
import net.sourceforge.zbar.Image;
import net.sourceforge.zbar.ImageScanner;
import net.sourceforge.zbar.Symbol;
import net.sourceforge.zbar.SymbolSet;

/**
 * 真正的解码操作 相关代码
 */
public class DecodeHandler extends Handler {

	private final CaptureActivity activity;
	private final String formats;
	private boolean running = true;

	private ImageScanner mImageScanner = null;

	/** 旋转缓冲复用（仅解码线程访问），避免每帧分配 */
	private byte[] mRotateBuffer;

	public DecodeHandler(CaptureActivity activity, String formats) {
		this.activity = activity;
		this.formats = formats;

		mImageScanner = new ImageScanner();
		mImageScanner.setConfig(0, Config.X_DENSITY, 3);
		mImageScanner.setConfig(0, Config.Y_DENSITY, 3);
	}

	@Override
	public void handleMessage(Message message) {
		if (!running) {
			return;
		}
		if (message.what == R.id.decode) {
			decode((byte[]) message.obj, message.arg1, message.arg2);

		} else if (message.what == R.id.quit) {
			running = false;
			Looper.myLooper().quit();

		}
	}

	/**
	 * Decode the data within the viewfinder rectangle, and time how long it
	 * took. For efficiency, reuse the same reader objects from one decode to
	 * the next.
	 *
	 * @param data
	 *            The YUV preview frame.
	 * @param width
	 *            The width of the preview frame.
	 * @param height
	 *            The height of the preview frame.
	 */
	private void decode(byte[] data, int width, int height) {
		Size size = activity.getCameraManager().getPreviewSize();
		Rect rect = activity.getCropRect();
		if (size == null || rect == null) {
			Log.d("decode", "预览尺寸或剪切面积为空！");
			sendDecodeFailed();
			return;
		}

		// 只旋转取景框区域（相机默认横屏数据，需翻转为竖屏）。
		// 旋转后像素(rx, ry) 对应原图(x, y) = (ry, size.height - 1 - rx)，
		// 裁剪矩形位于旋转后坐标系中。
		int rotatedWidth = size.height;
		int rotatedHeight = size.width;
		int cropLeft = Math.max(0, Math.min(rect.left, rotatedWidth - 1));
		int cropTop = Math.max(0, Math.min(rect.top, rotatedHeight - 1));
		int cropWidth = Math.min(rect.width(), rotatedWidth - cropLeft);
		int cropHeight = Math.min(rect.height(), rotatedHeight - cropTop);
		if (cropWidth <= 0 || cropHeight <= 0) {
			sendDecodeFailed();
			return;
		}

		// 旋转缓冲复用，避免每帧分配整幅缓冲（本方法仅在解码线程调用）
		byte[] rotatedData = mRotateBuffer;
		if (rotatedData == null || rotatedData.length < cropWidth * cropHeight) {
			rotatedData = new byte[cropWidth * cropHeight];
			mRotateBuffer = rotatedData;
		}

		for (int ry = 0; ry < cropHeight; ry++) {
			int x = cropTop + ry;
			for (int rx = 0; rx < cropWidth; rx++) {
				int y = size.height - 1 - (cropLeft + rx);
				rotatedData[ry * cropWidth + rx] = data[x + y * size.width];
			}
		}

		//先用zxing-cpp解码
		boolean result = decodeByZxingCpp(rotatedData, cropWidth, cropHeight);
		result = result || decodeByZbar(rotatedData, cropWidth, cropHeight);
		if (!result) {
			//如果 都解码失败，则发送消息
			sendDecodeFailed();
		}
	}

	private void sendDecodeFailed() {
		Handler handler = activity.getHandler();
		if (handler != null) {
			Message message = Message.obtain(handler, R.id.decode_failed);
			message.sendToTarget();
		}
	}

	private boolean decodeByZxingCpp(byte[] cropData, int cropWidth, int cropHeight) {
		// 缓冲已裁剪，无需再传裁剪矩形
		// JNI 内部已实现多策略解码（多种二值化 + 伽马校正 + 形态学闭合），一次调用即可
		String text = ZXingCpp.decode(cropData, cropWidth, cropHeight,
				0, 0, 0, 0,
				formats, ZXingCpp.BINARIZER_LOCAL_AVERAGE);

		if (text == null) {
			return false;
		}

		Handler handler = activity.getHandler();
		if (handler == null) {
			return false;
		}
		Message message = Message.obtain(handler, R.id.decode_succeeded, text);
		Bundle bundle = new Bundle();
		bundleThumbnail(cropData, cropWidth, cropHeight, bundle);

		// 获取调试图片（裁剪原图 + 伽马校正图）
		try {
			ZXingCpp.DebugImages debugImages = ZXingCpp.getDebugImages();
			if (debugImages != null) {
				if (debugImages.cropImage != null) {
					ByteArrayOutputStream cropOut = new ByteArrayOutputStream();
					debugImages.cropImage.compress(Bitmap.CompressFormat.JPEG, 80, cropOut);
					bundleByteArray(bundle, "debug_crop_image", cropOut.toByteArray());
				}
				if (debugImages.gammaImage != null) {
					ByteArrayOutputStream gammaOut = new ByteArrayOutputStream();
					debugImages.gammaImage.compress(Bitmap.CompressFormat.JPEG, 80, gammaOut);
					bundleByteArray(bundle, "debug_gamma_image", gammaOut.toByteArray());
				}
			}
		} catch (Exception e) {
			Log.w("DecodeHandler", "获取调试图片失败", e);
		}

		message.setData(bundle);
		message.sendToTarget();
		return true;
	}

	private static void bundleByteArray(Bundle bundle, String key, byte[] data) {
		bundle.putByteArray(key, data);
	}

	private boolean decodeByZbar(byte[] cropData, int cropWidth, int cropHeight) {
		// 缓冲已裁剪为取景框区域，整幅解码即可
		Image barcode = new Image(cropWidth, cropHeight, "Y800");
		barcode.setData(cropData);

		int result = mImageScanner.scanImage(barcode);
		String resultStr = null;

		if (result != 0) {
			SymbolSet syms = mImageScanner.getResults();
			for (Symbol sym : syms) {
				resultStr = sym.getData();
			}
		}
		if (!TextUtils.isEmpty(resultStr)) {
			Handler handler = activity.getHandler();
			if (handler != null) {
				Message message = Message.obtain(handler, R.id.decode_succeeded_zbar,resultStr);
				message.sendToTarget();
			}
			return true;
		} else {
			return false;
		}
	}

	/**
	 * 根据YUV图像生成缩略图，将缩略图数据传给界面
	 */
	private static void bundleThumbnail(byte[] cropData, int cropWidth, int cropHeight, Bundle bundle) {
		PreviewFrame frame = new PreviewFrame(cropData, cropWidth, cropHeight, 0, 0, cropWidth, cropHeight);
		int[] pixels = frame.renderThumbnail();
		int thumbWidth = frame.getThumbnailWidth();
		int thumbHeight = frame.getThumbnailHeight();
		Bitmap bitmap = Bitmap.createBitmap(pixels, 0, thumbWidth, thumbWidth, thumbHeight, Bitmap.Config.ARGB_8888);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		bitmap.compress(Bitmap.CompressFormat.JPEG, 50, out);
		bundle.putByteArray(DecodeThread.BARCODE_BITMAP, out.toByteArray());
	}

}
