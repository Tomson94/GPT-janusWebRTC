package com.qdev.gptjanuswebrtc.webRtcManagement;

import android.content.Context;

import org.webrtc.Camera2Enumerator;
import org.webrtc.VideoCapturer;

/**
 * Provides methods to create and manaeg the VideoCapturer instance.
 *
 * <ul>
 *     <li>Detect available cameras and choose the appropriate one</li>
 *     <li>Initialize the video capturer with the necessary parameters</li>
 *     <li>Handle camera switch functionality if needed</li>
 * </ul>
 * */
public class VideoCaptureFactory {
    private final String TAG = VideoCaptureFactory.class.getCanonicalName();

    public VideoCapturer getVideoCapturer(Context context) {
        Camera2Enumerator enumerator = new Camera2Enumerator(context);

        for (String deviceName : enumerator.getDeviceNames()) {
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer capturer = enumerator.createCapturer(deviceName, null);
                if (capturer != null) {
                    return capturer;
                }
            }
        }
        // Try back camera if front is not available
        for (String deviceName : enumerator.getDeviceNames()) {
            if (enumerator.isBackFacing(deviceName)) {
                VideoCapturer capturer = enumerator.createCapturer(deviceName, null);
                if (capturer != null) {
                    return capturer;
                }
            }
        }

        throw new RuntimeException("No camera found");
    }
}
