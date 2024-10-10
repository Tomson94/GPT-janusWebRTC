package com.qdev.gptjanuswebrtc.socketCommunication;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.qdev.gptjanuswebrtc.Constants;
import com.qdev.gptjanuswebrtc.JanusMessageHandler;
import com.qdev.gptjanuswebrtc.webRtcManagement.CustomSdpObserver;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioTrack;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.RtpSender;
import org.webrtc.SessionDescription;
import org.webrtc.VideoTrack;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.logging.HttpLoggingInterceptor;
import okio.ByteString;

/**
 * A class extending WebSocketListener to manage the WebSocket connection with the Janus server.
 *
 * <ul>
 *  <li>Establish and maintain a WebSocket connection to wss://jnx13.snipback.com/websocket.</li>
 *  <li>Send and receive messages following the Janus protocol.</li>
 *  <li>Handle events such as onOpen, onMessage, onClose, and onError.</li>
 *  <li>Parse incoming messages and dispatch them to appropriate handlers.</li>
 * </ul>
 */
public class JanusWebSocketClient extends WebSocketListener {
    private final String TAG = JanusWebSocketClient.class.getCanonicalName();

    private String serverUrl;
    private long sessionId;
    private long handleId;
    private WebSocket webSocket;
    private JanusMessageHandler handler;
    private PeerConnection peerConnectionRef;
    private SessionDescription remoteSessionDescription;
    MediaStream mediaStream;
    private Handler mHandler;


    public void setMediaStream(MediaStream mediaStream){
        this.mediaStream = mediaStream;
    }

    private Runnable fireKeepAlive = new Runnable() {
        @Override
        public void run() {
            requestKeepAlive();
            mHandler.postDelayed(fireKeepAlive, 30000);
        }
    };

    public JanusWebSocketClient(String serverUri, JanusMessageHandler handler, PeerConnection peerConnection) {
        this.serverUrl = serverUri;
        this.handler = handler;
        this.peerConnectionRef = peerConnection;

        OkHttpClient httpClient = new OkHttpClient.Builder()
                .addNetworkInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
                .addInterceptor(chain -> {
                    Request.Builder builder = chain.request().newBuilder();
                    builder.addHeader("Sec-WebSocket-Protocol", "janus-protocol");
                    return chain.proceed(builder.build());
                }).connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
        Request request = new Request.Builder().url(serverUri).build();
        webSocket = httpClient.newWebSocket(request, this);
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        // Handle WebSocket connection opened
        try {
            sendCreateSession();
        } catch (JSONException e) {
            Log.e(TAG, "onOpen: ", e);
        }
    }

    private void sendCreateSession() throws JSONException {
        JSONObject message = new JSONObject();
        message.put("janus", "create");
        message.put("room", Constants.ROOM_ID);
        message.put("transaction", /*generateTransactionId()*/"Create");
        message.put("apisecret", Constants.SECRET);
        send(message.toString());
    }

    private void sendAttachPlugin() throws JSONException {
        JSONObject message = new JSONObject();
        message.put("janus", "attach");
        message.put("plugin", "janus.plugin.videoroom");
        message.put("transaction", /*generateTransactionId()*/"Attach");
        message.put("session_id", sessionId);
        message.put("apisecret", Constants.SECRET);
        send(message.toString());
    }

