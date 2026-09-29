package com.dtr.zxing.utils;

import java.io.FileOutputStream;
import java.io.IOException;

import com.phynos.scanner.zxing.ZXingCpp;

import android.graphics.Bitmap;
import android.graphics.Canvas;

/**
 * 二维码生成工具类（基于 zxing-cpp）
 * @param content   内容
 * @param widthPix  图片宽度
 * @param heightPix 图片高度
 * @param logoBm    二维码中心的Logo图标（可以为null）
 * @param filePath  用于存储二维码图片的文件路径
 * @return 生成二维码及保存文件是否成功
 */
public class QRCodeUtil {

	/** 容错级别 H（约30%），与旧 ErrorCorrectionLevel.H 一致 */
	private static final String EC_LEVEL = "H";

	public static boolean createQRImage(String content, int widthPix, int heightPix, Bitmap logoBm, String filePath) {
		try {
			if (content == null || "".equals(content)) {
				return false;
			}

			// 模块矩阵（含标准静区），1 表示黑模块
			int[] matrix = ZXingCpp.encodeQr(content, EC_LEVEL, true);
			if (matrix == null || matrix.length < 2) {
				return false;
			}
			int cols = matrix[0];
			int rows = matrix[1];

			// 最近邻缩放到目标宽高，逐点生成二维码图片
			int[] pixels = new int[widthPix * heightPix];
			for (int y = 0; y < heightPix; y++) {
				int my = y * rows / heightPix;
				for (int x = 0; x < widthPix; x++) {
					int mx = x * cols / widthPix;
					pixels[y * widthPix + x] = matrix[2 + my * cols + mx] == 1 ? 0xff000000 : 0xffffffff;
				}
			}

			// 生成二维码图片的格式，使用ARGB_8888
			Bitmap bitmap = Bitmap.createBitmap(widthPix, heightPix, Bitmap.Config.ARGB_8888);
			bitmap.setPixels(pixels, 0, widthPix, 0, 0, widthPix, heightPix);

			if (logoBm != null) {
				bitmap = addLogo(bitmap, logoBm);
			}

			//必须使用compress方法将bitmap保存到文件中再进行读取。直接返回的bitmap是没有任何压缩的，内存消耗巨大！
			return bitmap != null && bitmap.compress(Bitmap.CompressFormat.JPEG, 100, new FileOutputStream(filePath));
		} catch (IOException e) {
			e.printStackTrace();
		}

		return false;
	}

	/**
	 * 在二维码中间添加Logo图案
	 */
	private static Bitmap addLogo(Bitmap src, Bitmap logo) {
		if (src == null) {
			return null;
		}

		if (logo == null) {
			return src;
		}

		//获取图片的宽高
		int srcWidth = src.getWidth();
		int srcHeight = src.getHeight();
		int logoWidth = logo.getWidth();
		int logoHeight = logo.getHeight();

		if (srcWidth == 0 || srcHeight == 0) {
			return null;
		}

		if (logoWidth == 0 || logoHeight == 0) {
			return src;
		}

		//logo大小为二维码整体大小的1/5
		float scaleFactor = srcWidth * 1.0f / 5 / logoWidth;
		Bitmap bitmap = Bitmap.createBitmap(srcWidth, srcHeight, Bitmap.Config.ARGB_8888);
		try {
			Canvas canvas = new Canvas(bitmap);
			canvas.drawBitmap(src, 0, 0, null);
			canvas.scale(scaleFactor, scaleFactor, srcWidth / 2, srcHeight / 2);
			canvas.drawBitmap(logo, (srcWidth - logoWidth) / 2, (srcHeight - logoHeight) / 2, null);

			//canvas.save(Canvas.ALL_SAVE_FLAG);
			canvas.save();
			canvas.restore();
		} catch (Exception e) {
			bitmap = null;
			e.getStackTrace();
		}

		return bitmap;
	}
}
