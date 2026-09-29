package com.phynos.scanner.zxing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * 编码→解码 往返回归（JNI 无法用 JVM 单测覆盖）。
 */
@RunWith(AndroidJUnit4.class)
public class ZXingCppRoundTripTest {

	/** 模块矩阵放大倍数，模拟拍摄到的图像 */
	private static final int SCALE = 8;

	private static byte[] matrixToLuma(int[] matrix, int scale) {
		int cols = matrix[0];
		int rows = matrix[1];
		int w = cols * scale;
		int h = rows * scale;
		byte[] luma = new byte[w * h];
		for (int y = 0; y < h; y++) {
			int my = y / scale;
			for (int x = 0; x < w; x++) {
				int mx = x / scale;
				// 1=黑模块，对应亮度 0；白底 255
				luma[y * w + x] = matrix[2 + my * cols + mx] == 1 ? (byte) 0 : (byte) 0xFF;
			}
		}
		return luma;
	}

	private void assertRoundTrip(String content) {
		int[] matrix = ZXingCpp.encodeQr(content, "H", true);
		assertNotNull("encodeQr 失败: " + content, matrix);
		assertTrue(matrix.length >= 2);
		int cols = matrix[0];
		int rows = matrix[1];
		assertTrue(cols > 0 && rows > 0);
		assertEquals(matrix.length, cols * rows + 2);

		byte[] luma = matrixToLuma(matrix, SCALE);
		String decoded = ZXingCpp.decode(luma, cols * SCALE, rows * SCALE,
				0, 0, 0, 0, ZXingCpp.FORMATS_QR, ZXingCpp.BINARIZER_LOCAL_AVERAGE);
		assertEquals(content, decoded);
	}

	@Test
	public void roundTripAscii() {
		assertRoundTrip("hello");
	}

	@Test
	public void roundTripUtf8() {
		assertRoundTrip("hello 你好");
	}

	@Test
	public void roundTripSn() {
		assertRoundTrip("SN-0123456789ABCDEF");
	}

	@Test
	public void roundTripGlobalHistogram() {
		String content = "histogram-test";
		int[] matrix = ZXingCpp.encodeQr(content, "M", true);
		assertNotNull(matrix);
		byte[] luma = matrixToLuma(matrix, SCALE);
		String decoded = ZXingCpp.decode(luma, matrix[0] * SCALE, matrix[1] * SCALE,
				0, 0, 0, 0, ZXingCpp.FORMATS_QR, ZXingCpp.BINARIZER_GLOBAL_HISTOGRAM);
		assertEquals(content, decoded);
	}

	@Test
	public void decodeEmptyReturnsNull() {
		byte[] luma = new byte[64 * 64];
		java.util.Arrays.fill(luma, (byte) 0xFF);
		String decoded = ZXingCpp.decode(luma, 64, 64, 0, 0, 0, 0,
				ZXingCpp.FORMATS_QR, ZXingCpp.BINARIZER_LOCAL_AVERAGE);
		assertEquals(null, decoded);
	}

	@Test
	public void cropRectDecode() {
		String content = "crop-me";
		int[] matrix = ZXingCpp.encodeQr(content, "H", true);
		assertNotNull(matrix);
		int cols = matrix[0];
		int rows = matrix[1];
		byte[] luma = matrixToLuma(matrix, SCALE);

		// 只解码中间区域（周围留出的均是静区白底，中间包含码）
		int cropW = cols * SCALE;
		int cropH = rows * SCALE;
		String decoded = ZXingCpp.decode(luma, cropW, cropH,
				0, 0, cropW, cropH, ZXingCpp.FORMATS_QR, ZXingCpp.BINARIZER_LOCAL_AVERAGE);
		assertEquals(content, decoded);
	}
}
