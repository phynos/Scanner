/*
 * Copyright (C) 2008 ZXing authors
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

import android.os.Handler;
import android.os.Looper;

import com.dtr.zxing.activity.CaptureActivity;

import java.util.concurrent.CountDownLatch;

/**
 * This thread does all the heavy lifting of decoding the images.
 * 解码的线程，真正的解码相关工作在其关联的handle中处理
 * @author dswitkin@google.com (Daniel Switkin)
 */
public class DecodeThread extends Thread {

	public static final String BARCODE_BITMAP = "barcode_bitmap";

	/* 定义三种模式：条形码、二维码、全部  */
	public static final int BARCODE_MODE = 0X100;
	public static final int QRCODE_MODE = 0X200;
	public static final int ALL_MODE = 0X300;

	/* 各模式下都会附加尝试的格式 */
	private static final String EXTRA_FORMATS = "Aztec,PDF417";

	private final CaptureActivity activity;
	private final String formats;
	private Handler handler;
	private final CountDownLatch handlerInitLatch;

	public DecodeThread(CaptureActivity activity, int decodeMode) {

		this.activity = activity;
		handlerInitLatch = new CountDownLatch(1);

		String modeFormats;
		switch (decodeMode) {
		case BARCODE_MODE:
			modeFormats = DecodeFormatManager.getBarCodeFormats();
			break;

		case QRCODE_MODE:
			modeFormats = DecodeFormatManager.getQrCodeFormats();
			break;

		case ALL_MODE:
			modeFormats = DecodeFormatManager.getBarCodeFormats() + ","
					+ DecodeFormatManager.getQrCodeFormats();
			break;

		default:
			modeFormats = DecodeFormatManager.getQrCodeFormats();
			break;
		}

		formats = modeFormats + "," + EXTRA_FORMATS;
	}

	public Handler getHandler() {
		try {
			handlerInitLatch.await();
		} catch (InterruptedException ie) {
			// continue?
		}
		return handler;
	}

	@Override
	public void run() {
		Looper.prepare();
		handler = new DecodeHandler(activity, formats);
		handlerInitLatch.countDown();
		Looper.loop();
	}

}
