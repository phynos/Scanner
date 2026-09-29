package com.dtr.zxing.decode;

/**
 * 预览帧的亮度（Y平面）数据 + 裁剪区域，提供缩略图渲染。
 * <p>
 * 原 MyPlanarYUVLuminanceSource，去掉了对 zxing LuminanceSource 的继承；
 * 解码交给 {@link com.phynos.scanner.zxing.ZXingCpp}（直接吃 Y 平面数据 + 裁剪矩形）。
 */
public class PreviewFrame {

	private int mThumbnailScaleFactor = 2;

	public int getmThumbnailScaleFactor() {
		return mThumbnailScaleFactor;
	}

	public void setmThumbnailScaleFactor(int mThumbnailScaleFactor) {
		this.mThumbnailScaleFactor = mThumbnailScaleFactor;
	}

	private final byte[] yuvData;
	private final int dataWidth;
	private final int dataHeight;
	private final int left;
	private final int top;
	private final int width;
	private final int height;

	public PreviewFrame(byte[] yuvData,
			int dataWidth,
			int dataHeight,
			int left,
			int top,
			int width,
			int height) {
		if (left + width > dataWidth || top + height > dataHeight) {
			throw new IllegalArgumentException("Crop rectangle does not fit within image data.");
		}

		this.yuvData = yuvData;
		this.dataWidth = dataWidth;
		this.dataHeight = dataHeight;
		this.left = left;
		this.top = top;
		this.width = width;
		this.height = height;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public int[] renderThumbnail() {
		int width = getWidth() / mThumbnailScaleFactor;
		int height = getHeight() / mThumbnailScaleFactor;
		int[] pixels = new int[width * height];
		byte[] yuv = yuvData;
		int inputOffset = top * dataWidth + left;

		for (int y = 0; y < height; y++) {
			int outputOffset = y * width;
			for (int x = 0; x < width; x++) {
				int grey = yuv[inputOffset + x * mThumbnailScaleFactor] & 0xff;
				pixels[outputOffset + x] = 0xFF000000 | (grey * 0x00010101);
			}
			inputOffset += dataWidth * mThumbnailScaleFactor;
		}
		return pixels;
	}

	/**
	 * @return width of image from {@link #renderThumbnail()}
	 */
	 public int getThumbnailWidth() {
		 return getWidth() / mThumbnailScaleFactor;
	 }

	 /**
	  * @return height of image from {@link #renderThumbnail()}
	  */
	  public int getThumbnailHeight() {
		  return getHeight() / mThumbnailScaleFactor;
	  }

}
