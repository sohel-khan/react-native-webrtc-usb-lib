package com.relywisdom.usbwebrtc;

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//


import android.content.Context;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;

import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.Logging;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoFrame;

import java.util.Arrays;

import javax.annotation.Nullable;


class UsbCameraCapturer implements CameraVideoCapturer {
    private static final String TAG = "UsbCameraCapturer";
    private static final int MAX_OPEN_CAMERA_ATTEMPTS = 3;
    private static final int OPEN_CAMERA_DELAY_MS = 500;
    private static final int OPEN_CAMERA_TIMEOUT = 10000;
    private final CameraEnumerator cameraEnumerator;
    @Nullable
    private final CameraEventsHandler eventsHandler;
    private final Handler uiThreadHandler;
    @Nullable
    private final UsbCameraSession.CreateSessionCallback createSessionCallback = new UsbCameraSession.CreateSessionCallback() {
        public void onDone(UsbCameraSession session) {
            UsbCameraCapturer.this.checkIsOnCameraThread();
            Logging.d("UsbCameraCapturer", "Create session done. Switch state: " + UsbCameraCapturer.this.switchState + ". MediaRecorder state: " + UsbCameraCapturer.this.mediaRecorderState);
            UsbCameraCapturer.this.uiThreadHandler.removeCallbacks(UsbCameraCapturer.this.openCameraTimeoutRunnable);
            synchronized(UsbCameraCapturer.this.stateLock) {
                UsbCameraCapturer.this.capturerObserver.onCapturerStarted(true);
                UsbCameraCapturer.this.sessionOpening = false;
                UsbCameraCapturer.this.currentSession = session;
                UsbCameraCapturer.this.cameraStatistics = new CameraStatistics(UsbCameraCapturer.this.surfaceHelper, UsbCameraCapturer.this.eventsHandler);
                UsbCameraCapturer.this.firstFrameObserved = false;
                UsbCameraCapturer.this.stateLock.notifyAll();
                if (UsbCameraCapturer.this.switchState == UsbCameraCapturer.SwitchState.IN_PROGRESS) {
                    if (UsbCameraCapturer.this.switchEventsHandler != null) {
                        UsbCameraCapturer.this.switchEventsHandler.onCameraSwitchDone(UsbCameraCapturer.this.cameraEnumerator.isFrontFacing(UsbCameraCapturer.this.cameraName));
                        UsbCameraCapturer.this.switchEventsHandler = null;
                    }

                    UsbCameraCapturer.this.switchState = UsbCameraCapturer.SwitchState.IDLE;
                } else if (UsbCameraCapturer.this.switchState == UsbCameraCapturer.SwitchState.PENDING) {
                    UsbCameraCapturer.this.switchState = UsbCameraCapturer.SwitchState.IDLE;
                    UsbCameraCapturer.this.switchCameraInternal(UsbCameraCapturer.this.switchEventsHandler);
                }

                if (UsbCameraCapturer.this.mediaRecorderState == UsbCameraCapturer.MediaRecorderState.IDLE_TO_ACTIVE || UsbCameraCapturer.this.mediaRecorderState == UsbCameraCapturer.MediaRecorderState.ACTIVE_TO_IDLE) {
                    if (UsbCameraCapturer.this.mediaRecorderEventsHandler != null) {
                        UsbCameraCapturer.this.mediaRecorderEventsHandler.onMediaRecorderSuccess();
                        UsbCameraCapturer.this.mediaRecorderEventsHandler = null;
                    }

                    if (UsbCameraCapturer.this.mediaRecorderState == UsbCameraCapturer.MediaRecorderState.IDLE_TO_ACTIVE) {
                        UsbCameraCapturer.this.mediaRecorderState = UsbCameraCapturer.MediaRecorderState.ACTIVE;
                    } else {
                        UsbCameraCapturer.this.mediaRecorderState = UsbCameraCapturer.MediaRecorderState.IDLE;
                    }
                }

            }
        }

        public void onFailure(UsbCameraSession.FailureType failureType, String error) {
            UsbCameraCapturer.this.checkIsOnCameraThread();
            UsbCameraCapturer.this.uiThreadHandler.removeCallbacks(UsbCameraCapturer.this.openCameraTimeoutRunnable);
            synchronized(UsbCameraCapturer.this.stateLock) {
                UsbCameraCapturer.this.capturerObserver.onCapturerStarted(false);
                UsbCameraCapturer.this.openAttemptsRemaining--;
                if (UsbCameraCapturer.this.openAttemptsRemaining <= 0) {
                    Logging.w("UsbCameraCapturer", "Opening camera failed, passing: " + error);
                    UsbCameraCapturer.this.sessionOpening = false;
                    UsbCameraCapturer.this.stateLock.notifyAll();
                    if (UsbCameraCapturer.this.switchState != UsbCameraCapturer.SwitchState.IDLE) {
                        if (UsbCameraCapturer.this.switchEventsHandler != null) {
                            UsbCameraCapturer.this.switchEventsHandler.onCameraSwitchError(error);
                            UsbCameraCapturer.this.switchEventsHandler = null;
                        }

                        UsbCameraCapturer.this.switchState = UsbCameraCapturer.SwitchState.IDLE;
                    }

                    if (UsbCameraCapturer.this.mediaRecorderState != UsbCameraCapturer.MediaRecorderState.IDLE) {
                        if (UsbCameraCapturer.this.mediaRecorderEventsHandler != null) {
                            UsbCameraCapturer.this.mediaRecorderEventsHandler.onMediaRecorderError(error);
                            UsbCameraCapturer.this.mediaRecorderEventsHandler = null;
                        }

                        UsbCameraCapturer.this.mediaRecorderState = UsbCameraCapturer.MediaRecorderState.IDLE;
                    }

                    if (failureType == UsbCameraSession.FailureType.DISCONNECTED) {
                        UsbCameraCapturer.this.eventsHandler.onCameraDisconnected();
                    } else {
                        UsbCameraCapturer.this.eventsHandler.onCameraError(error);
                    }
                } else {
                    Logging.w("UsbCameraCapturer", "Opening camera failed, retry: " + error);
                    UsbCameraCapturer.this.createSessionInternal(500, (MediaRecorder)null);
                }

            }
        }
    };
    @Nullable
    private final UsbCameraSession.Events cameraSessionEventsHandler = new UsbCameraSession.Events() {
        public void onCameraOpening() {
            UsbCameraCapturer.this.checkIsOnCameraThread();
            synchronized(UsbCameraCapturer.this.stateLock) {
                if (UsbCameraCapturer.this.currentSession != null) {
                    Logging.w("UsbCameraCapturer", "onCameraOpening while session was open.");
                } else {
                    UsbCameraCapturer.this.eventsHandler.onCameraOpening(UsbCameraCapturer.this.cameraName);
                }
            }
        }

        public void onCameraError(UsbCameraSession session, String error) {
            UsbCameraCapturer.this.checkIsOnCameraThread();
            synchronized(UsbCameraCapturer.this.stateLock) {
                if (session != UsbCameraCapturer.this.currentSession) {
                    Logging.w("UsbCameraCapturer", "onCameraError from another session: " + error);
                } else {
                    UsbCameraCapturer.this.eventsHandler.onCameraError(error);
                    UsbCameraCapturer.this.stopCapture();
                }
            }
        }

        public void onCameraDisconnected(UsbCameraSession session) {
            UsbCameraCapturer.this.checkIsOnCameraThread();
            synchronized(UsbCameraCapturer.this.stateLock) {
                if (session != UsbCameraCapturer.this.currentSession) {
                    Logging.w("UsbCameraCapturer", "onCameraDisconnected from another session.");
                } else {
                    UsbCameraCapturer.this.eventsHandler.onCameraDisconnected();
                    UsbCameraCapturer.this.stopCapture();
                }
            }
        }

        public void onCameraClosed(UsbCameraSession session) {
            UsbCameraCapturer.this.checkIsOnCameraThread();
            synchronized(UsbCameraCapturer.this.stateLock) {
                if (session != UsbCameraCapturer.this.currentSession && UsbCameraCapturer.this.currentSession != null) {
                    Logging.d("UsbCameraCapturer", "onCameraClosed from another session.");
                } else {
                    UsbCameraCapturer.this.eventsHandler.onCameraClosed();
                }
            }
        }

        public void onFrameCaptured(UsbCameraSession session, VideoFrame frame) {
            UsbCameraCapturer.this.checkIsOnCameraThread();
            synchronized(UsbCameraCapturer.this.stateLock) {
                if (session != UsbCameraCapturer.this.currentSession) {
                    Logging.w("UsbCameraCapturer", "onTextureFrameCaptured from another session.");
                } else {
                    if (!UsbCameraCapturer.this.firstFrameObserved) {
                        UsbCameraCapturer.this.eventsHandler.onFirstFrameAvailable();
                        UsbCameraCapturer.this.firstFrameObserved = true;
                    }

                    UsbCameraCapturer.this.cameraStatistics.addFrame();
                    UsbCameraCapturer.this.capturerObserver.onFrameCaptured(frame);
                }
            }
        }
    };
    private final Runnable openCameraTimeoutRunnable = new Runnable() {
        public void run() {
            UsbCameraCapturer.this.eventsHandler.onCameraError("Camera failed to start within timeout.");
        }
    };
    @Nullable
    private Handler cameraThreadHandler;
    private Context applicationContext;
    private CapturerObserver capturerObserver;
    @Nullable
    private SurfaceTextureHelper surfaceHelper;
    private final Object stateLock = new Object();
    private boolean sessionOpening;
    @Nullable
    private UsbCameraSession currentSession;
    private String cameraName;
    private int width;
    private int height;
    private int framerate;
    private int openAttemptsRemaining;
    private UsbCameraCapturer.SwitchState switchState;
    @Nullable
    private CameraSwitchHandler switchEventsHandler;
    @Nullable
    private CameraStatistics cameraStatistics;
    private boolean firstFrameObserved;
    private UsbCameraCapturer.MediaRecorderState mediaRecorderState;
    @Nullable
    private MediaRecorderHandler mediaRecorderEventsHandler;

