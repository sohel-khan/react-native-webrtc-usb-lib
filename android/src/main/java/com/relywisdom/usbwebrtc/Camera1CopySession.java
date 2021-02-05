package com.relywisdom.usbwebrtc;

import android.content.Context;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.SystemClock;
import android.view.WindowManager;

import org.webrtc.CameraEnumerationAndroid;
import org.webrtc.Logging;
import org.webrtc.NV21Buffer;
import org.webrtc.RendererCommon;
import org.webrtc.Size;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoFrame;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

class Camera1CopySession implements UsbCameraSession {
    private static final String TAG = "Camera1CopySession";
    private static final int NUMBER_OF_CAPTURE_BUFFERS = 3;
    private final Handler cameraThreadHandler;
    private final Events events;
    private final boolean captureToTexture;
    private final Context applicationContext;
    private final SurfaceTextureHelper surfaceTextureHelper;
    private final int cameraId;
    private final Camera camera;
    private final Camera.CameraInfo info;
    private final CameraEnumerationAndroid.CaptureFormat captureFormat;
    private final long constructionTimeNs;
    private Camera1CopySession.SessionState state;
    private boolean firstFrameReported = false;

    public static void create(CreateSessionCallback callback, Events events, boolean captureToTexture, Context applicationContext, SurfaceTextureHelper surfaceTextureHelper, MediaRecorder mediaRecorder, int cameraId, int width, int height, int framerate) {
        long constructionTimeNs = System.nanoTime();
        Logging.d("Camera1CopySession", "Open camera " + cameraId);
        events.onCameraOpening();

        Camera camera;
        try {
            camera = Camera.open(cameraId);
        } catch (RuntimeException var21) {
            callback.onFailure(FailureType.ERROR, var21.getMessage());
            return;
        }

        if (camera == null) {
            callback.onFailure(FailureType.ERROR, "android.hardware.Camera.open returned null for camera id = " + cameraId);
        } else {
            try {
                camera.setPreviewTexture(surfaceTextureHelper.getSurfaceTexture());
            } catch (RuntimeException | IOException var20) {
                camera.release();
                callback.onFailure(FailureType.ERROR, var20.getMessage());
                return;
            }

            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(cameraId, info);
            Camera.Parameters parameters = camera.getParameters();
            CameraEnumerationAndroid.CaptureFormat captureFormat = findClosestCaptureFormat(parameters, width, height, framerate);
            Size pictureSize = findClosestPictureSize(parameters, width, height);
            updateCameraParameters(camera, parameters, captureFormat, pictureSize, captureToTexture);
            if (!captureToTexture) {
                int frameSize = captureFormat.frameSize();

                for (int i = 0; i < 3; ++i) {
                    ByteBuffer buffer = ByteBuffer.allocateDirect(frameSize);
                    camera.addCallbackBuffer(buffer.array());
                }
            }

            camera.setDisplayOrientation(0);
            callback.onDone(new Camera1CopySession(events, captureToTexture, applicationContext, surfaceTextureHelper, mediaRecorder, cameraId, camera, info, captureFormat, constructionTimeNs));
        }
    }

    private static void updateCameraParameters(Camera camera, Camera.Parameters parameters, CameraEnumerationAndroid.CaptureFormat captureFormat, Size pictureSize, boolean captureToTexture) {
        List<String> focusModes = parameters.getSupportedFocusModes();
        parameters.setPreviewFpsRange(captureFormat.framerate.min, captureFormat.framerate.max);
        parameters.setPreviewSize(captureFormat.width, captureFormat.height);
        parameters.setPictureSize(pictureSize.width, pictureSize.height);
        if (!captureToTexture) {
            Objects.requireNonNull(captureFormat);
            parameters.setPreviewFormat(17);
        }

        if (parameters.isVideoStabilizationSupported()) {
            parameters.setVideoStabilization(true);
        }

        if (focusModes.contains("continuous-video")) {
            parameters.setFocusMode("continuous-video");
        }

        camera.setParameters(parameters);
    }

