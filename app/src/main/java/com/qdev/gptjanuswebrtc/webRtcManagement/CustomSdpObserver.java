package com.qdev.gptjanuswebrtc.webRtcManagement;

import android.util.Log;

import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

/**
 * Implements SdpObserver to receive callbacks from the WebRTC SessionDescription protocol.
 *
 *  <ul>
 *      <li>Receive callbacks for SDP creation and setting (onCreateSuccess, onSetSuccess, onCreateFailure, onSetFailure, etc)</li>
 *      <li>Handle success and failure scenarios for creating offers and answers.</li>
 *      <li>Communicate SDP information to #JanusWebSocketClient.</li>
 *  </ul>
 * */
public class CustomSdpObserver implements SdpObserver {

    private String TAG;

    public CustomSdpObserver(String tag){
        this.TAG = tag;
    }

    @Override
    public void onCreateSuccess(SessionDescription sessionDescription) {
        Log.d(TAG, "onCreateSuccess: ");
    }

    @Override
    public void onSetSuccess() {
        Log.d(TAG, "onSetSuccess: ");
    }

    @Override
    public void onCreateFailure(String s) {
        Log.d(TAG, "onCreateFailure: " + s);
    }

    @Override
    public void onSetFailure(String s) {
        Log.d(TAG, "onSetFailure: " + s);
    }
}