    public UsbCameraCapturer(String cameraName, @Nullable CameraEventsHandler eventsHandler, CameraEnumerator cameraEnumerator) {
        this.switchState = UsbCameraCapturer.SwitchState.IDLE;
        this.mediaRecorderState = UsbCameraCapturer.MediaRecorderState.IDLE;
        if (eventsHandler == null) {
            eventsHandler = new CameraEventsHandler() {
                public void onCameraError(String errorDescription) {
                }

                public void onCameraDisconnected() {
                }

                public void onCameraFreezed(String errorDescription) {
                }

                public void onCameraOpening(String cameraName) {
                }

                public void onFirstFrameAvailable() {
                }

                public void onCameraClosed() {
                }
            };
        }

        this.eventsHandler = eventsHandler;
        this.cameraEnumerator = cameraEnumerator;
        this.cameraName = cameraName;
        this.uiThreadHandler = new Handler(Looper.getMainLooper());
        String[] deviceNames = cameraEnumerator.getDeviceNames();
        if (deviceNames.length == 0) {
            throw new RuntimeException("No cameras attached.");
        } else if (!Arrays.asList(deviceNames).contains(this.cameraName)) {
            throw new IllegalArgumentException("Camera name " + this.cameraName + " does not match any known camera device.");
        }
    }