    private static CameraEnumerationAndroid.CaptureFormat findClosestCaptureFormat(Camera.Parameters parameters, int width, int height, int framerate) {
        List<CameraEnumerationAndroid.CaptureFormat.FramerateRange> supportedFramerates = UsbCameraEnumerator.convertFramerates(parameters.getSupportedPreviewFpsRange());
        Logging.d("Camera1CopySession", "Available fps ranges: " + supportedFramerates);
        CameraEnumerationAndroid.CaptureFormat.FramerateRange fpsRange = CameraEnumerationAndroid.getClosestSupportedFramerateRange(supportedFramerates, framerate);
        Size previewSize = CameraEnumerationAndroid.getClosestSupportedSize(UsbCameraEnumerator.convertSizes(parameters.getSupportedPreviewSizes()), width, height);
        //CameraEnumerationAndroid.reportCameraResolution(camera1ResolutionHistogram, previewSize);
        return new CameraEnumerationAndroid.CaptureFormat(previewSize.width, previewSize.height, fpsRange);
    }

    private static Size findClosestPictureSize(Camera.Parameters parameters, int width, int height) {
        return CameraEnumerationAndroid.getClosestSupportedSize(UsbCameraEnumerator.convertSizes(parameters.getSupportedPictureSizes()), width, height);
    }

    private Camera1CopySession(Events events, boolean captureToTexture, Context applicationContext, SurfaceTextureHelper surfaceTextureHelper, @Nullable MediaRecorder mediaRecorder, int cameraId, Camera camera, Camera.CameraInfo info, CameraEnumerationAndroid.CaptureFormat captureFormat, long constructionTimeNs) {
        Logging.d("Camera1CopySession", "Create new camera1 session on camera " + cameraId);
        this.cameraThreadHandler = new Handler();
        this.events = events;
        this.captureToTexture = captureToTexture;
        this.applicationContext = applicationContext;
        this.surfaceTextureHelper = surfaceTextureHelper;
        this.cameraId = cameraId;
        this.camera = camera;
        this.info = info;
        this.captureFormat = captureFormat;
        this.constructionTimeNs = constructionTimeNs;
        this.startCapturing();
        if (mediaRecorder != null) {
            camera.unlock();
            mediaRecorder.setCamera(camera);
        }

    }

