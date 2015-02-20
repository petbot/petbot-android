package com.atos.petbot;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

import petbot.net.stun.DiscoveryInfo;
import petbot.net.stun.StunClient;

import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.json.JSONArray;
import org.json.JSONObject;

import com.atos.audiocontroller.SoundManager;
import com.atos.petbot.xmlrpc.XmlRpcHttpCookieTransportFactory;
import com.atos.util.Log;

import android.support.v7.app.ActionBarActivity;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentTransaction;
import android.text.TextUtils;
import android.text.format.Time;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
//import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IMediaPlayer.OnBufferingUpdateListener;
import tv.danmaku.ijk.media.player.IMediaPlayer.OnErrorListener;
import tv.danmaku.ijk.media.player.IMediaPlayer.OnInfoListener;
import tv.danmaku.ijk.media.player.IMediaPlayer.OnPreparedListener;
import tv.danmaku.ijk.media.widget.VideoView;

enum ActivityResult {
	ACCOUNT_CHOOSER,
	LOG_REQUEST
}

public class MainActivity extends ActionBarActivity implements DeviceNotFoundDialog.NoticeDialogListener {

	private Menu menu = null;
	private MediaPlayer media_player = new MediaPlayer();
	
	private VideoView video_player;
	private Thread video_thread;
	boolean paused = true;
	boolean logged_in = false;
	private View buffering_indicator;
	private String stream_uri = "";
	private Object video_lock = new Object();

	public static String PACKAGE_NAME;
	
	private XmlRpcClient rpc_client;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        
    	super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        PACKAGE_NAME = getApplicationContext().getPackageName();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.registerOnSharedPreferenceChangeListener(com.atos.util.Log.preference_listener);
        String log_level = preferences.getString("log_level", Integer.toString(android.util.Log.INFO));
        Log.setLogLevel(Integer.parseInt(log_level));
        
        getActionBar().setBackgroundDrawable(new ColorDrawable(Color.parseColor("#80000000")));
        
        CookieManager cookie_manager = new CookieManager();
		CookieHandler.setDefault(cookie_manager);

		buffering_indicator = findViewById(R.id.buffering_indicator);

		video_player = (VideoView) findViewById(R.id.video_view);
		video_player.setMediaBufferingIndicator(buffering_indicator);

		video_player.setOnErrorListener(mErrorListener);
		video_player.setOnBufferingUpdateListener(mBufferListener);
		video_player.setOnInfoListener(mInfoListener);
		video_player.setOnPreparedListener(mPreparedListener);

	    XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
		try {
			config.setServerURL(new URL(ServerInfo.url + "/relay"));
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
	    rpc_client = new XmlRpcClient();
		rpc_client.setTransportFactory(new XmlRpcHttpCookieTransportFactory(rpc_client));
		rpc_client.setConfig(config);
	    
		setupVideo();
    }
    
    @Override
	protected void onPause(){
    	super.onPause();
    	synchronized(video_lock){
    		paused = true;
    	}

    	buffering_indicator.setVisibility(View.VISIBLE);
    	if(menu != null){
    		menu.findItem(R.id.action_picture).setEnabled(false);
    	}
    }

    protected void onResume(){
    	
    	super.onResume();
    	paused = false;
    	
    	if(logged_in){
    		Log.i(PACKAGE_NAME, "Resuming: already logged in");
    		synchronized (video_lock) {
                paused = false;
                video_lock.notifyAll();
            }
    	} else {
    		Log.i(PACKAGE_NAME, "Logging in");
    		login();
    	}
    }
    