    public void initialize(@Nullable SurfaceTextureHelper surfaceTextureHelper, Context applicationContext, CapturerObserver capturerObserver) {
        this.applicationContext = applicationContext;
        this.capturerObserver = capturerObserver;
        this.surfaceHelper = surfaceTextureHelper;
        this.cameraThreadHandler = surfaceTextureHelper == null ? null : surfaceTextureHelper.getHandler();
    }

    public void startCapture(int width, int height, int framerate) {
        Logging.d("UsbCameraCapturer", "startCapture: " + width + "x" + height + "@" + framerate);
        if (this.applicationContext == null) {
            throw new RuntimeException("UsbCameraCapturer must be initialized before calling startCapture.");
        } else {
            Object var4 = this.stateLock;
            synchronized(this.stateLock) {
                if (!this.sessionOpening && this.currentSession == null) {
                    this.width = width;
                    this.height = height;
                    this.framerate = framerate;
                    this.sessionOpening = true;
                    this.openAttemptsRemaining = 3;
                    this.createSessionInternal(0, (MediaRecorder)null);
                } else {
                    Logging.w("UsbCameraCapturer", "Session already open");
                }
            }
        }
    }

    private void createSessionInternal(int delayMs, final MediaRecorder mediaRecorder) {
        this.uiThreadHandler.postDelayed(this.openCameraTimeoutRunnable, (long)(delayMs + 10000));
        this.cameraThreadHandler.postDelayed(new Runnable() {
            public void run() {
                UsbCameraCapturer.this.createCameraSession(UsbCameraCapturer.this.createSessionCallback, UsbCameraCapturer.this.cameraSessionEventsHandler, UsbCameraCapturer.this.applicationContext, UsbCameraCapturer.this.surfaceHelper, mediaRecorder, UsbCameraCapturer.this.cameraName, UsbCameraCapturer.this.width, UsbCameraCapturer.this.height, UsbCameraCapturer.this.framerate);
            }
        }, (long)delayMs);
    }

