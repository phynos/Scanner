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
package com.dtr.zxing.activity;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.dtr.zxing.camera.CameraManager;
import com.dtr.zxing.decode.DecodeThread;
import com.dtr.zxing.decode.MyPlanarYUVLuminanceSource;
import com.dtr.zxing.utils.BeepManager;
import com.dtr.zxing.utils.InactivityTimer;
import com.google.zxing.Result;
import com.phynos.scanner.all.BuildConfig;
import com.phynos.scanner.all.R;

import java.io.IOException;

/**
 * This activity opens the camera and does the actual scanning on a background
 * thread. It draws a viewfinder to help the user place the barcode correctly,
 * shows feedback as the image processing is happening, and then overlays the
 * results when a scan is successful.
 * 
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Sean Owen
 */
public final class CaptureActivity extends AppCompatActivity implements
SurfaceHolder.Callback, OnClickListener {

	private static final String TAG = CaptureActivity.class.getSimpleName();

	public final static int RESULT_CODE = 1221;

//	public final static String QR_CODE = "qr_code";

	public final static String KEY_INPUT_MODE = "key_input_mode";
	/**
	 * 扫码模式
	 */
	public final static int INPUT_MODE_QR = 0;
	/**
	 * 手动模式
	 */
	public final static int INPUT_MODE_TEXT = 1;
	/**
	 * 模式
	 */
	private int mMode = 0;
	/**
	 * SharedPreferences认证
	 */
	public final static String SHARED_KEY = "msn";
	
	private CameraManager cameraManager;
	private CaptureActivityHandler handler;
	private InactivityTimer inactivityTimer;
	private BeepManager beepManager;
	private ImageView mImageLight;//开灯按钮
	private SurfaceView scanPreview = null;
	
	private RelativeLayout scanCropView;
	private EditText mEditSn;
	private ImageView mImageResult;

	/**
	 * 闪光灯状态 标志位
	 */
	private boolean mIsLightOn = false;

	private Rect mCropRect = null;

	private LinearLayout mPreview1;

	private View mPreview2;
	
	
	/**
	 * 模式切换
	 */
	private TextView mTextMode;
	/**
	 * 手动输入模式界面
	 */
	private LinearLayout mLayoutModeInput;

	private TextView mTitle;
	
	/**
	 * 判断是否需要检测，防止不停的弹框
	 */
	private boolean mIsNeedCheck = true;

	private String mShareSn;
	
	public Handler getHandler() {
		return handler;
	}

	public CameraManager getCameraManager() {
		return cameraManager;
	}

	private boolean isHasSurface = false;

	static {
		System.loadLibrary("iconv");
	}

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);

		Window window = getWindow();
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.activity_qr_scan);

		findViewById(R.id.toolbar_back).setOnClickListener(this);
		
		mPreview1 = (LinearLayout)findViewById(R.id.layout_preview);
		mPreview2 = findViewById(R.id.view_preview_2);
		//
		mTextMode = (TextView)findViewById(R.id.textview_qr_mode);
		mTextMode.setOnClickListener(this);
		//
		mLayoutModeInput = (LinearLayout)findViewById(R.id.layout_qr_mode_input);
		
		mTitle = (TextView)findViewById(R.id.toolbar_title);

		mImageResult = (ImageView)findViewById(R.id.imageview_result);

		scanPreview = (SurfaceView) findViewById(R.id.capture_preview);
		
		scanCropView = (RelativeLayout) findViewById(R.id.capture_crop_view);
		ImageView scanLine = (ImageView) findViewById(R.id.capture_scan_line);
		mImageLight = (ImageView) findViewById(R.id.imageview_light);
		mImageLight.setOnClickListener(this);
		mEditSn = (EditText)findViewById(R.id.edittext_sn);
		
		findViewById(R.id.button_ok).setOnClickListener(this);

		inactivityTimer = new InactivityTimer(this);
		beepManager = new BeepManager(this);

		//扫描动画
		TranslateAnimation animation = new TranslateAnimation(
				Animation.RELATIVE_TO_PARENT, 0.0f,
				Animation.RELATIVE_TO_PARENT, 0.0f,
				Animation.RELATIVE_TO_PARENT, -1.0f,
				Animation.RELATIVE_TO_PARENT, 0.0f);
		animation.setDuration(2000);
		animation.setRepeatCount(-1);
		animation.setRepeatMode(Animation.RESTART);
		if (scanLine != null) {
			scanLine.startAnimation(animation);
		}
		setupData();
	}
	
	private void setupData(){
		int mode = getIntent().getIntExtra(KEY_INPUT_MODE, INPUT_MODE_QR);
		setChargeSnInputMode(mode);
	    if (getSharedPreferences(SHARED_KEY, Context.MODE_PRIVATE) == null) {
			mShareSn = "";
		} else {
			mShareSn = getSharedPreferences(SHARED_KEY, Context.MODE_PRIVATE).getString("pointSn", "");
		}
		mEditSn.setText(mShareSn);
	}

	@Override
	protected void onResume() {
		super.onResume();
		// CameraManager must be initialized here, not in onCreate(). This is
		// necessary because we don't
		// want to open the camera driver and measure the screen size if we're
		// going to show the help on
		// first launch. That led to bugs where the scanning rectangle was the
		// wrong size and partially
		// off screen.
		cameraManager = new CameraManager(getApplication());

		handler = null;

		if (isHasSurface) {
			// The activity was paused but not stopped, so the surface still
			// exists. Therefore
			// surfaceCreated() won't be called, so init the camera here.
			requestMyCamera(scanPreview.getHolder());
		} else {
			// Install the callback and wait for surfaceCreated() to init the
			// camera.
			scanPreview.getHolder().addCallback(this);
		}
		//		cameraManager.flashHandler();
		inactivityTimer.onResume();
	}

	@Override
	protected void onPause() {
		if (handler != null) {
			handler.quitSynchronously();
			handler = null;
		}
		inactivityTimer.onPause();
		beepManager.close();
		cameraManager.closeDriver();
		if (!isHasSurface) {
			scanPreview.getHolder().removeCallback(this);
		}
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		inactivityTimer.shutdown();
		super.onDestroy();
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		if (holder == null) {
			Log.e(TAG,
					"*** WARNING *** surfaceCreated() gave us a null surface!");
		}
		if (!isHasSurface) {
			isHasSurface = true;
			requestMyCamera(holder);
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		isHasSurface = false;
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {

	}
	
	private final static int REQUEST_CODE_CAMERA = 99;
	
	private void requestMyCamera(SurfaceHolder holder) {
		if(mIsNeedCheck){
			if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
				ActivityCompat.requestPermissions(this,
						new String[] { Manifest.permission.CAMERA },
						REQUEST_CODE_CAMERA);
			} else {
				initCamera(holder);
			}
		}		
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @Nullable String[] permissions, @Nullable int[] grantResults) {
		if (permissions != null && grantResults != null) {
			super.onRequestPermissionsResult(requestCode, permissions, grantResults);
			if (requestCode == REQUEST_CODE_CAMERA) {
				if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					// Permission Granted
					initCamera(scanPreview.getHolder());
				} else {
					// Permission Denied
					mIsNeedCheck = false;
				}
			}
		}
	}

	/**
	 * 处理zxing的解码结果
	 * @param rawResult 解码结果
	 * @param barcode 解码之后的灰阶图
	 */
	public void handleDecode(final Result rawResult,final Bitmap barcode) {
		inactivityTimer.onActivity();
		beepManager.playBeepSoundAndVibrate();

		handler.postDelayed(new Runnable() {

			@Override
			public void run() {
				//处理结果文本
				handleText(rawResult.getText());
				if(BuildConfig.DEBUG){
					//显示结果缩略图
					mImageResult.setVisibility(View.VISIBLE);
					mImageResult.setImageBitmap(barcode);	
				}
			}
		}, 800);
	}

	public void handleDecode(final String result){
		inactivityTimer.onActivity();
		beepManager.playBeepSoundAndVibrate();

		handler.postDelayed(new Runnable() {

			@Override
			public void run() {
				//处理结果文本
				handleText(result);
			}
		}, 800);
	}

	//处理扫码信息
	private void handleText(final String text) {
		//分析二维码内容版本
		String snTemp = text;
		if(text.length() != 12 ||
				text.contains("http://www.eastevs.com")){
			if(text.contains("sn=")){
				int begin = text.indexOf("sn=");
				begin = begin + 3;
				int end = text.indexOf("&", begin);
				if(end == -1)
					end = text.length();				
				snTemp = text.substring(begin, end);
			}
		}
		String sn = snTemp;
		commit(sn);
	}

	private void showErrorMessage(String msg){
		new AlertDialog.Builder(this)
		.setMessage(msg)
		.setPositiveButton("确定", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				restartPreviewAfterDelay(500);
			}
		})
		.create()
		.show();
	}

	private void initCamera(SurfaceHolder surfaceHolder) {
		if (surfaceHolder == null) {
			throw new IllegalStateException("No SurfaceHolder provided");
		}
		if (cameraManager.isOpen()) {
			Log.w(TAG,
					"initCamera() while already open -- late SurfaceView callback?");
			return;
		}
		try {
			cameraManager.openDriver(surfaceHolder);

			initCrop();

			// Creating the handler starts the preview, which can also throw a
			// RuntimeException.
			if (handler == null) {
				handler = new CaptureActivityHandler(this, cameraManager,
						DecodeThread.QRCODE_MODE);
			}
		} catch (IOException ioe) {
			Log.w(TAG, ioe);
			displayFrameworkBugMessageAndExit();
		} catch (RuntimeException e) {
			// Barcode Scanner has seen crashes in the wild of this variety:
			// java.?lang.?RuntimeException: Fail to connect to camera service
			Log.w(TAG, "Unexpected error initializing camera", e);
			displayFrameworkBugMessageAndExit();
		}
	}

	private void displayFrameworkBugMessageAndExit() {
		// camera error
		new AlertDialog.Builder(this)
		.setMessage("相机打开出错，请检查权限和手机相机配置！")
		.setPositiveButton("关闭", new DialogInterface.OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				finish();
			}
		})
		.setNeutralButton("手动输入", new DialogInterface.OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				setChargeSnInputMode(INPUT_MODE_TEXT);	
			}
		})
		.create()
		.show();
	}

	public void restartPreviewAfterDelay(long delayMS) {
		if (handler != null) {
				handler.sendEmptyMessageDelayed(R.id.restart_preview, delayMS);
		}
	}

	public Rect getCropRect() {
		return mCropRect;
	}

	/**
	 * 初始化截取的矩形区域
	 */
	private void initCrop() {
		//获取相机分辨率
		int cameraWidth = cameraManager.getCameraResolution().y;
		int cameraHeight = cameraManager.getCameraResolution().x;

		/** 获取布局中扫描框的位置信息 */
		int[] location = new int[2];
		scanCropView.getLocationInWindow(location);

		int cropLeft = location[0];
		int cropTop = location[1];

		int cropWidth = scanCropView.getWidth();
		int cropHeight = scanCropView.getHeight();

		/** 获取屏幕的宽高 */
		int containerWidth = scanPreview.getWidth();
		int containerHeight = scanPreview.getHeight();

		/** 计算最终截取的矩形的左上角顶点x坐标 */
		int x = cropLeft * cameraWidth / containerWidth;
		/** 计算最终截取的矩形的左上角顶点y坐标 */
		int y = cropTop * cameraHeight / containerHeight;

		/** 计算最终截取的矩形的宽度 */
		int width = cropWidth * cameraWidth / containerWidth;
		/** 计算最终截取的矩形的高度 */
		int height = cropHeight * cameraHeight / containerHeight;

		/** 生成最终的截取的矩形 */
		mCropRect = new Rect(x, y, width + x, height + y);
	}
	
	private void changeChargeSnInputMode(){
		if(mMode == INPUT_MODE_QR){
			setChargeSnInputMode(INPUT_MODE_TEXT);
		} else if(mMode == INPUT_MODE_TEXT){
			setChargeSnInputMode(INPUT_MODE_QR);
		}
	}
	
	private void setChargeSnInputMode(int mode){
		if(mode == INPUT_MODE_QR){
			mMode = INPUT_MODE_QR;
			mTitle.setText("扫描二维码");
			mTextMode.setText("无法识别？手动输入序列号");
			mLayoutModeInput.setVisibility(View.GONE);
			mPreview1.setVisibility(View.VISIBLE);
			mPreview2.setVisibility(View.GONE);
			//启动识别
		} else if(mode == INPUT_MODE_TEXT){
			mMode = INPUT_MODE_TEXT;
			mTitle.setText("输入序列号");
			mTextMode.setText("切换到扫描二维码模式");
			mLayoutModeInput.setVisibility(View.VISIBLE);
			mPreview1.setVisibility(View.INVISIBLE);
			mPreview2.setVisibility(View.VISIBLE);
			//停止识别			
		}
	}

	@Override
	public void onClick(View v) {
		int i = v.getId();
		if (i == R.id.textview_qr_mode) {
			changeChargeSnInputMode();

		} else if (i == R.id.toolbar_back) {
			finish();

		} else if (i == R.id.button_ok) {
			String sn = mEditSn.getText().toString();
			if (sn.equals("") || sn.length() != 12){
				showErrorMessage("请输入12位有效序列号");
			} else {
				commit(sn);
			}
		} else if (i == R.id.imageview_light) {
			if (!mIsLightOn) {
				//开灯
				if(cameraManager.turnOn()){
					mIsLightOn = true;
					mImageLight.setImageResource(R.drawable.btn_charging_flashlight_off);
				} else {
					Toast.makeText(this,"闪光灯打开失败",Toast.LENGTH_SHORT).show();
				}
			} else {
				//关灯
				if(cameraManager.turnOff()){
					mIsLightOn = false;
					mImageLight.setImageResource(R.drawable.btn_charging_flashlight_on);
				} else {
					Toast.makeText(this,"闪光灯关闭失败",Toast.LENGTH_SHORT).show();
				}
			}

		}
	}

	private void commit(String sn) {
		Intent intent = getIntent();
		if (sn.equals(mShareSn)) {
			intent.putExtra("isSame", true);
		} else {
			intent.putExtra("isSame", false);
		}
		intent.putExtra("sn", sn);
		setResult(RESULT_CODE, intent);
		finish();
	}

	public MyPlanarYUVLuminanceSource buildLuminanceSource(byte[] data, int width, int height) {
		//根据取景框 裁剪数据
		Rect rect = getCropRect();
		if (rect == null) {
			return null;
		}
		// Go ahead and assume it's YUV rather than die.
		return new MyPlanarYUVLuminanceSource(data, width, height, rect.left, rect.top, rect.width(), rect.height(), false);
	}

}