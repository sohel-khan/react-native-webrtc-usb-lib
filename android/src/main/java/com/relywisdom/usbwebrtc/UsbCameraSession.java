package com.relywisdom.usbwebrtc;


import org.webrtc.VideoFrame;

interface UsbCameraSession {
    void stop();

    public interface Events {
        void onCameraOpening();

        void onCameraError(UsbCameraSession var1, String var2);

        void onCameraDisconnected(UsbCameraSession var1);

        void onCameraClosed(UsbCameraSession var1);

        void onFrameCaptured(UsbCameraSession var1, VideoFrame var2);
    }

    public interface CreateSessionCallback {
        void onDone(UsbCameraSession var1);

        void onFailure(UsbCameraSession.FailureType var1, String var2);
    }

    public static enum FailureType {
        ERROR,
        DISCONNECTED;

        private FailureType() {
        }
    }
}
