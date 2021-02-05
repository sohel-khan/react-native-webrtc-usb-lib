package com.relywisdom.usbwebrtc;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.usb.UsbManager;
import android.os.SystemClock;

import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraEnumerationAndroid;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.Logging;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;

public class UsbCameraEnumerator implements CameraEnumerator {
    private final UsbManager mUsbManager;

    public  UsbCameraEnumerator(Context context) {
        mUsbManager = (UsbManager)context.getSystemService(Context.USB_SERVICE);
    }

    public static final String cameraName = "usbCamera";

    @Override
    public String[] getDeviceNames() {
        ArrayList<String> namesList = new ArrayList();

        for(int i = 0; i < Camera.getNumberOfCameras(); ++i) {
            String name = getDeviceName(i);
            if (name != null) {
                namesList.add(name);
                Logging.d("Camera1Enumerator", "Index: " + i + ". " + name);
            } else {
                Logging.e("Camera1Enumerator", "Index: " + i + ". Failed to query camera name.");
            }
        }


        if (hasUsbDevice()) namesList.add(cameraName);

        String[] namesArray = new String[namesList.size()];
        return (String[])namesList.toArray(namesArray);
    }

    public boolean hasUsbDevice() {
        if (mUsbManager.getDeviceList().size() > 0) return true;
        return false;
    }

    @Override
    public boolean isFrontFacing(String deviceName) {
        if (cameraName.equals(deviceName)) return false;
        Camera.CameraInfo info = getCameraInfo(getCameraIndex(deviceName));
        return info != null && info.facing == 1;
    }

    @Override
    public boolean isBackFacing(String deviceName) {
        if (cameraName.equals(deviceName)) return false;
        Camera.CameraInfo info = getCameraInfo(getCameraIndex(deviceName));
        return info != null && info.facing == 0;
    }

    @Override
    public List<CameraEnumerationAndroid.CaptureFormat> getSupportedFormats(String deviceName) {
        if (cameraName.equals(deviceName)) return new ArrayList<>();
        return getSupportedFormats(getCameraIndex(deviceName));
    }

    @Override
    public CameraVideoCapturer createCapturer(String deviceName, CameraVideoCapturer.CameraEventsHandler eventsHandler) {
        return new UsbCameraCapturer(deviceName, eventsHandler, this);
    }

    private static List<List<CameraEnumerationAndroid.CaptureFormat>> cachedSupportedFormats;

    static synchronized List<CameraEnumerationAndroid.CaptureFormat> getSupportedFormats(int cameraId) {
        if (cachedSupportedFormats == null) {
            cachedSupportedFormats = new ArrayList();

            for(int i = 0; i < Camera.getNumberOfCameras(); ++i) {
                cachedSupportedFormats.add(enumerateFormats(i));
            }
        }

        return (List)cachedSupportedFormats.get(cameraId);
    }

    private static List<CameraEnumerationAndroid.CaptureFormat> enumerateFormats(int cameraId) {
        Logging.d("Camera1Enumerator", "Get supported formats for camera index " + cameraId + ".");
        long startTimeMs = SystemClock.elapsedRealtime();
        Camera camera = null;

        Camera.Parameters parameters;
        label94: {
            ArrayList var6;
            try {
                Logging.d("Camera1Enumerator", "Opening camera with index " + cameraId);
                camera = Camera.open(cameraId);
                parameters = camera.getParameters();
                break label94;
            } catch (RuntimeException var15) {
                Logging.e("Camera1Enumerator", "Open camera failed on camera index " + cameraId, var15);
                var6 = new ArrayList();
            } finally {
                if (camera != null) {
                    camera.release();
                }

            }

            return var6;
        }

        ArrayList formatList = new ArrayList();

        try {
            int minFps = 0;
            int maxFps = 0;
            List<int[]> listFpsRange = parameters.getSupportedPreviewFpsRange();
            if (listFpsRange != null) {
                int[] range = (int[])listFpsRange.get(listFpsRange.size() - 1);
                minFps = range[0];
                maxFps = range[1];
            }

            Iterator var19 = parameters.getSupportedPreviewSizes().iterator();

            while(var19.hasNext()) {
                Camera.Size size = (Camera.Size)var19.next();
                formatList.add(new CameraEnumerationAndroid.CaptureFormat(size.width, size.height, minFps, maxFps));
            }
        } catch (Exception var14) {
            Logging.e("Camera1Enumerator", "getSupportedFormats() failed on camera index " + cameraId, var14);
        }

        long endTimeMs = SystemClock.elapsedRealtime();
        Logging.d("Camera1Enumerator", "Get supported formats for camera index " + cameraId + " done. Time spent: " + (endTimeMs - startTimeMs) + " ms.");
        return formatList;
    }

    static List<org.webrtc.Size> convertSizes(List<Camera.Size> cameraSizes) {
        List<org.webrtc.Size> sizes = new ArrayList();
        Iterator var2 = cameraSizes.iterator();

        while(var2.hasNext()) {
            Camera.Size size = (Camera.Size)var2.next();
            sizes.add(new org.webrtc.Size(size.width, size.height));
        }

        return sizes;
    }

    static List<CameraEnumerationAndroid.CaptureFormat.FramerateRange> convertFramerates(List<int[]> arrayRanges) {
        List<CameraEnumerationAndroid.CaptureFormat.FramerateRange> ranges = new ArrayList();
        Iterator var2 = arrayRanges.iterator();

        while(var2.hasNext()) {
            int[] range = (int[])var2.next();
            ranges.add(new CameraEnumerationAndroid.CaptureFormat.FramerateRange(range[0], range[1]));
        }

        return ranges;
    }

    static int getCameraIndex(String deviceName) {
        Logging.d("Camera1Enumerator", "getCameraIndex: " + deviceName);
        if (cameraName.equals(deviceName)) return Camera.getNumberOfCameras();

        for(int i = 0; i < Camera.getNumberOfCameras(); ++i) {
            if (deviceName.equals(getDeviceName(i))) {
                return i;
            }
        }

        throw new IllegalArgumentException("No such camera: " + deviceName);
    }

    @Nullable
    static String getDeviceName(int index) {
        if (index == Camera.getNumberOfCameras()) return cameraName;
        Camera.CameraInfo info = getCameraInfo(index);
        if (info == null) {
            return null;
        } else {
            String facing = info.facing == 1 ? "front" : "back";
            return "Camera " + index + ", Facing " + facing + ", Orientation " + info.orientation;
        }
    }

    @Nullable
    private static Camera.CameraInfo getCameraInfo(int index) {
        Camera.CameraInfo info = new Camera.CameraInfo();

        try {
            Camera.getCameraInfo(index, info);
            return info;
        } catch (Exception var3) {
            Logging.e("Camera1Enumerator", "getCameraInfo failed on index " + index, var3);
            return null;
        }
    }
}