    private void sendJoinRoomAsPublisher() throws JSONException {
        JSONObject body = new JSONObject();
        body.put("request", "join");
        body.put("ptype", "publisher");
        body.put("room", Constants.ROOM_ID);
        body.put("id", Constants.ROOM_ID);
        body.put("display", "AndroidUser");

        JSONObject message = new JSONObject();
        message.put("janus", "message");
        message.put("body", body);
        message.put("transaction", /*generateTransactionId()*/"JoinRoom");
        message.put("session_id", sessionId);
        message.put("handle_id", handleId);
        message.put("apisecret", Constants.SECRET);
        send(message.toString());
        /*sendJoinRoomAsSubcriber();*/
    }
    private void sendJoinRoomAsSubcriber() throws JSONException {
        JSONObject subscribeRequest = new JSONObject();
        try {
            subscribeRequest.put("janus", "message");
            JSONObject body = new JSONObject();
            body.put("request", "join");
            body.put("room", Constants.ROOM_ID); // Your room ID
            body.put("ptype", "subscriber"); // Set participant type as subscriber
            body.put("feed", Constants.ROOM_ID); // Specify the publisher ID you want to subscribe to
            subscribeRequest.put("body", body);
            subscribeRequest.put("transaction", "JoinRoom"); // Unique transaction ID
        } catch (JSONException e) {
            e.printStackTrace();
        }
        subscribeRequest.put("session_id", sessionId);
        subscribeRequest.put("handle_id", handleId);
        subscribeRequest.put("apisecret", Constants.SECRET);
        send(subscribeRequest.toString());
    }

    private void sendCreateRoom() throws JSONException {
        /*{
            "request" : "create",
                "room" : <unique numeric ID, optional, chosen by plugin if missing>,
                "permanent" : <true|false, whether the room should be saved in the config file, default=false>,
                "description" : "<pretty name of the room, optional>",
                "secret" : "<password required to edit/destroy the room, optional>",
                "pin" : "<password required to join the room, optional>",
                "is_private" : <true|false, whether the room should appear in a list request>,
            "allowed" : [ array of string tokens users can use to join this room, optional],
        ...
        }*/
        JSONObject body = new JSONObject();
        body.put("request", "create");
        //body.put("ptype", "publisher");
        body.put("room", Constants.ROOM_ID);
        //body.put("display", "AndroidUser");

        JSONObject message = new JSONObject();
        message.put("janus", "message");
        message.put("body", body);
        message.put("transaction", /*generateTransactionId()*/"CreateRoom");
        message.put("session_id", sessionId);
        message.put("handle_id", handleId);
        message.put("apisecret", Constants.SECRET);
        send(message.toString());
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        // Handle incoming messages
        parseJanusMessage(text);
    }

    private void parseJanusMessage(String message) {
        try {
            JSONObject json = new JSONObject(message);
            String janusMessageType = json.optString("janus");
            Log.d(TAG, "parseJanusMessage: response message = " + json);
            switch (janusMessageType) {
                case "success":
                    // Handle success responses
                    handleSuccessResponse(json);
                    break;
                case "event":
                    // Handle events
                    handleEventResponse(json);
                    break;
                case "media":
                    // Handle media
                    break;
                case "trickle":
                    handleRemoteTrickle(json);
                    break;
                case "error":
                    // Handle errors
                    handleError(json);
                    break;
                // Add additional cases as needed
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void handleError(JSONObject json) throws JSONException {
        JSONObject error = json.getJSONObject("error");
        int code = error.getInt("code");
        String reason = error.getString("reason");

        handler.onError("Janus Error (" + code + "): " + reason);
    }

    private void handleRemoteTrickle(JSONObject json) {
        try {
            JSONObject candidate = json.getJSONObject("candidate");
            IceCandidate iceCandidate = new IceCandidate(
                    candidate.getString("sdpMid"),
                    candidate.getInt("sdpMLineIndex"),
                    candidate.getString("candidate")
            );
            handler.onRemoteIceCandidateReceived(iceCandidate);
        } catch (JSONException e) {
            Log.e(TAG, "handleRemoteTrickle: ", e);
        }
    }

    private void handleEventResponse(JSONObject json) {
        JSONObject pluginData = json.optJSONObject("plugindata");
        if (pluginData != null) {
            try {
                String plugin = pluginData.getString("plugin");
                if (plugin != null) {
                    JSONObject data = pluginData.getJSONObject("data");
                    String videoroom = data.getString("videoroom");

                    if ("joined".equals(videoroom)) {
                        // We have successfully joined the room
                        handler.onJoinRoomSuccess();
                        createOffer();
                    } else if ("event".equals(videoroom)) {
                        // Handle other events, like new publishers
                        // Handle remote feed if needed
                        if (!data.isNull("error_code")) {
                            if (data.getString("error_code") != null || data.getString("error_code") == "426") {
                                Log.e(TAG, "handleEventResponse: no existing room, creating a new one");
                                sendCreateRoom();
                            }
                        }
                    }
                }
            } catch (JSONException e) {
                Log.e(TAG, "handleEventResponse: ", e);
            }
        }

        // Handle JSEP if present
        if (json.has("jsep")) {
            JSONObject jsep = null;
            try {
                jsep = json.getJSONObject("jsep");
                handleRemoteJsep(jsep);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void createOffer() {
        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"));
        
        peerConnectionRef.createOffer(new CustomSdpObserver("createOffer") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                peerConnectionRef.setLocalDescription(new CustomSdpObserver("setLocalDescription") {
                    @Override
                    public void onSetSuccess() {
                        Log.d(TAG, "onSetSuccess: Local description is set ");
                        sendOfferToJanus(sessionDescription);
//                        sendPublishToJanus(sessionDescription);
                        Log.d(TAG, "onSetSuccess: Media published " + mediaStream.videoTracks.size());

//                        publishStream(mediaStream);
                    }

                    @Override
                    public void onSetFailure(String s) {
                        Log.e(TAG, "onSetFailure: "+ s);
                    }
                }, sessionDescription);
            }
        }, constraints);
    }

    private void createAnswer() {
        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));

        peerConnectionRef.createAnswer(new CustomSdpObserver("createAnswer") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                peerConnectionRef.setLocalDescription(new CustomSdpObserver("setLocalDescription") {
                    @Override
                    public void onSetSuccess() {
                        sendAnswerToJanus(sessionDescription);
                    }
                }, sessionDescription);
            }
        }, constraints);
    }

