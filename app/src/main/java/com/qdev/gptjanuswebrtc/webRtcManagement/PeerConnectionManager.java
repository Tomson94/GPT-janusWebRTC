package com.qdev.gptjanuswebrtc.webRtcManagement;

import org.webrtc.PeerConnection;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the WebRTC PeerConnection and related media streams.
 *
 * <ul>
 *  <li>Initialize PeerConnectionFactory and create a PeerConnection instance.</li>
 *  <li>Set up local media streams (AudioTrack and VideoTrack).</li>
 *  <li>Handle ICE candidates and signaling state changes.</li>
 *  <li>Implement methods to create offers/answers and set local/remote descriptions.</li>
 *  <li>Interface with CustomPeerConnectionObserver and CustomSdpObserver.</li>
 * </ul>
 * */
public class PeerConnectionManager {
    private final String TAG = PeerConnectionManager.class.getCanonicalName();


}
