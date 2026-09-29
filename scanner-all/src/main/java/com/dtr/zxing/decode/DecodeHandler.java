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

		// 这里需要将获取的data翻转一下，因为相机默认拿的的横屏的数据
		byte[] rotatedData = new byte[data.length];
		for (int y = 0; y < size.height; y++) {
			for (int x = 0; x < size.width; x++)
				rotatedData[x * size.height + size.height - y - 1] = data[x + y * size.width];
		}

		//先用zxing-cpp解码
		boolean result = decodeByZxingCpp(rotatedData);
		result = result || decodeByZbar(rotatedData);
		if(!result){
			//如果 都解码失败，则发送消息
			Handler handler = activity.getHandler();
			if (handler != null) {
				Message message = Message.obtain(handler, R.id.decode_failed);
				message.sendToTarget();
			}
		}
	}

	private boolean decodeByZxingCpp(byte[] rotatedData) {
		Rect rect = activity.getCropRect();
		if (rect == null) {
			Log.d("zxing-cpp-decode", "剪切面积为空！");
			return false;
		}

		Size size = activity.getCameraManager().getPreviewSize();
		// 旋转后宽高互换
		int width = size.height;
		int height = size.width;

		//先用 LocalAverage 二值化（约等于旧 HybridBinarizer）
		String text = ZXingCpp.decode(rotatedData, width, height,
				rect.left, rect.top, rect.width(), rect.height(),
				formats, ZXingCpp.BINARIZER_LOCAL_AVERAGE);

		//如果失败，再用 GlobalHistogram 二值化（可以增加低对比度的识别率）
		if (text == null) {
			text = ZXingCpp.decode(rotatedData, width, height,
					rect.left, rect.top, rect.width(), rect.height(),
					formats, ZXingCpp.BINARIZER_GLOBAL_HISTOGRAM);
		}

		if (text == null) {
			return false;
		}

		Handler handler = activity.getHandler();
		if (handler == null) {
			return false;
		}
		Message message = Message.obtain(handler, R.id.decode_succeeded, text);
		Bundle bundle = new Bundle();
		bundleThumbnail(rotatedData, width, height, rect, bundle);
		message.setData(bundle);
		message.sendToTarget();
		return true;
	}

	private boolean decodeByZbar(byte[] rotatedData){
		Size size = activity.getCameraManager().getPreviewSize();
		// 宽高也要调整
		int tmp = size.width;
		size.width = size.height;
		size.height = tmp;

		Image barcode = new Image(size.width, size.height,"Y800");
		barcode.setData(rotatedData);

		Rect rect = activity.getCropRect();
		if(rect == null) {
			Log.d("zbar-decode", "剪切面积为空！");
			return false;
		}
		barcode.setCrop(rect.left, rect.top, rect.width(),rect.height());

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
	private static void bundleThumbnail(byte[] rotatedData, int width, int height, Rect rect, Bundle bundle) {
		PreviewFrame frame = new PreviewFrame(rotatedData, width, height,
				rect.left, rect.top, rect.width(), rect.height());
		int[] pixels = frame.renderThumbnail();
		int thumbWidth = frame.getThumbnailWidth();
		int thumbHeight = frame.getThumbnailHeight();
		Bitmap bitmap = Bitmap.createBitmap(pixels, 0, thumbWidth, thumbWidth, thumbHeight, Bitmap.Config.ARGB_8888);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		bitmap.compress(Bitmap.CompressFormat.JPEG, 50, out);
		bundle.putByteArray(DecodeThread.BARCODE_BITMAP, out.toByteArray());
	}

}
