package com.qdev.gptjanuswebrtc;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.gson.Gson;
import com.qdev.gptjanuswebrtc.socketCommunication.JanusWebSocketClient;
import com.qdev.gptjanuswebrtc.webRtcManagement.CustomPeerConnectionObserver;
import com.qdev.gptjanuswebrtc.webRtcManagement.CustomSdpObserver;
import com.qdev.gptjanuswebrtc.webRtcManagement.VideoCaptureFactory;

import org.webrtc.AddIceObserver;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RTCStats;
import org.webrtc.RendererCommon;
import org.webrtc.RtpSender;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.List;

;

public class MainActivity extends AppCompatActivity implements JanusMessageHandler, PeerConnectionListener {
    private static final String TAG = MainActivity.class.getSimpleName();

    private SurfaceViewRenderer localRenderer;
    private SurfaceViewRenderer remoteRenderer;
    private VideoTrack localVideoTrack;
    private AudioTrack localAudioTrack;
    private RtpSender localVideoSender;
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1;

    private EglBase eglBase;
    private PeerConnectionFactory peerConnectionFactory;
    private PeerConnection peerConnection;
    private JanusWebSocketClient janusClient;
    private VideoCapturer videoCapturer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            v.setPadding(
                    insets.getInsets(WindowInsetsCompat.Type.systemBars()).left,
                    insets.getInsets(WindowInsetsCompat.Type.systemBars()).top,
                    insets.getInsets(WindowInsetsCompat.Type.systemBars()).right,
                    insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom);
            return insets;
        });

        localRenderer = findViewById(R.id.local_video_view);
        remoteRenderer = findViewById(R.id.remote_video_view);

        // Check for camera permissions
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO},
                    CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            setup();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (localRenderer != null) {
            localRenderer.release();
        }
        if (remoteRenderer != null) {
            remoteRenderer.release();
        }

    }

    @Override
    protected void onPause() {
        janusClient.requestDestroyStream();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setup();
            } else {
                Log.e(TAG, "Camera permission denied.");
            }
        }
    }

    private void setup() {
        initEglBase();
        initSurfaceViewRenderers();
        initPeerConnectionFactory();
        initVideoCapturer();
        initPeerConnection();
    }

    private void initEglBase() {
        eglBase = EglBase.create();
    }

    private void initSurfaceViewRenderers() {
        localRenderer.init(eglBase.getEglBaseContext(), null);
        remoteRenderer.init(eglBase.getEglBaseContext(), null);
        localRenderer.setMirror(true);
        localRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        remoteRenderer.setMirror(false);
        remoteRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        remoteRenderer.setZOrderMediaOverlay(true);
    }

    private void initPeerConnectionFactory() {
        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(this.getApplicationContext())
                        .setEnableInternalTracer(true)
                        .createInitializationOptions()
        );

        peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(
                                eglBase.getEglBaseContext(),
                                true,
                                true
                        )
                )
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(
                                eglBase.getEglBaseContext()
                        )
                )
                .createPeerConnectionFactory();
    }

    private void initPeerConnection() {
        // ICE Servers
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
//        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("stun:jnx14.snipback.com:3478").setUsername("snptctr").setPassword("09Ywaei010#13!4").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("turn:jnx14.snipback.com:3478").setUsername("snptctr").setPassword("09Ywaei010#13!4").createIceServer());
        // Add TURN servers if needed

        // Configuration
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

        // Create PeerConnection
        peerConnection = peerConnectionFactory.createPeerConnection(
                rtcConfig,
                new CustomPeerConnectionObserver(this) // Implement this observer
        );

        // Add Tracks
        /*List<String> mediaStreamLabels = Collections.singletonList(String.valueOf(Constants.ROOM_ID));
        if (localVideoTrack != null) {
            peerConnection.addTrack(localVideoTrack, mediaStreamLabels);
        }
        if (localAudioTrack != null) {
            peerConnection.addTrack(localAudioTrack, mediaStreamLabels);
        }*/

        MediaStream mediaStream = peerConnectionFactory.createLocalMediaStream("localStream");

        // Add Tracks
        if (localVideoTrack != null) {
            mediaStream.addTrack(localVideoTrack);
            peerConnection.addTrack(localVideoTrack);
        }
        if (localAudioTrack != null) {
            mediaStream.addTrack(localAudioTrack);
            peerConnection.addTrack(localAudioTrack);
        }

        peerConnection.setBitrate(
                1000 * 1000,   // 500 kbps
                4000 * 1000, // 2.5 Mbps
                6000 * 1000   // 4 Mbps
        );

        peerConnection.getStats(report -> {
            for (RTCStats stat : report.getStatsMap().values()) {
                // Look for outbound-rtp (video) stats
                Log.e(TAG, "initPeerConnection: " + stat.toString());
                if ("outbound-rtp".equals(stat.getType())) {
                    if (stat.getMembers().containsKey("mediaType") && "video".equals(stat.getMembers().get("mediaType"))) {
                        long bytesSent = (long) stat.getMembers().get("bytesSent");
                        long packetsSent = (long) stat.getMembers().get("packetsSent");
                        Log.d(TAG, "Video is being sent. Bytes sent: " + bytesSent + ", Packets sent: " + packetsSent);
                    }
                }
            }
        });


        initWebSocket(mediaStream);
    }

    private void initVideoCapturer() {
        videoCapturer = new VideoCaptureFactory()
                .getVideoCapturer(this);
        initMediaStreams();
    }

    private void initMediaStreams() {
        // Video Source
        SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create(
                "CaptureThread", eglBase.getEglBaseContext());
        VideoSource videoSource = peerConnectionFactory.createVideoSource(true);
        videoCapturer.initialize(surfaceTextureHelper, this, videoSource.getCapturerObserver());
        videoCapturer.startCapture(720, 480, 30); // Width, height, FPS

        // Video Track
        localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource);
        localVideoTrack.addSink(localRenderer);

        // Audio Source and Track
        AudioSource audioSource = peerConnectionFactory.createAudioSource(new MediaConstraints());
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource);
    }

    private void initWebSocket(MediaStream mediaStream) {
        janusClient = new JanusWebSocketClient(Constants.JANUS_SERVER_URI, this, peerConnection);

        janusClient.setMediaStream(mediaStream);
//        janusClient.connect();
    }

    @Override
    public void onCreateSessionSuccess(long sessionId) {
        Log.d(TAG, "Session created successfully with sessionId: " + sessionId);
    }

    @Override
    public void onAttachPluginSuccess(long handleId) {
        Log.d(TAG, "Plugin attached successfully with handleId: " + handleId);
    }

    @Override
    public void onJoinRoomSuccess() {
        Log.d(TAG, "Joined room successfully");
//        setVideoMaxBitrate(5000);
    }

    @Override
    public void onRemoteJsepReceived(SessionDescription sessionDescription) {
        Log.d(TAG, "onRemoteJsepReceived: sessionDescription = " + new Gson().toJson(sessionDescription));
        peerConnection.setRemoteDescription(
                new CustomSdpObserver("setRemoteDescription") {
                    @Override
                    public void onSetSuccess() {
                        Log.d(TAG, "onSetSuccess: Remote description set successfully");
                    }

                    @Override
                    public void onSetFailure(String s) {
                        Log.e(TAG, "onSetFailure: Failed to set remote description. " + s);
                    }
                },
                sessionDescription
        );
    }

    @Override
    public void onRemoteIceCandidateReceived(IceCandidate iceCandidate) {
        Log.d(TAG, "onRemoteIceCandidateReceived:" + iceCandidate.serverUrl);

        peerConnection.addIceCandidate(iceCandidate, new AddIceObserver() {
            @Override
            public void onAddSuccess() {
                Log.d(TAG, "onAddSuccess: Remote candidate set");
            }

            @Override
            public void onAddFailure(String s) {
                Log.e(TAG, "onAddFailure: remote candidate set failed " + s);
            }
        });
    }

    @Override
    public void onError(String error) {
        Log.e(TAG, "Error: " + error);
        runOnUiThread(() -> {
            Toast.makeText(this, "Error: " + error, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public PeerConnection getPeerConnection() {
        return peerConnection;
    }

    @Override
    public void onTrackReceived(VideoTrack remoteVideoTrack) {
        runOnUiThread(() -> {
            Log.e(TAG, "onTrackReceived: " + remoteVideoTrack.id());
            remoteVideoTrack.setEnabled(true);
            remoteVideoTrack.addSink(remoteRenderer);
        });
    }

    @Override
    public void sendLocalIceCandidateToJanus(IceCandidate iceCandidate) {
        if (janusClient != null) {
            if (iceCandidate == null) {
                janusClient.trickleCandidateComplete();
//                janusClient.requestRemoteIceCandidate();
            } else {
                janusClient.sendLocalIceCandidate(iceCandidate);
            }
        }
    }
}