    public void publishStream(MediaStream mediaStream) {
        // Create the JSON object to send to Janus
        JSONObject msg = new JSONObject();
        try {
            msg.put("janus", "message");
            msg.put("session_id", sessionId);
            msg.put("handle_id", handleId); // Plugin ID
            msg.put("method", "create"); // Assuming you want to create a stream
            msg.put("transaction", generateTransactionId());
            msg.put("apisecret", Constants.SECRET);

            // Add any additional parameters needed for publishing the stream
            JSONObject params = new JSONObject();
            params.put("request", "publish");
            params.put("type", "video"); // You can specify "audio" or "video" based on your needs

            // Add the media stream details (e.g., video and audio tracks)
            // You may need to serialize the media tracks here
            JSONArray tracks = new JSONArray();

            // Assuming you have access to mediaStream's tracks
            for (VideoTrack videoTrack : mediaStream.videoTracks) {
                JSONObject videoTrackInfo = new JSONObject();
                videoTrackInfo.put("track_id", videoTrack.id());
                videoTrackInfo.put("type", "video");
                tracks.put(videoTrackInfo);
            }

            for (AudioTrack audioTrack : mediaStream.audioTracks) {
                JSONObject audioTrackInfo = new JSONObject();
                audioTrackInfo.put("track_id", audioTrack.id());
                audioTrackInfo.put("type", "audio");
                tracks.put(audioTrackInfo);
            }

            params.put("tracks", tracks);
            msg.put("body", params);

            // Send the message over WebSocket
            send(msg.toString());

        } catch (JSONException e) {
            Log.e(TAG, "publishStream: ", e);
        }
    }


