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

import com.phynos.scanner.zxing.ZXingCpp;

/**
 * 解码格式配置，返回 zxing-cpp 的格式名字符串（见 {@link ZXingCpp}）。
 */
public class DecodeFormatManager {

	private DecodeFormatManager() {
	}

	// 二维码解码
	public static String getQrCodeFormats() {
		return ZXingCpp.FORMATS_QR;
	}

	// 1D条形码解码
	public static String getBarCodeFormats() {
		return ZXingCpp.FORMATS_BARCODE;
	}
}
