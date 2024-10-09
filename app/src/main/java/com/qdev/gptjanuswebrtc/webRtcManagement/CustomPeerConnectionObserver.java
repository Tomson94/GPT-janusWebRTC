package com.qdev.gptjanuswebrtc.webRtcManagement;

import android.util.Log;

import com.qdev.gptjanuswebrtc.PeerConnectionListener;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.CandidatePairChangeEvent;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpTransceiver;
import org.webrtc.VideoTrack;

/**
 * Implements PeerConnection.Observer to receive callbacks from the PeerConnection.
 *  <ul>
 *      <li> Handle events like onIceCandidate, onAddStream, onConnectionChange, etc.</li>
 *      <li> Pass ICE candidates to JanusWebSocketClient to send to the Janus server.</li>
 *      <li> Update the UI with remote streams when they are added.</li>
 *  </ul>
 * */
public class CustomPeerConnectionObserver implements PeerConnection.Observer {
    private final String TAG = CustomPeerConnectionObserver.class.getCanonicalName();
    PeerConnectionListener peerConnectionListener;

    public CustomPeerConnectionObserver(PeerConnectionListener peerConnectionListener) {
        this.peerConnectionListener = peerConnectionListener;
    }

    @Override
    public void onSignalingChange(PeerConnection.SignalingState signalingState) {
        Log.d(TAG, "onSignalingChange: " + signalingState);
    }

    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
        Log.d(TAG, "onIceConnectionChange: " + iceConnectionState);
    }

    @Override
    public void onIceConnectionReceivingChange(boolean receiving) {
        Log.d(TAG, "onIceConnectionReceivingChange: " + receiving);
    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
        Log.d(TAG, "onIceGatheringChange: " + iceGatheringState);
        if (iceGatheringState == PeerConnection.IceGatheringState.COMPLETE) {
            sendLocalIceCandidateToJanus(null); // Send null candidate to indicate completion
        }
    }

    @Override
    public void onIceCandidate(IceCandidate iceCandidate) {
        Log.d(TAG, "onIceCandidate: " + iceCandidate);
        Log.d(TAG, "ICE Candidate sdp: " + iceCandidate.sdp);
        Log.d(TAG, "ICE Candidate sdpMid: " + iceCandidate.sdpMid);
        Log.d(TAG, "ICE Candidate sdpMLineIndex: " + iceCandidate.sdpMLineIndex);
        sendLocalIceCandidateToJanus(iceCandidate);
    }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] candidates) {
        Log.d(TAG, "onIceCandidatesRemoved");
    }

    @Override
    public void onAddStream(MediaStream mediaStream) {
        // Deprecatedqqq
        Log.d(TAG, "onAddStream: " + mediaStream);
    }

    @Override
    public void onRemoveStream(MediaStream mediaStream) {
        // Deprecated
        Log.d(TAG, "onRemoveStream: " + mediaStream);
    }

    @Override
    public void onDataChannel(DataChannel dataChannel) {
        Log.d(TAG, "onDataChannel");
    }

    @Override
    public void onRenegotiationNeeded() {
        Log.d(TAG, "onRenegotiationNeeded");
    }

    @Override
    public void onAddTrack(RtpReceiver receiver, MediaStream[] mediaStreams) {
        // Deprecated, use onTrack instead
        Log.d(TAG, "onAddTrack: " + mediaStreams);
    }

    @Override
    public void onTrack(RtpTransceiver transceiver) {
        Log.d(TAG, "onTrack");
        if (transceiver.getReceiver().track() instanceof VideoTrack) {
            VideoTrack remoteVideoTrack = (VideoTrack) transceiver.getReceiver().track();

            peerConnectionListener.onTrackReceived(remoteVideoTrack);
        }
    }

    @Override
    public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
        Log.d(TAG, "onConnectionChange: " + newState);
    }

    @Override
    public void onSelectedCandidatePairChanged(CandidatePairChangeEvent event) {
        Log.d(TAG, "onSelectedCandidatePairChanged");
    }

    private void sendLocalIceCandidateToJanus(IceCandidate iceCandidate) {
       peerConnectionListener.sendLocalIceCandidateToJanus(iceCandidate);
    }
}