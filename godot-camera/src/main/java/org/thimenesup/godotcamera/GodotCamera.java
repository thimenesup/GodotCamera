package org.thimenesup.godotcamera;

import org.godotengine.godot.Godot;
import org.godotengine.godot.GodotLib;
import org.godotengine.godot.plugin.GodotPlugin;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Context;

import android.hardware.Camera; //developer.android.com/reference/android/hardware/Camera.html#setPreviewCallback(android.hardware.Camera.PreviewCallback)
import android.graphics.SurfaceTexture; //developer.android.com/reference/android/graphics/SurfaceTexture.html

import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;


public class GodotCamera extends GodotPlugin {

    public static final String TAG = "GodotCamera";

    Godot godot = null;
    Context context = null;

    int callbackObjectID = 0;

    Camera camera = null;
    SurfaceTexture previewTexture = new SurfaceTexture(0);

    RenderScript renderScript = null;
    ScriptIntrinsicYuvToRGB yuvToRgbIntrinsic = null;
    Type.Builder yuvType = null;
    Allocation scriptInput = null;
    Type.Builder rgbaType = null;
    Allocation scriptOutput = null;
    byte[] rgbData = null;


    public GodotCamera(Godot godot) {
        super(godot);
        this.godot = godot;

        context = getActivity().getApplicationContext();
    }

    @Override
    public String getPluginName() {
        return "GodotCamera";
    }

    @Override
    public List<String> getPluginMethods() {
        List<String> methods = new ArrayList<>();

        methods.add("init");

        methods.add("setCallbackObject");
        methods.add("initializeCamera");
        methods.add("initializeCapture");
        methods.add("finalizeCapture");
        methods.add("getCameraCount");
        methods.add("setPreviewSize");
        methods.add("setPreviewFormat");
        methods.add("getPreviewSize");
        methods.add("getPreviewFramerate");
        methods.add("getPreviewFormat");
        methods.add("getSupportedPreviewFormats");

        return methods;
    }

    public void init(final int scriptID)
    {

    }

    public void setCallbackObject(int callbackID)
    {
        callbackObjectID = callbackID;
    }

    public int initializeCamera(int cameraID)
    {
        int success = 0;
        try {
            camera = Camera.open(cameraID);
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

        renderScript = RenderScript.create(context);
        yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(renderScript, Element.U8_4(renderScript));
        {
            Camera.Parameters parameters = camera.getParameters();
            Camera.Size size = parameters.getPreviewSize();
            setupCallbackBuffers(size.width, size.height);
        }

        Camera.PreviewCallback previewCallback = new Camera.PreviewCallback()
        {
            public void onPreviewFrame(byte[] data, Camera camera)
            {
                //Convert from YUV to RGBA
                scriptInput.copyFrom(data);
                yuvToRgbIntrinsic.setInput(scriptInput);
                yuvToRgbIntrinsic.forEach(scriptOutput);
                scriptOutput.copyTo(rgbData);

                Camera.Parameters parameters = camera.getParameters();
                Camera.Size size = parameters.getPreviewSize();
                
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
        return Camera.getNumberOfCameras();
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


    private void setupCallbackBuffers(int width, int height)
    {
        int yuvBufferLength = width * height * 3 / 2; // 12 bits per pixel
        yuvType = new Type.Builder(renderScript, Element.U8(renderScript)).setX(yuvBufferLength);
        scriptInput = Allocation.createTyped(renderScript, yuvType.create(), Allocation.USAGE_SCRIPT);

        rgbaType = new Type.Builder(renderScript, Element.RGBA_8888(renderScript)).setX(width).setY(height);
        scriptOutput = Allocation.createTyped(renderScript, rgbaType.create(), Allocation.USAGE_SCRIPT);

        rgbData = new byte[width * height * 4];
    }

}
