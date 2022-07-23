package org.resiprocate.android.basiccall;

import java.util.logging.Logger;

import android.Manifest;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.core.app.ActivityCompat;

import org.resiprocate.android.basicclient.SipService;
import org.resiprocate.android.basicclient.SipStackRemote;
import org.resiprocate.android.basicclient.SipUserRemote;

public class MainActivity extends Activity {
	
	Logger logger = Logger.getLogger(MainActivity.class.getCanonicalName());

	private final String[] mPermissions = {
		Manifest.permission.CAMERA,
		Manifest.permission.RECORD_AUDIO,
		Manifest.permission.WRITE_EXTERNAL_STORAGE
	};
	
	SipStackRemote mSipStackRemote;

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
				String targetUri = recipientField.getText().toString();
				Intent intent = new Intent(MainActivity.this, SessionCallActivity.class);
				Bundle dataBundle = new Bundle();
				dataBundle.putString("TARGET_URI", targetUri);
				intent.putExtras(dataBundle);
				startActivity(intent);
				logger.info("done call");
			}
		});
				
		if(!hasPermissions(this, mPermissions)) {
			requestPermissions();
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
		if(!hasPermissions(this, mPermissions)) {
			this.finish();
		}
	}

	private void requestPermissions(){
		int PERMISSION_ALL = 1;

		if(!hasPermissions(this, mPermissions)) {
			ActivityCompat.requestPermissions(this, mPermissions, PERMISSION_ALL);
		}
	}

	private static boolean hasPermissions(Context context, String... permissions) {
		if (context != null && permissions != null) {
			for (String permission : permissions) {
				if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
					return false;
				}
			}
		}
		return true;
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
			stopService(new Intent(SipService.class.getName()));
			// Make the activity screen go away:
			MainActivity.this.finish();
			break;
        }
 
        return true;
    }
	
}

/* ====================================================================
 *
 * Copyright (c) 2021, Dalibor Kofjač https://github.com/dalkofjac/
 * Copyright (c) 2022, Software Freedom Institute https://softwarefreedom.institute
 * Copyright (c) 2013-2022, Daniel Pocock https://danielpocock.com
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. Neither the name of the author(s) nor the names of any contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR(S) AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR(S) OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 * ====================================================================
 *
 *
 */
