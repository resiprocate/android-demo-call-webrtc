package org.resiprocate.android.basiccall;

import java.util.logging.Logger;

import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import org.resiprocate.android.basicclient.SipService;
import org.resiprocate.android.basicclient.SipStackRemote;
import org.resiprocate.android.basicclient.SipUserRemote;

public class MainActivity extends Activity {
	
	Logger logger = Logger.getLogger(MainActivity.class.getCanonicalName());
	
	SipStackRemote mSipStackRemote;

	final String testSdp = "v=0\r\n" +
		"o=- 0 0 IN IP4 10.1.1.238\r\n" +
		"s=basicAndroidClientTest\r\n" +
		"c=IN IP4 10.1.1.238\r\n" +
		"t=0 0\r\n" +
		"m=audio 8000 RTP/AVP 9 0 8 18 101\r\n" +
                "a=rtpmap:9 G722/8000\r\n" +
                "a=rtpmap:0 PCMU/8000\r\n" +
                "a=rtpmap:8 PCMA/8000\r\n" +
		"a=rtpmap:18 G729/8000\r\n" +
		"a=rtpmap:101 telephone-event/8000\r\n" +
		"a=fmtp:101 0-16\r\n" +
                "a=ptime:20" +
                "a=sendrecv";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		Button buttonSend = (Button) findViewById(R.id.ButtonSend);
		buttonSend.setOnClickListener(new Button.OnClickListener() {
			@Override
			public void onClick(View v) {
				
				EditText recipientField = (EditText)findViewById(R.id.recipient);
				EditText bodyField = (EditText)findViewById(R.id.msg_body);
				
				logger.info("trying to send a message....");
				try {
					mSipStackRemote.sendMessage(
							recipientField.getText().toString(),
							bodyField.getText().toString());
					logger.info("done message send");
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					logger.severe("failed message send");
					e.printStackTrace();
				}
				
			}
		});

		Button buttonCall = (Button) findViewById(R.id.ButtonCall);
		buttonCall.setOnClickListener(new Button.OnClickListener() {
			@Override
			public void onClick(View v) {

				EditText recipientField = (EditText)findViewById(R.id.recipient);

				logger.info("trying to call....");
				try {
					mSipStackRemote.call(
							recipientField.getText().toString());
					logger.info("done call");
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					logger.severe("failed call");
					e.printStackTrace();
				}

			}
		});
				
		Intent intent = new Intent(this, SipService.class);
		intent.setPackage("org.resiprocate.android.basiccall");
		startService(intent);

		bindService(intent,
                mConnection, Context.BIND_AUTO_CREATE);
		
	}
	
	@Override
	public void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		Bundle b = intent.getExtras();
	    if (b != null) {
	        String sender = b.getString("sender");
	        if(sender != null)
	        {
	        	logger.info("Replying to sender: " + sender);
	        	EditText recipientField = (EditText)findViewById(R.id.recipient);
	        	recipientField.setText(sender);
	        } else {
	        	logger.info("No sender to reply to");
	        }
	    } else {
	    	logger.info("no extras found in Intent");
	    }
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		disconnectService();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		
		return true;
	}
	
	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
 
        case R.id.action_settings:
            Intent i = new Intent(this, Settings.class);
            startActivity(i);
            break;
            
        case R.id.action_exit:
    		// This tests the DUM and stack shutdown
    		// Otherwise they just keep running in the background
        	disconnectService();
			stopService(new Intent(SipService.class.getName()));
			// Make the activity screen go away:
			MainActivity.this.finish();
			break;
        }
 
        return true;
    }
	
	private ServiceConnection mConnection = new ServiceConnection() {
	    // Called when the connection with the service is established
	    public void onServiceConnected(ComponentName className, IBinder service) {
	        // Following the example above for an AIDL interface,
	        // this gets an instance of the IRemoteInterface, which we can use to call on the service
	        mSipStackRemote = SipStackRemote.Stub.asInterface(service);
		try {
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
		}

		@Override
		public void onOfferRequired() {
			logger.info("onOfferRequired");
			// FIXME - call the WebRTC stack and set up an Observer to call provideOffer
			try {
				mSipStackRemote.provideOffer(testSdp);
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				logger.throwing("MainActivity", "Stub", e);
			}
		}

		@Override
		public void onAnswerRequired(String offer) {
			logger.info("onAnswerRequired, offer = " + offer);
			// FIXME - call the WebRTC stack
			try {
				mSipStackRemote.provideAnswer(testSdp);
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				logger.throwing("MainActivity", "Stub", e);
			}
		}

		@Override
		public void onAnswer(String answer) {
			logger.info("onAnswer: " + answer);
			// FIXME - call the WebRTC stack
		}
	};
}
