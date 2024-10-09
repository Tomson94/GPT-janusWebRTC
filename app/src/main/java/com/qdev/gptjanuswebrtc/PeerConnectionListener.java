package com.qdev.gptjanuswebrtc;

import org.webrtc.IceCandidate;
import org.webrtc.VideoTrack;

public interface PeerConnectionListener {
    void onTrackReceived(VideoTrack remoteVideoTrack);

    void sendLocalIceCandidateToJanus(IceCandidate iceCandidate);
}