    private void sendOfferToJanus(SessionDescription sdp) {
        try {
            JSONObject body = new JSONObject();
            body.put("request", "configure");
            body.put("audio", true);
            body.put("video", true);

            JSONObject jsep = new JSONObject();
            jsep.put("type", sdp.type.canonicalForm());
            jsep.put("sdp", sdp.description);

            JSONObject message = new JSONObject();
            message.put("janus", "message");
            message.put("body", body);
            message.put("jsep", jsep);
            message.put("transaction", /*generateTransactionId()*/"Configure");
            message.put("session_id", sessionId);
            message.put("handle_id", handleId);
            message.put("apisecret", Constants.SECRET);
            send(message.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

     public void sendPublishToJanus(SessionDescription sdp) {
        try {
            JSONObject body = new JSONObject();
            body.put("request", "publish");
            body.put("audio", true);
            body.put("video", true);

            JSONObject jsep = new JSONObject();
            jsep.put("type", sdp.type.canonicalForm());
            jsep.put("sdp", sdp.description);

            JSONObject message = new JSONObject();
            message.put("janus", "message");
            message.put("body", body);
            message.put("jsep", jsep);
            message.put("transaction", generateTransactionId());
            message.put("session_id", sessionId);
            message.put("handle_id", handleId);
            message.put("apisecret", Constants.SECRET);
            send(message.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void sendAnswerToJanus(SessionDescription sdp) {
        try {
            // Construct the body of the message
            JSONObject body = new JSONObject();
            body.put("request", "start");
            body.put("room", Constants.ROOM_ID); // Replace with your actual room ID if different

            // Construct the JSEP object
            JSONObject jsep = new JSONObject();
            jsep.put("type", sdp.type.canonicalForm());
            jsep.put("sdp", sdp.description);

            // Construct the complete message
            JSONObject message = new JSONObject();
            message.put("janus", "message");
            message.put("body", body);
            message.put("jsep", jsep);
            message.put("transaction", generateTransactionId());
            message.put("session_id", sessionId);
            message.put("handle_id", handleId);
            message.put("apisecret", Constants.SECRET);
            // Send the message via the JanusWebSocketClient
            send(message.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void handleRemoteJsep(JSONObject jsep) {
        try {
            String sdpType = jsep.getString("type");
            String sdp = jsep.getString("sdp");

            SessionDescription.Type type = SessionDescription.Type.fromCanonicalForm(sdpType);
            remoteSessionDescription = new SessionDescription(type, sdp);

            handler.onRemoteJsepReceived(remoteSessionDescription);
        } catch (JSONException e) {
            Log.e(TAG, "handleRemoteJsep: ", e);
        }
    }

    private void handleSuccessResponse(JSONObject json) throws JSONException {

        JSONObject data;
        if (json.has("data")) {
            data = json.getJSONObject("data");
            Log.e(TAG, "handleSuccessResponse: " + data.toString());
            if (!json.has("session_id")) {
                // This is the response to the "create" request
                sessionId = data.getLong("id");
                handler.onCreateSessionSuccess(sessionId);

                if (mHandler == null)
                    mHandler = new Handler(Looper.getMainLooper());
                mHandler.post(fireKeepAlive);

                sendAttachPlugin();
            } else if (json.has("session_id") && !json.has("sender")) {
                // This is the response to the "attach" request
                handleId = data.getLong("id");
                handler.onAttachPluginSuccess(handleId);
                sendJoinRoomAsPublisher();
            }
        } else {
            if (json.has("session_id") && json.has("sender")) {
                // This is the response to a plugin request
                JSONObject pluginData = json.optJSONObject("plugindata");
                if (pluginData != null) {
                    String plugin = pluginData.getString("plugin");
                    if ("janus.plugin.videoroom".equals(plugin)) {
                        JSONObject dataObj = pluginData.getJSONObject("data");
                        String videoroom = dataObj.getString("videoroom");
                        if ("created".equals(videoroom)) {
                            // Room was created successfully
                            long roomId = dataObj.getLong("room");
                            Log.d(TAG, "Room created with ID: " + roomId);
                            // Now join the room
                            sendJoinRoomAsPublisher();
                        } else if ("joined".equals(videoroom)) {
                            // Successfully joined the room
                            handler.onJoinRoomSuccess();
                            // Proceed to create an offer
                            createOffer();
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
        // Handle incoming binary messages if any
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        // Handle WebSocket is closing
        webSocket.close(1000, null);
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        // Handle WebSocket closed
        Log.e(TAG, "onClosed: " + String.valueOf(code) + reason);
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        // Handle errors
        Log.e(TAG, "onFailure: ", t);
    }

    private String generateTransactionId() {
        return UUID.randomUUID().toString();
    }

    private void send(String msg) {
        Log.d(TAG, "send: message = " + new Gson().toJson(msg));
        webSocket.send(msg);
    }

    public void trickleCandidateComplete() {
        JSONObject candidate = new JSONObject();
        JSONObject message = new JSONObject();

        Log.d(TAG, "trickleCandidateComplete: sending end-of-candidates to Janus");
        try {
            candidate.putOpt("completed", true);

            message.putOpt("janus", "trickle");
            message.putOpt("candidate", candidate);
            message.putOpt("transaction", /*generateTransactionId()*/"Candidate");
            message.putOpt("session_id", sessionId);
            message.putOpt("handle_id", handleId);
            message.put("apisecret", Constants.SECRET);
            send(message.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void sendLocalIceCandidate(IceCandidate iceCandidate) {
        try {
            JSONObject candidate = new JSONObject();
            JSONObject message = new JSONObject();
            message.put("janus", "trickle");
            message.put("transaction", /*generateTransactionId()*/"Candidate");
            message.put("session_id", sessionId);
            message.put("handle_id", handleId);

            candidate.putOpt("candidate", iceCandidate != null ? iceCandidate.sdp : "");

            String sdpMid = "";
            int sdpMLineIndex = 0;
            if (iceCandidate != null) {
                if (iceCandidate.sdpMid != null) {
                    sdpMid = iceCandidate.sdpMid;
                }

                sdpMLineIndex = iceCandidate.sdpMLineIndex;
            }
            candidate.put("sdpMid", sdpMid);

            // Janus requires sdpMLineIndex, set to 0 or appropriate value
            candidate.put("sdpMLineIndex", Math.max(sdpMLineIndex, 0));

            message.put("candidate", candidate);
            message.put("apisecret", Constants.SECRET);
            send(message.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void requestRemoteIceCandidate() {
        try {
            JSONObject candidate = new JSONObject();
            JSONObject message = new JSONObject();
            message.put("janus", "trickle");
            message.put("transaction", generateTransactionId());
            message.put("session_id", sessionId);
            message.put("handle_id", handleId);

            candidate.putOpt("completed", false);

            message.put("candidate", candidate);
            message.put("apisecret", Constants.SECRET);
            send(message.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void requestKeepAlive(){
       /**
        * {"transaction":"Heatbeat","session_id":7231210807641497,"apisecret":"YMDhSqL695vGDUELODmxWy3eA","janus":"keepalive"}
        * */
        try {
            JSONObject message = new JSONObject();
            message.putOpt("transaction", /*generateTransactionId()*/"Heatbeat");
            message.putOpt("session_id", sessionId);
            message.putOpt("apisecret", Constants.SECRET);
            message.putOpt("janus", "keepalive");
            send(message.toString());
        } catch (JSONException e){
            Log.e(TAG, "requestKeepAlive: ", e);
        }
    }

    private void onRemoteIceCandidateReceived(JSONObject json) throws JSONException {
        // Parse the ICE candidate from the received JSON message
        JSONObject candidateJson = json.getJSONObject("candidate");
        IceCandidate iceCandidate = new IceCandidate(
                candidateJson.getString("sdpMid"),
                candidateJson.getInt("sdpMLineIndex"),
                candidateJson.getString("candidate")
        );

        // Add the ICE candidate to the PeerConnection
        peerConnectionRef.addIceCandidate(iceCandidate);
        Log.d("ICE Candidate", "Remote ICE candidate added.");
    }

   public   void requestDestroyStream(){
        /**
         * {"transaction":"Destroy","janus":"destroy","session_id":7231210807641497,"apisecret":"YMDhSqL695vGDUELODmxWy3eA"}
         * */
        try {
            JSONObject message = new JSONObject();
            message.putOpt("transaction","Destroy");
            message.putOpt("session_id", sessionId);
            message.putOpt("apisecret", Constants.SECRET);
            message.putOpt("janus", "destroy");
            send(message.toString());
        } catch (JSONException e){
            Log.e(TAG, "requestKeepAlive: ", e);
        }
    }

}