    public void stopCapture() {
        Logging.d("UsbCameraCapturer", "Stop capture");
        Object var1 = this.stateLock;
        synchronized(this.stateLock) {
            while(this.sessionOpening) {
                Logging.d("UsbCameraCapturer", "Stop capture: Waiting for session to open");

                try {
                    this.stateLock.wait();
                } catch (InterruptedException var4) {
                    Logging.w("UsbCameraCapturer", "Stop capture interrupted while waiting for the session to open.");
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            if (this.currentSession != null) {
                Logging.d("UsbCameraCapturer", "Stop capture: Nulling session");
                this.cameraStatistics.release();
                this.cameraStatistics = null;
                final UsbCameraSession oldSession = this.currentSession;
                this.cameraThreadHandler.post(new Runnable() {
                    public void run() {
                        oldSession.stop();
                    }
                });
                this.currentSession = null;
                this.capturerObserver.onCapturerStopped();
            } else {
                Logging.d("UsbCameraCapturer", "Stop capture: No session open");
            }
        }

        Logging.d("UsbCameraCapturer", "Stop capture done");
    }

    public void changeCaptureFormat(int width, int height, int framerate) {
        Logging.d("UsbCameraCapturer", "changeCaptureFormat: " + width + "x" + height + "@" + framerate);
        Object var4 = this.stateLock;
        synchronized(this.stateLock) {
            this.stopCapture();
            this.startCapture(width, height, framerate);
        }
    }

    public void dispose() {
        Logging.d("UsbCameraCapturer", "dispose");
        this.stopCapture();
    }

    public void switchCamera(final CameraSwitchHandler switchEventsHandler) {
        Logging.d("UsbCameraCapturer", "switchCamera");
        this.cameraThreadHandler.post(new Runnable() {
            public void run() {
                UsbCameraCapturer.this.switchCameraInternal(switchEventsHandler);
            }
        });
    }

    public void addMediaRecorderToCamera(final MediaRecorder mediaRecorder, final MediaRecorderHandler mediaRecoderEventsHandler) {
        Logging.d("UsbCameraCapturer", "addMediaRecorderToCamera");
        this.cameraThreadHandler.post(new Runnable() {
            public void run() {
                UsbCameraCapturer.this.updateMediaRecorderInternal(mediaRecorder, mediaRecoderEventsHandler);
            }
        });
    }

    public void removeMediaRecorderFromCamera(final MediaRecorderHandler mediaRecoderEventsHandler) {
        Logging.d("UsbCameraCapturer", "removeMediaRecorderFromCamera");
        this.cameraThreadHandler.post(new Runnable() {
            public void run() {
                UsbCameraCapturer.this.updateMediaRecorderInternal((MediaRecorder)null, mediaRecoderEventsHandler);
            }
        });
    }

    public boolean isScreencast() {
        return false;
    }

    private void reportCameraSwitchError(String error, @Nullable CameraSwitchHandler switchEventsHandler) {
        Logging.e("UsbCameraCapturer", error);
        if (switchEventsHandler != null) {
            switchEventsHandler.onCameraSwitchError(error);
        }

    }

    private void switchCameraInternal(@Nullable CameraSwitchHandler switchEventsHandler) {
        Logging.d("UsbCameraCapturer", "switchCamera internal");
        String[] deviceNames = this.cameraEnumerator.getDeviceNames();
        if (deviceNames.length < 2) {
            if (switchEventsHandler != null) {
                switchEventsHandler.onCameraSwitchError("No camera to switch to.");
            }

        } else {
            Object var3 = this.stateLock;
            synchronized(this.stateLock) {
                if (this.switchState != UsbCameraCapturer.SwitchState.IDLE) {
                    this.reportCameraSwitchError("Camera switch already in progress.", switchEventsHandler);
                    return;
                }

                if (this.mediaRecorderState != UsbCameraCapturer.MediaRecorderState.IDLE) {
                    this.reportCameraSwitchError("switchCamera: media recording is active", switchEventsHandler);
                    return;
                }

                if (!this.sessionOpening && this.currentSession == null) {
                    this.reportCameraSwitchError("switchCamera: camera is not running.", switchEventsHandler);
                    return;
                }

                this.switchEventsHandler = switchEventsHandler;
                if (this.sessionOpening) {
                    this.switchState = UsbCameraCapturer.SwitchState.PENDING;
                    return;
                }

                this.switchState = UsbCameraCapturer.SwitchState.IN_PROGRESS;
                Logging.d("UsbCameraCapturer", "switchCamera: Stopping session");
                this.cameraStatistics.release();
                this.cameraStatistics = null;
                final UsbCameraSession oldSession = this.currentSession;
                this.cameraThreadHandler.post(new Runnable() {
                    public void run() {
                        oldSession.stop();
                        UsbCameraCapturer.this.currentSession = null;
                        int cameraNameIndex = Arrays.asList(deviceNames).indexOf(UsbCameraCapturer.this.cameraName);
                        UsbCameraCapturer.this.cameraName = deviceNames[(cameraNameIndex + 1) % deviceNames.length];
                        UsbCameraCapturer.this.sessionOpening = true;
                        UsbCameraCapturer.this.openAttemptsRemaining = 1;
                        UsbCameraCapturer.this.createSessionInternal(0, (MediaRecorder)null);
                    }
                });
            }

            Logging.d("UsbCameraCapturer", "switchCamera done");
        }
    }

    private void reportUpdateMediaRecorderError(String error, @Nullable MediaRecorderHandler mediaRecoderEventsHandler) {
        this.checkIsOnCameraThread();
        Logging.e("UsbCameraCapturer", error);
        if (mediaRecoderEventsHandler != null) {
            mediaRecoderEventsHandler.onMediaRecorderError(error);
        }

    }

    private void updateMediaRecorderInternal(@Nullable MediaRecorder mediaRecorder, MediaRecorderHandler mediaRecoderEventsHandler) {
        this.checkIsOnCameraThread();
        boolean addMediaRecorder = mediaRecorder != null;
        Logging.d("UsbCameraCapturer", "updateMediaRecoderInternal internal. State: " + this.mediaRecorderState + ". Switch state: " + this.switchState + ". Add MediaRecorder: " + addMediaRecorder);
        Object var4 = this.stateLock;
        synchronized(this.stateLock) {
            if (addMediaRecorder && this.mediaRecorderState != UsbCameraCapturer.MediaRecorderState.IDLE || !addMediaRecorder && this.mediaRecorderState != UsbCameraCapturer.MediaRecorderState.ACTIVE) {
                this.reportUpdateMediaRecorderError("Incorrect state for MediaRecorder update.", mediaRecoderEventsHandler);
                return;
            }

            if (this.switchState != UsbCameraCapturer.SwitchState.IDLE) {
                this.reportUpdateMediaRecorderError("MediaRecorder update while camera is switching.", mediaRecoderEventsHandler);
                return;
            }

            if (this.currentSession == null) {
                this.reportUpdateMediaRecorderError("MediaRecorder update while camera is closed.", mediaRecoderEventsHandler);
                return;
            }

            if (this.sessionOpening) {
                this.reportUpdateMediaRecorderError("MediaRecorder update while camera is still opening.", mediaRecoderEventsHandler);
                return;
            }

            this.mediaRecorderEventsHandler = mediaRecoderEventsHandler;
            this.mediaRecorderState = addMediaRecorder ? UsbCameraCapturer.MediaRecorderState.IDLE_TO_ACTIVE : UsbCameraCapturer.MediaRecorderState.ACTIVE_TO_IDLE;
            Logging.d("UsbCameraCapturer", "updateMediaRecoder: Stopping session");
            this.cameraStatistics.release();
            this.cameraStatistics = null;
            final UsbCameraSession oldSession = this.currentSession;
            this.cameraThreadHandler.post(new Runnable() {
                public void run() {
                    oldSession.stop();
                }
            });
            this.currentSession = null;
            this.sessionOpening = true;
            this.openAttemptsRemaining = 1;
            this.createSessionInternal(0, mediaRecorder);
        }

        Logging.d("UsbCameraCapturer", "updateMediaRecoderInternal done");
    }

    private void checkIsOnCameraThread() {
        if (Thread.currentThread() != this.cameraThreadHandler.getLooper().getThread()) {
            Logging.e("UsbCameraCapturer", "Check is on camera thread failed.");
            throw new RuntimeException("Not on camera thread.");
        }
    }

    protected String getCameraName() {
        Object var1 = this.stateLock;
        synchronized(this.stateLock) {
            return this.cameraName;
        }
    }

    protected void createCameraSession(UsbCameraSession.CreateSessionCallback var1, UsbCameraSession.Events var2, Context var3, SurfaceTextureHelper var4, MediaRecorder var5, String var6, int var7, int var8, int var9) {
        if (UsbCameraEnumerator.cameraName.equals(var6)) UvcCameraSession.create(var1, var2, var3, var4, var5, var7, var8, var9);
        else Camera1CopySession.create(var1, var2, true, var3, var4, var5, UsbCameraEnumerator.getCameraIndex(var6), var7, var8, var9);
    }

    static enum MediaRecorderState {
        IDLE,
        IDLE_TO_ACTIVE,
        ACTIVE_TO_IDLE,
        ACTIVE;

        private MediaRecorderState() {
        }
    }

    static enum SwitchState {
        IDLE,
        PENDING,
        IN_PROGRESS;

        private SwitchState() {
        }
    }
}
