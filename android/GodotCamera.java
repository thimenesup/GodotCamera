package org.godotengine.godot;

import android.app.Activity;
import android.content.Context;

import com.godot.game.R;

import android.hardware.Camera; //developer.android.com/reference/android/hardware/Camera.html#setPreviewCallback(android.hardware.Camera.PreviewCallback)
import android.graphics.SurfaceTexture; //developer.android.com/reference/android/graphics/SurfaceTexture.html

import android.graphics.ImageFormat;

import java.util.List;

import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;


public class GodotCamera extends Godot.SingletonBase {

	static public Godot.SingletonBase initialize(Activity p_activity) {
		return new GodotCamera(p_activity);
	}

	public GodotCamera(Activity p_activity) {
		activity = (Godot)p_activity;
		context = activity.getApplicationContext();

		//register class name and functions to bind
		registerClass("GodotCamera", new String[]{"setCallbackObject", "initializeCamera", "initializeCapture", "finalizeCapture", 
		"getCameraCount", "setPreviewSize", "setPreviewFormat", "getPreviewSize", "getPreviewFramerate", "getPreviewFormat", "getSupportedPreviewFormats"});
	}

	Godot activity;
	Context context;
	
	private int callbackObjectID = 0;

	public void setCallbackObject(int callbackID)
	{
		callbackObjectID = callbackID;
	}


	Camera camera;
	SurfaceTexture previewTexture = new SurfaceTexture(0); //No idea what does the argument

	RenderScript rs;

	public int initializeCamera(int cameraId)
	{
		int success = 0;
		try {
			camera = Camera.open(cameraId);
			success = 1;
		}
		catch (Exception e) {
			GodotLib.calldeferred(callbackObjectID, "_on_exception", new String[]{e.toString()});
		}
		return success;
	}

	public void initializeCapture()
	{
		if (camera == null)
			return;
		
		try {
			camera.setPreviewTexture(previewTexture);
		}
		catch (Exception e) {
			GodotLib.calldeferred(callbackObjectID, "_on_exception", new String[]{e.toString()});
		}	

		rs = RenderScript.create(context);

		Camera.PreviewCallback previewCallback = new Camera.PreviewCallback()  
    	{ 
            public void onPreviewFrame(byte[] data, Camera camera)  
            { 
				Camera.Parameters parameters = camera.getParameters();
				Camera.Size size = parameters.getPreviewSize();
				byte[] rgbData = yuv2rgb(data, size.width, size.height);
				GodotLib.calldeferred(callbackObjectID, "_on_captured_data", new Object[]{rgbData, size.width, size.height});
            } 
    	};
		
		camera.setPreviewCallback(previewCallback);
		
		camera.startPreview();
	}

	public void finalizeCapture()
	{
		if (camera != null){
			camera.stopPreview();
			camera.release();
			camera = null;
		}
	}


	public int getCameraCount() 
	{
		if (camera == null)
			return -1;
		return camera.getNumberOfCameras();
	}

	public void setPreviewSize(int width, int height)
	{
		if (camera == null)
			return;
		
		Camera.Parameters parameters = camera.getParameters();
		parameters.setPreviewSize(width, height);
		camera.setParameters(parameters);
	}

	public void setPreviewFormat(int format) //Not all devices support all formats
	{
		if (camera == null)
			return;
		
		Camera.Parameters parameters = camera.getParameters();
		parameters.setPreviewFormat(format);
		camera.setParameters(parameters);
	}

	public int[] getPreviewSize()
	{
		if (camera == null)
			return new int[]{0,0};
		
		Camera.Parameters parameters = camera.getParameters();
		Camera.Size size = parameters.getPreviewSize();
		return new int[]{size.width,size.height};
	}

	public int getPreviewFramerate()
	{
		if (camera == null)
			return -1;
		
		Camera.Parameters parameters = camera.getParameters();
		return parameters.getPreviewFrameRate();
	}

	public int getPreviewFormat()
	{
		if (camera == null)
			return -1;
		
		Camera.Parameters parameters = camera.getParameters();
		return parameters.getPreviewFormat();
	}

	public int[] getSupportedPreviewFormats()
	{
		if (camera == null)
			return new int[]{0};
		
		Camera.Parameters parameters = camera.getParameters();
		List<Integer> formatList = parameters.getSupportedPreviewFormats();
		int[] formats = new int[formatList.size()];
		for (int i = 0; i < formatList.size(); ++i){
			formats[i] = (int)formatList.get(i);
		}
		return formats;
	}

	
	private byte[] yuv2rgb(byte[] yuvData, int w, int h)
	{
		ScriptIntrinsicYuvToRGB yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs));

		Type.Builder yuvType = new Type.Builder(rs, Element.U8(rs)).setX(yuvData.length);
		Allocation in = Allocation.createTyped(rs, yuvType.create(), Allocation.USAGE_SCRIPT);

		Type.Builder rgbaType = new Type.Builder(rs, Element.RGBA_8888(rs)).setX(w).setY(h);
		Allocation out = Allocation.createTyped(rs, rgbaType.create(), Allocation.USAGE_SCRIPT);

		in.copyFrom(yuvData);

		yuvToRgbIntrinsic.setInput(in);
		yuvToRgbIntrinsic.forEach(out);

		byte[] outBytes = new byte[w * h * 4];
		out.copyTo(outBytes);

		return outBytes;
	}
	

}