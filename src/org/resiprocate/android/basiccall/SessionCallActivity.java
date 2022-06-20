package org.resiprocate.android.basiccall;

import java.util.logging.Logger;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import org.resiprocate.android.basiccall.capturers.AndroidCameraCapturer;
import org.resiprocate.android.basiccall.observers.CustomPeerConnectionObserver;
import org.resiprocate.android.basiccall.observers.CustomSdpObserver;
//import org.resiprocate.android.basiccall.services.SignalrService;

import org.resiprocate.android.basicclient.SipService;
import org.resiprocate.android.basicclient.SipStackRemote;
import org.resiprocate.android.basicclient.SipUserRemote;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpTransceiver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SessionCallActivity extends Activity {

    Logger logger = Logger.getLogger(SessionCallActivity.class.getCanonicalName());

    private String mTargetUri;
    SipStackRemote mSipStackRemote;
    private PeerConnectionFactory mPeerConnectionFactory;
    private PeerConnection mPeerConnection;
    private EglBase mRootEglBase;
    private SurfaceViewRenderer mLocalVideoView;
    private SurfaceViewRenderer mRemoteVideoView;
    private AudioTrack mLocalAudioTrack;
    private VideoTrack mLocalVideoTrack;
    private AndroidCameraCapturer mCameraCapturer;

    private boolean mIsInitiator = false;
    private boolean mIsChannelReady = false;
    private boolean mIsStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session_call);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mTargetUri = getIntent().getExtras().getString("TARGET_URI");
        if (mTargetUri == null) {
            logger.severe("no TARGET_URI specified, aborting call");
            this.finish();
        }

        Intent intent = new Intent(this, SipService.class);
        intent.setPackage("org.resiprocate.android.basiccall");
        startService(intent);

        bindService(intent,
        mConnection, Context.BIND_AUTO_CREATE);

        initViews();
        mIsInitiator = true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        start();
    }

    @Override
    protected void onDestroy() {
        hangup();
        mRootEglBase.release();
        mCameraCapturer.dispose();
        PeerConnectionFactory.shutdownInternalTracer();
        disconnectService();
        super.onDestroy();
    }

    private void initViews() {
        mRootEglBase = EglBase.create();
        mLocalVideoView = findViewById(R.id.local_gl_surface_view);
        mLocalVideoView.init(mRootEglBase.getEglBaseContext(), null);
        mLocalVideoView.setZOrderMediaOverlay(true);
        mLocalVideoView.setMirror(false);
        mRemoteVideoView = findViewById(R.id.remote_gl_surface_view);
        mRemoteVideoView.init(mRootEglBase.getEglBaseContext(), null);
        mRemoteVideoView.setZOrderMediaOverlay(true);
        mRemoteVideoView.setMirror(false);
    }

    private void start() {
        getUserMedia();
    }

    private void getUserMedia() {
        logger.info("getUserMedia");

        // Initialize the PeerConnectionFactory globals
        PeerConnectionFactory.InitializationOptions initializationOptions = PeerConnectionFactory.InitializationOptions
                .builder(this)
                .setEnableInternalTracer(true)
                .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        // Create a new PeerConnectionFactory instance
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        options.networkIgnoreMask = 16; // ADAPTER_TYPE_LOOPBACK
        options.disableNetworkMonitor = true;
        mPeerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(mRootEglBase.getEglBaseContext(), false, false))
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(mRootEglBase.getEglBaseContext()))
                .createPeerConnectionFactory();

        // Create VideoSource and VideoTrack instances
        VideoSource videoSource = mPeerConnectionFactory.createVideoSource(false);
        mLocalVideoTrack = mPeerConnectionFactory.createVideoTrack("WebRTC_track_v1", videoSource);
        mLocalVideoTrack.setEnabled(true);

        // Create AudioSource and AudioTrack instances
        AudioSource audioSource = mPeerConnectionFactory.createAudioSource(new MediaConstraints());
        mLocalAudioTrack = mPeerConnectionFactory.createAudioTrack("WebRTC_track_a1", audioSource);
        mLocalAudioTrack.setEnabled(true);

        // Initialize the VideoCapturer
        SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThreadOne", mRootEglBase.getEglBaseContext());
        mCameraCapturer = AndroidCameraCapturer.create(true, true);
        mCameraCapturer.initialize(surfaceTextureHelper, this, videoSource.getCapturerObserver());

        // Start recording
        mCameraCapturer.startCapture(640, 480, 30);
        mLocalVideoTrack.addSink(mLocalVideoView);

        // Try to initiate a call
        if(mIsInitiator) {
            final Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    initiateCall();
                }
            }, 2000); // FIXME - remove delay, proceed when ServiceConnection ready
        }
    }

    private void initiateCall() {
        logger.info("initiateCall");

        if(!mIsStarted && mIsChannelReady && mLocalVideoView != null) {
            createPeerConnection();

            if(mPeerConnection != null && mLocalAudioTrack != null && mLocalVideoTrack != null) {
                mPeerConnection.addTrack(mLocalAudioTrack, Arrays.asList("WebRTC-stream"));
                mPeerConnection.addTrack(mLocalVideoTrack, Arrays.asList("WebRTC-stream"));
                logger.info("added tracks");
            } else {
                logger.severe("tracks not ready");
                if(mPeerConnection == null)
                    logger.severe("mPeerConnection is null");
                if(mLocalAudioTrack == null)
                    logger.severe("mLocalAudioTrack is null");
                if(mLocalVideoTrack == null)
                    logger.severe("mLocalVideoTrack is null");
                return;
            }

            mIsStarted = true;
            if(mIsInitiator) {
                try {
                    logger.info("sending call to SIP");
                    mSipStackRemote.call(mTargetUri);
                } catch (RemoteException ex) {
                    logger.throwing("SessionCallActivity", "initiateCall", ex);
                }
            }
        }
    }

    private void createPeerConnection() {
        logger.info("createPeerConnection");

        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        if(!BuildConfig.TURN_CREDENTIAL.isEmpty()) {
            // TURN server: FIXME - needs trickle ICE signalling support
            PeerConnection.IceServer iceServer = PeerConnection.IceServer.builder(BuildConfig.TURN_SERVER)
                .setUsername(BuildConfig.TURN_USER)
                .setPassword(BuildConfig.TURN_CREDENTIAL)
                .createIceServer();
            iceServers.add(iceServer);
        } else {
            // STUN only
            PeerConnection.IceServer iceServer = PeerConnection.IceServer.builder(BuildConfig.TURN_SERVER)
                .createIceServer();
            iceServers.add(iceServer);
        }

        PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(iceServers);
        config.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        config.bundlePolicy = PeerConnection.BundlePolicy.BALANCED;
        config.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        //config.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY; // FIXME trickle ICE
        config.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE;
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        config.iceTransportsType = PeerConnection.IceTransportsType.ALL;
        config.keyType = PeerConnection.KeyType.ECDSA;

        mPeerConnection = mPeerConnectionFactory.createPeerConnection(config, new CustomPeerConnectionObserver() {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                logger.info("mPeerConnection::onIceCandidate");
                sendIceCandidate(iceCandidate);
            }

            @Override
            public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
                logger.info("mPeerConnection::onAddTrack");
                if(rtpReceiver.track() != null && rtpReceiver.track().kind() != null && rtpReceiver.track().kind().equals("video")) {
                    VideoTrack track = (VideoTrack) rtpReceiver.track();
                    if(track != null) {
                        track.addSink(mRemoteVideoView);
                    }
                }
            }

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
                logger.info("onIceGatheringChange: " + iceGatheringState);
                if(iceGatheringState == PeerConnection.IceGatheringState.COMPLETE) {
                    try {
                        // FIXME revise when trickle ICE implemented
                        logger.info("sending offer to SIP");
                        SessionDescription sessionDescription = mPeerConnection.getLocalDescription();
                        mSipStackRemote.provideOffer(sessionDescription.description);
                    } catch (RemoteException ex) {
                        logger.throwing("SessionCallActivity", "initiateCall", ex);
                    }
                }
            }
        });

        if(mPeerConnection == null) {
            logger.severe("failed to create mPeerConnection, check TURN server URIs");
        }
    }

    private void sendOffer() throws RemoteException {
        logger.info("sendOffer");

        addTransceivers();
        mPeerConnection.createOffer(new CustomSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                mPeerConnection.setLocalDescription(new CustomSdpObserver(), sessionDescription);
                try {
                    // FIXME - trickle ICE - the offer might not have any candidates yet,
                    // we send the INVITE / offer when IceGatheringState.COMPLETE occurs above
                    //mSipStackRemote.provideOffer(sessionDescription.description);
                } catch (Exception e) {
                    logger.throwing("SessionCallActivity", "sendOffer failed.", e);
                }
            }
        }, new MediaConstraints());
    }

    private void sendAnswer() throws RemoteException {
        logger.info("sendAnswer");

        addTransceivers();
        mPeerConnection.createAnswer(new CustomSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                mPeerConnection.setLocalDescription(new CustomSdpObserver(), sessionDescription);
                try {
                    mSipStackRemote.provideAnswer(sessionDescription.description);
                } catch (Exception e) {
                    logger.throwing("SessionCallActivity", "sendAnswer failed.", e);
                }
            }
        }, new MediaConstraints());
    }

    // FIXME trickle ICE
    /*private void addIceCandidate(LinkedTreeMap data) {
        logger.info("addIceCandidate");

        mPeerConnection.addIceCandidate(new IceCandidate(data.get("id").toString(),
                (int)Double.parseDouble(data.get("label").toString()), data.get("candidate").toString()));
    }*/

    // FIXME trickle ICE
    private void sendIceCandidate(IceCandidate iceCandidate) {
        logger.info("sendIceCandidate");

        try {
            // iceCandidate.sdpMLineIndex
            // iceCandidate.sdpMid
            // iceCandidate.sdp
            // FIXME - send ICE candidate
        } catch (Exception e) {
            logger.throwing("SessionCallActivity", "sendOffer failed.", e);
        }
    }

    private void addTransceivers() {
        logger.info("addTransceivers");

        RtpTransceiver.RtpTransceiverInit audioConstraint = new RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY);
        mPeerConnection.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, audioConstraint);
        RtpTransceiver.RtpTransceiverInit videoConstraint = new RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY);
        mPeerConnection.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, videoConstraint);
    }

    private void hangup() {
        logger.info("hangup");

        stopPeerConnection();
        //mSipStackRemote.end(); // FIXME - BYE is not implemented yet
    }

    private void handleRemoteHangup() {
        logger.info("handleRemoteHangup");

        stopPeerConnection();
        mIsInitiator = true;
        runOnUiThread(() -> Toast.makeText(this, getString(R.string.peer_has_left), Toast.LENGTH_LONG).show());
    }

    private void stopPeerConnection() {
        logger.info("stopPeerConnection");

        mIsStarted = false;
        mIsChannelReady = false;
        if(mPeerConnection != null) {
            mPeerConnection.close();
            mPeerConnection = null;
        }
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        // Called when the connection with the service is established
        public void onServiceConnected(ComponentName className, IBinder service) {
            // Following the example above for an AIDL interface,
            // this gets an instance of the IRemoteInterface, which we can use to call on the service
            mSipStackRemote = SipStackRemote.Stub.asInterface(service);
            try {
                mIsChannelReady = true;
                mSipStackRemote.registerUser(mSipUser);
            } catch (RemoteException ex) {
                logger.throwing("MainActivity", "onServiceConnected", ex);
            }
            logger.info("Service is bound");
        }

        // Called when the connection with the service disconnects unexpectedly
        public void onServiceDisconnected(ComponentName className) {
            logger.severe("Service has unexpectedly disconnected");
            mSipStackRemote = null;
        }

    };

    private void disconnectService() {
        if(mSipStackRemote == null)
            return;
        unbindService(mConnection);
    }

    private final SipUserRemote.Stub mSipUser = new SipUserRemote.Stub() {
        @Override
        public IBinder asBinder() {
            return this;
        }

        @Override
        public void onMessage(String sender, String body) {
            logger.info("Got a message from " + sender + ": " + body);
        }

        @Override
        public void onProgress(int code, String reason) {
            logger.info("onProgress: " + code + ": " + reason);
        }

        @Override
        public void onConnected() {
            logger.info("onConnected");
        }

        @Override
        public void onFailure(int code, String reason) {
            logger.info("onFailure: " + code + ": " + reason);
        }

        @Override
        public void onIncoming(String caller) {
            logger.info("onIncoming from: " + caller);
        }

        @Override
        public void onTerminated(String reason) {
            logger.info("onTerminated, reason: " + reason);
	    handleRemoteHangup();
        }

        @Override
        public void onOfferRequired() {
            logger.info("onOfferRequired");
            try {
                sendOffer();
            } catch (RemoteException e) {
                // TODO Auto-generated catch block
                logger.throwing("MainActivity", "Stub", e);
            }
        }

        @Override
        public void onAnswerRequired(String offer) {
            logger.info("onAnswerRequired, offer = " + offer);
            try {
                mPeerConnection.setRemoteDescription(new CustomSdpObserver(),
                    new SessionDescription(SessionDescription.Type.OFFER, offer));
                sendAnswer();
            } catch (RemoteException e) {
                // TODO Auto-generated catch block
                logger.throwing("MainActivity", "Stub", e);
            }
        }

        @Override
        public void onAnswer(String answer) { // FIXME - handle PRANSWER, etc
            logger.info("onAnswer: " + answer);
            // FIXME - call the WebRTC stack
	    mPeerConnection.setRemoteDescription(new CustomSdpObserver(),
                new SessionDescription(SessionDescription.Type.ANSWER, answer));
        }
    };
}