    private void login(){
    	
    	Intent login_intent = AccountManager.newChooseAccountIntent(null, null, new String[]{"com.atos.petbot"}, true, null, "", null, null);
    	startActivityForResult(login_intent, ActivityResult.ACCOUNT_CHOOSER.ordinal());
    }

    
    public void setupVideo(){
    	
		video_thread = new Thread(new Runnable() {
			public void run() {

		    	Log.i(PACKAGE_NAME, "Starting video");
					
				StunClient client = new StunClient("petbot.ca", 3478);
				DiscoveryInfo stun_info = client.bindForRemoteAddressOnly(null);
				
				// set local and advertised ports for video player
				video_player.setLocalPort(stun_info.getLocalPort());
				video_player.setAdvertisedPort(stun_info.getPublicPort());
				Log.d(PACKAGE_NAME, "STUN local port: " + stun_info.getLocalPort() + ", public port: " + stun_info.getPublicPort());
				
				Object[] result = null;  	
		    	int retries = 0;
				
		    	try{
		    	
				while(true) {
				
					synchronized (video_lock) {
		                while (paused) {
		                    try {
		                        video_lock.wait();
		                    } catch (InterruptedException e) {
		                    }
		                }
		            }
					
					Log.d(PACKAGE_NAME, "Waiting to send stream video request");
					synchronized(rpc_client){
						Log.d(PACKAGE_NAME, "Sending stream video request");
						result = (Object[]) rpc_client.execute("streamVideo", new Object[]{});
					}
					
					Map<String,String> info = (Map<String,String>) result[1];
					String new_stream_uri = info.get("rtsp");
					
					// could not get stream location, wait and try again
					if(new_stream_uri == null || new_stream_uri.isEmpty()){
						
						if(retries < 5){
							retries++;
						}
						int timeout = video_player.isPlaying() ? 5000 : retries * 1000;
						
						try {
							Log.i(PACKAGE_NAME, "Could not get stream location, waiting for " + timeout + "ms");
							Thread.sleep(timeout);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						continue;
					} else {
						Log.i(PACKAGE_NAME, "Stream location: " + new_stream_uri);
						retries = 0;
					}
					
					if (!stream_uri.equals(new_stream_uri)) {
						
						Log.i(PACKAGE_NAME, "New stream location, restart video");
						stream_uri = new_stream_uri;
						
						runOnUiThread(new Runnable() {
							
							@Override
							public void run() {	
								video_player.stopPlayback();
								video_player.setVideoPath(stream_uri);
								video_player.start();
							}
						});
					}
					
					Thread.sleep(5000);
				}
				
		    	} catch (XmlRpcException e) {
					FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
					DialogFragment not_found_dialog = new DeviceNotFoundDialog();
					ft.add(not_found_dialog, "not found");
					ft.commitAllowingStateLoss();
					e.printStackTrace();
					//break;
				} catch (IndexOutOfBoundsException e) {
					FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
					DialogFragment not_found_dialog = new DeviceNotFoundDialog();
					ft.add(not_found_dialog, "please try again");
					ft.commitAllowingStateLoss();
					e.printStackTrace();
					//break;
				} catch (Exception exc) {
					exc.printStackTrace();
				}
			}
		});
	}
    
    public void dropTreat(){
    	new Thread(new Runnable() {
			public void run() {
				
				try {
					
					Boolean result = false;
					Log.i(PACKAGE_NAME, "Sending request to drop treat");
					synchronized(rpc_client){
						result = (Boolean) rpc_client.execute("sendCookie", new Object[]{});
					}
					
					Log.i(PACKAGE_NAME, "Treat drop result: " + result);
					
				} catch (XmlRpcException e) {
					e.printStackTrace();
				}
			}
    	}).start();
    }
    
    public void getSounds(){
    	
    	Log.i(PACKAGE_NAME, "Retrieving sounds list");
    	new Thread(new Runnable() {
			public void run() {			
				try{
					URL server = new URL(ServerInfo.url + "/list_sounds");
					HttpsURLConnection connection = (HttpsURLConnection) server.openConnection();
					connection.setRequestMethod("GET");
					connection.setRequestProperty("Accept", "application/json");
					connection.setRequestProperty("Content-Type", "application/json");
	
					BufferedReader in_stream = new BufferedReader(new InputStreamReader(connection.getInputStream()));
					StringBuilder response = new StringBuilder();
					String line = null;
					while ((line = in_stream.readLine()) != null) {
						response.append(line);
					}
	
					SubMenu sounds_menu = menu.findItem(R.id.action_sound).getSubMenu();
					JSONArray sounds_list = (new JSONObject(response.toString())).getJSONObject("result").getJSONArray("sounds");
					
					for(int index = 0; index < sounds_list.length(); index++){
						
						String sound_name = sounds_list.getString(index);
						Log.d(PACKAGE_NAME, "  " + sound_name);
						sounds_menu.add(R.id.sounds, Menu.NONE, Menu.NONE, sound_name);
					}
					
				} catch(Exception exc){
					exc.printStackTrace();
				}
			}
        }).start();
    }
    
    public void playSound(final String sound_name){

    	new Thread(new Runnable() {
			public void run() {
		    	try {
		    		
		    		Log.i(PACKAGE_NAME, "Sending request to get sound: " + sound_name);
		    		URL server = new URL(ServerInfo.url + "/get_sound/" + sound_name);
		    		HttpsURLConnection connection = (HttpsURLConnection) server.openConnection();
		    		connection.setRequestMethod("GET");
		    		
		    		InputStream in_stream = connection.getInputStream();
		    		byte[] buffer = new byte[32000];

		    		FileOutputStream sound_file = openFileOutput("sound.mp3", Context.MODE_PRIVATE);
		    		int read;
		    		while ((read = in_stream.read(buffer)) != -1) {
		    			sound_file.write(buffer, 0, read);
		    		}
		    		sound_file.flush();
		    		sound_file.close();
		    		
		    		Log.i(PACKAGE_NAME, "Playing sound");
		    		media_player.reset();
		    		media_player.setAudioStreamType(AudioManager.STREAM_MUSIC);
		    		media_player.setDataSource(getFileStreamPath("sound.mp3").getPath());
					media_player.prepare();
					media_player.start();
					
					String sound_url = ServerInfo.url + "/get_sound/" + sound_name;
					
					Boolean result;
					Log.d(PACKAGE_NAME, "Waiting to send request to play sound on device");
					
					synchronized(rpc_client){
						Log.i(PACKAGE_NAME, "Sending request to play sound on device");
						result = (Boolean) rpc_client.execute("playSound", new Object[]{sound_url});
					}
					Log.i(PACKAGE_NAME, "Play sound on device result: " + result);

					
				} catch (IOException exc) {
					// TODO Auto-generated catch block
					exc.printStackTrace();
				} catch (XmlRpcException e) {
					FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
					DialogFragment not_found_dialog = new DeviceNotFoundDialog();
					ft.add(not_found_dialog, "not found");
					ft.commitAllowingStateLoss();
					e.printStackTrace();
				}
			}
    	}).start();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        this.menu = menu;
        
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        
    	if(item.getGroupId() == R.id.sounds){
    		playSound(item.getTitle().toString());
    		return true;
    	}
    	
    	switch(item.getItemId()){
    		case R.id.action_drop:
    			dropTreat();
    			return true;
    		
    		case R.id.action_picture:
    			
    			Log.i(PACKAGE_NAME, "Saving screenshot");
    			
    			// grab the video frame and save as bitmap
				ByteBuffer data = ByteBuffer.wrap(video_player.grabFrame());
				Bitmap bitmap = Bitmap.createBitmap(video_player.getVideoWidth(), video_player.getVideoHeight(), Bitmap.Config.RGB_565);
				bitmap.copyPixelsFromBuffer(data);
				
				File album = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "PetBot");
			    album.mkdirs();
				try {
					File image_file = File.createTempFile("img", ".jpg", album);
					FileOutputStream out = new FileOutputStream(image_file);   
					bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
					out.close();
					
					Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
				    mediaScanIntent.setData(Uri.fromFile(image_file));
				    this.sendBroadcast(mediaScanIntent);
					
					// bring up intent to view and share the picture
					Intent view_picture = new Intent();
					view_picture.setAction(Intent.ACTION_VIEW);
					view_picture.setDataAndType(Uri.fromFile(image_file), "image/*");
					startActivity(view_picture);
					
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
							
    			return true;
    			
    		case R.id.action_logs:
    			saveLogcatToFile();
    			return true;
    			
    		case R.id.sound_manager:
    			Intent sound_manager = new Intent(this, SoundManager.class);
    			startActivity(sound_manager);
    			return true;
    			
    		case R.id.action_settings:
    			/*Intent settings = new Intent(this, SettingsActivity.class);
    			startActivity(settings);*/
    			startActivity(new Intent(this, SettingsActivity.class));

    			return true;
    			
    		default:
    			return super.onOptionsItemSelected(item);
    	}
    }

    private OnErrorListener mErrorListener = new OnErrorListener(){
    	public boolean onError(IMediaPlayer mp, int framework_err, int impl_err) {
    		Log.i(PACKAGE_NAME, "ERROR LISTENER");
    		return true;
    	}
    };
    
    private OnBufferingUpdateListener mBufferListener = new OnBufferingUpdateListener(){
    	public void onBufferingUpdate(IMediaPlayer mp, int percent) {
    		Log.i(PACKAGE_NAME, "BUFFER LISTENER");
    	}
    };
    
    private OnInfoListener mInfoListener = new OnInfoListener() {
        @Override
        public boolean onInfo(IMediaPlayer mp, int what, int extra) {
            Log.i(PACKAGE_NAME, "onInfo: (" + what + "," + extra + ")");

            if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_START) {
                Log.i(PACKAGE_NAME, "onInfo: (MEDIA_INFO_BUFFERING_START)");
                video_player.stopPlayback();
                stream_uri = "";
            } else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_END) {
                Log.i(PACKAGE_NAME, "onInfo: (MEDIA_INFO_BUFFERING_END)");
            }

            return true;
        }
    };
    
    OnPreparedListener mPreparedListener = new OnPreparedListener() {
		@Override
		public void onPrepared(IMediaPlayer mp) {
			buffering_indicator.setVisibility(View.GONE);
			menu.findItem(R.id.action_picture).setEnabled(true);
		}
    };
    
    public static String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException ex) {
            ex.printStackTrace();
        }
        return null;
    }
    
	@Override
	public void onDialogPositiveClick(DialogFragment dialog) {
		try {
			video_thread.join();
			setupVideo();
			video_thread.start();
		} catch (InterruptedException exc) {
			// TODO Auto-generated catch block
			exc.printStackTrace();
		}
	}

	@Override
	public void onDialogNegativeClick(DialogFragment dialog) {
		//login();
	}
	
	@Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        
		if( resultCode == RESULT_CANCELED)
            return;
        
		if( requestCode == ActivityResult.ACCOUNT_CHOOSER.ordinal() ) {
			
			Bundle bundle = data.getExtras();
        	final Account user = new Account(bundle.getString(AccountManager.KEY_ACCOUNT_NAME), 
        			bundle.getString(AccountManager.KEY_ACCOUNT_TYPE));
        	
        	final AccountManager account_manager = AccountManager.get(this);
        	account_manager.getAuthToken(user, "", null, this, null, null);
        	logged_in = true;
        	
    		new Thread(new Runnable(){
    			public void run(){
    				
    				if(TextUtils.isEmpty(ServerInfo.cookie)){
    					ServerInfo.login(user.name, account_manager.getPassword(user));
    				}
    				
    				Log.i(PACKAGE_NAME, "account info; " + user.name + " " + user.type + " " + ServerInfo.cookie);
    				
    				runOnUiThread(new Runnable(){
    					public void run(){
    						video_thread.start();
    						getSounds();
    					}
    				});
    			}
    		}).start();
		}
    }
	
	public void saveLogcatToFile() {
		
		String file_name = "petbot_" + System.currentTimeMillis() + ".log";
		File log_file = new File(this.getExternalCacheDir(), file_name);
		//File log_file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), file_name);
	    try {
			@SuppressWarnings("unused")
			Process process = Runtime.getRuntime().exec("logcat -f "+ log_file.getAbsolutePath() + " -v threadtime " + PACKAGE_NAME + ":D");
		} catch (IOException exc) {
			// TODO Auto-generated catch block
			exc.printStackTrace();
		}
	    
	    Intent email_intent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", "", null));
	    email_intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(log_file));
	    startActivityForResult(email_intent, 0);
	    //log_file.delete();
	}
    
}