    public void stop() {
        Logging.d("Camera1CopySession", "Stop camera1 session on camera " + this.cameraId);
        this.checkIsOnCameraThread();
        if (this.state != Camera1CopySession.SessionState.STOPPED) {
            long stopStartTime = System.nanoTime();
            this.stopInternal();
            int stopTimeMs = (int) TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - stopStartTime);
            ///camera1StopTimeMsHistogram.addSample(stopTimeMs);
        }

    }

    private void startCapturing() {
        Logging.d("Camera1CopySession", "Start capturing");
        this.checkIsOnCameraThread();
        this.state = Camera1CopySession.SessionState.RUNNING;
        this.camera.setErrorCallback(new Camera.ErrorCallback() {
            public void onError(int error, Camera camera) {
                String errorMessage;
                if (error == 100) {
                    errorMessage = "Camera server died!";
                } else {
                    errorMessage = "Camera error: " + error;
                }

                Logging.e("Camera1CopySession", errorMessage);
                Camera1CopySession.this.stopInternal();
                if (error == 2) {
                    Camera1CopySession.this.events.onCameraDisconnected(Camera1CopySession.this);
                } else {
                    Camera1CopySession.this.events.onCameraError(Camera1CopySession.this, errorMessage);
                }

            }
        });
        if (this.captureToTexture) {
            this.listenForTextureFrames();
        } else {
            this.listenForBytebufferFrames();
        }

        try {
            this.camera.startPreview();
        } catch (RuntimeException var2) {
            this.stopInternal();
            this.events.onCameraError(this, var2.getMessage());
        }

    }

    private void stopInternal() {
        Logging.d("Camera1CopySession", "Stop internal");
        this.checkIsOnCameraThread();
        if (this.state == Camera1CopySession.SessionState.STOPPED) {
            Logging.d("Camera1CopySession", "Camera is already stopped");
        } else {
            this.state = Camera1CopySession.SessionState.STOPPED;
            this.surfaceTextureHelper.stopListening();
            this.camera.stopPreview();
            this.camera.release();
            this.events.onCameraClosed(this);
            Logging.d("Camera1CopySession", "Stop done");
        }
    }

    private void listenForTextureFrames() {
        this.surfaceTextureHelper.startListening(new SurfaceTextureHelper.OnTextureFrameAvailableListener() {
            public void onTextureFrameAvailable(int oesTextureId, float[] transformMatrix, long timestampNs) {
                Camera1CopySession.this.checkIsOnCameraThread();
                if (Camera1CopySession.this.state != Camera1CopySession.SessionState.RUNNING) {
                    Logging.d("Camera1CopySession", "Texture frame captured but camera is no longer running.");
                    Camera1CopySession.this.surfaceTextureHelper.returnTextureFrame();
                } else {
                    int rotation;
                    if (!Camera1CopySession.this.firstFrameReported) {
                        rotation = (int) TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - Camera1CopySession.this.constructionTimeNs);
                        //Camera1CopySession.camera1StartTimeMsHistogram.addSample(rotation);
                        Camera1CopySession.this.firstFrameReported = true;
                    }

                    rotation = Camera1CopySession.this.getFrameOrientation();
                    if (Camera1CopySession.this.info.facing == 1) {
                        transformMatrix = RendererCommon.multiplyMatrices(transformMatrix, RendererCommon.horizontalFlipMatrix());
                    }

                    VideoFrame.Buffer buffer = Camera1CopySession.this.surfaceTextureHelper.createTextureBuffer(Camera1CopySession.this.captureFormat.width, Camera1CopySession.this.captureFormat.height, RendererCommon.convertMatrixToAndroidGraphicsMatrix(transformMatrix));
                    VideoFrame frame = new VideoFrame(buffer, rotation, timestampNs);
                    Camera1CopySession.this.events.onFrameCaptured(Camera1CopySession.this, frame);
                    frame.release();
                }
            }
        });
    }

    private void listenForBytebufferFrames() {
        this.camera.setPreviewCallbackWithBuffer(new Camera.PreviewCallback() {
            public void onPreviewFrame(byte[] data, Camera callbackCamera) {
                Camera1CopySession.this.checkIsOnCameraThread();
                if (callbackCamera != Camera1CopySession.this.camera) {
                    Logging.e("Camera1CopySession", "Callback from a different camera. This should never happen.");
                } else if (Camera1CopySession.this.state != Camera1CopySession.SessionState.RUNNING) {
                    Logging.d("Camera1CopySession", "Bytebuffer frame captured but camera is no longer running.");
                } else {
                    long captureTimeNs = TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime());
                    if (!Camera1CopySession.this.firstFrameReported) {
                        int startTimeMs = (int) TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - Camera1CopySession.this.constructionTimeNs);
                        //Camera1CopySession.camera1StartTimeMsHistogram.addSample(startTimeMs);
                        Camera1CopySession.this.firstFrameReported = true;
                    }

                    VideoFrame.Buffer frameBuffer = new NV21Buffer(data, Camera1CopySession.this.captureFormat.width, Camera1CopySession.this.captureFormat.height, () -> {
                        Camera1CopySession.this.cameraThreadHandler.post(() -> {
                            if (Camera1CopySession.this.state == Camera1CopySession.SessionState.RUNNING) {
                                Camera1CopySession.this.camera.addCallbackBuffer(data);
                            }

                        });
                    });
                    VideoFrame frame = new VideoFrame(frameBuffer, Camera1CopySession.this.getFrameOrientation(), captureTimeNs);
                    Camera1CopySession.this.events.onFrameCaptured(Camera1CopySession.this, frame);
                    frame.release();
                }
            }
        });
    }

    private int getDeviceOrientation() {
        WindowManager wm = (WindowManager) this.applicationContext.getSystemService(Context.WINDOW_SERVICE);
        int orientation = 0;
        switch (wm.getDefaultDisplay().getRotation()) {
            case 0:
            default:
                orientation = 0;
                break;
            case 1:
                orientation = 90;
                break;
            case 2:
                orientation = 180;
                break;
            case 3:
                orientation = 270;
        }

        return orientation;
    }

    private int getFrameOrientation() {
        int rotation = this.getDeviceOrientation();
        if (this.info.facing == 0) {
            rotation = 360 - rotation;
        }

        return (this.info.orientation + rotation) % 360;
    }

    private void checkIsOnCameraThread() {
        if (Thread.currentThread() != this.cameraThreadHandler.getLooper().getThread()) {
            throw new IllegalStateException("Wrong thread");
        }
    }

    private static enum SessionState {
        RUNNING,
        STOPPED;

        private SessionState() {
        }
    }
}
