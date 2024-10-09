package com.qdev.gptjanuswebrtc;

import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

public interface JanusMessageHandler {
    void onCreateSessionSuccess(long sessionId);
    void onAttachPluginSuccess(long handleId);
    void onJoinRoomSuccess();
    void onRemoteJsepReceived(SessionDescription sessionDescription);
    void onRemoteIceCandidateReceived(IceCandidate iceCandidate);
    void onError(String error);
    PeerConnection getPeerConnection();
}