package com.atos.petbot;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;

import com.atos.petbot.xmlrpc.XmlRpcHttpCookieTransportFactory;

import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.media.SoundPool;
import android.media.SoundPool.OnLoadCompleteListener;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.os.Build;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IMediaPlayer.OnBufferingUpdateListener;
import tv.danmaku.ijk.media.player.IMediaPlayer.OnErrorListener;
import tv.danmaku.ijk.media.player.IMediaPlayer.OnInfoListener;
import tv.danmaku.ijk.media.widget.MediaController;
import tv.danmaku.ijk.media.widget.VideoView;

public class MainActivity extends ActionBarActivity implements DeviceNotFoundDialog.NoticeDialogListener {

	private VideoView video_player;
	private View buffering_indicator;
	private String stream_uri = "";
	
	private SoundPool sound_pool;
	private boolean loaded = false;
	private int soundID;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        
    	super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        CookieManager cookie_manager = new CookieManager();
		CookieHandler.setDefault(cookie_manager);

		buffering_indicator = findViewById(R.id.buffering_indicator);
		
		video_player = (VideoView) findViewById(R.id.video_view);
		video_player.setMediaBufferingIndicator(buffering_indicator);
		video_player.setOnErrorListener(mErrorListener);
		//video_player.setOnBufferingUpdateListener(mBufferListener);
		video_player.setOnInfoListener(mInfoListener);
		
		sound_pool = new SoundPool(10, AudioManager.STREAM_MUSIC, 0);
	    sound_pool.setOnLoadCompleteListener(new OnLoadCompleteListener() {
	      @Override
	      public void onLoadComplete(SoundPool soundPool, int sampleId,
	          int status) {
	        loaded = true;
	      }
	    });
	    soundID = sound_pool.load(this, R.raw.mpu, 1);
		
		login();
    }

    private void login(){
    	CookieManager manager = (CookieManager) CookieManager.getDefault();
    	manager.getCookieStore().removeAll();
    	Intent login_activity = new Intent(this, LoginActivity.class);
		startActivityForResult(login_activity, 0);
    }
    
    @Override
	protected void onActivityResult(int request_code, int result_code, Intent data) {
		String auth_token = data.getStringExtra("auth_token");
		Log.i("!!!!!! ", auth_token);

		CookieManager manager = (CookieManager) CookieManager.getDefault();
		CookieStore cookieJar =  manager.getCookieStore();
		List<HttpCookie> cookies = cookieJar.getCookies();
		for (HttpCookie cookie: cookies) {
			Log.i("CookieHandler retrieved cookie: ", cookie.toString());
		}
		
		startVideo();
	}
    
    public void startVideo(){

		Log.i("INNNNNN; ", "VideoView Activity???");
		new Thread(new Runnable() {
			public void run() {

				while(true) {

					Log.i("?????? RESULT; ", "starting command");
					XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
					try {
						config.setServerURL(new URL(ServerInfo.url + "/relay"));
					} catch (MalformedURLException e) {
						e.printStackTrace();
					}
					
					Log.i("?????? RESULT; ", "1");
					
					XmlRpcClient client = new XmlRpcClient();
					client.setTransportFactory(new XmlRpcHttpCookieTransportFactory(client));
					client.setConfig(config);
					Object[] result = null;
					
					Log.i("?????? RESULT; ", "2");
					
					try {
						
						result = (Object[]) client.execute("streamVideo", new Object[]{});
						
						Log.i("?????? RESULT; ", "3");
						
						Map<String,String> info = (Map<String,String>) result[1];
						String new_stream_uri = info.get("rtsp");
						if(new_stream_uri == null || new_stream_uri.isEmpty()){
							
							Log.i("?????? RESULT; ", "4 " + info.toString());
							
							try {
								Thread.sleep(5000);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							continue;
						}
						
						if (!stream_uri.equals(new_stream_uri)) {
							stream_uri = new_stream_uri;
							Log.i("asdfasdfasdf", "URI: " + new_stream_uri);
							Log.i("asdfasdfasdf", "URI: " + stream_uri);
							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									
									Log.i("??? FOO ???", "STOP");
									video_player.stopPlayback();
										
									Log.i("??? FOO ???", "SET");
									video_player.setVideoPath(stream_uri);
									Log.i("??? FOO ???", "START");
									video_player.start();
								}
							});
						}
					} catch (XmlRpcException e) {
						FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
						DialogFragment not_found_dialog = new DeviceNotFoundDialog();
						ft.add(not_found_dialog, "not found");
						ft.commitAllowingStateLoss();
						e.printStackTrace();
						break;
					}

					try {
						Thread.sleep(5000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}

			}
		}).start();
	}
    
    public void dropTreat(View view){
    	new Thread(new Runnable() {
			public void run() {
				
				XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
				try {
					config.setServerURL(new URL(ServerInfo.url + "/relay"));
				} catch (MalformedURLException e) {
					e.printStackTrace();
				}
				
				XmlRpcClient client = new XmlRpcClient();
				client.setTransportFactory(new XmlRpcHttpCookieTransportFactory(client));
				client.setConfig(config);
				Boolean result = false;
				
				try {
					
					result = (Boolean) client.execute("sendCookie", new Object[]{});
					Log.i("??? treat ???", " " + result);
					
				} catch (XmlRpcException e) {
					e.printStackTrace();
				}

				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
    	}).start();
    }

    public void playSound(View view){
    	new Thread(new Runnable() {
			public void run() {
				
				XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
				try {
					config.setServerURL(new URL(ServerInfo.url + "/relay"));
				} catch (MalformedURLException e) {
					e.printStackTrace();
				}
				
				XmlRpcClient client = new XmlRpcClient();
				client.setTransportFactory(new XmlRpcHttpCookieTransportFactory(client));
				client.setConfig(config);
				Boolean result = false;
				
				try {
					
					result = (Boolean) client.execute("playSound", new Object[]{"8"});
					Log.i("??? SOUND ???", " " + result);
					
				} catch (XmlRpcException e) {
					e.printStackTrace();
				}

				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
    	}).start();
    	
		AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
		float actualVolume = (float) audioManager
		    .getStreamVolume(AudioManager.STREAM_MUSIC);
		float maxVolume = (float) audioManager
		    .getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		float volume = actualVolume / maxVolume;
		// Is the sound loaded already?
		if (loaded) {
			sound_pool.play(soundID, volume, volume, 1, 0, 1f);
			Log.e("Test", "Played sound");
		}
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private OnErrorListener mErrorListener = new OnErrorListener(){
    	public boolean onError(IMediaPlayer mp, int framework_err, int impl_err) {
    		Log.i("asdfasdfasdf", "ERROR LISTENER");
    		return true;
    	}
    };
    
    private OnBufferingUpdateListener mBufferListener = new OnBufferingUpdateListener(){
    	public void onBufferingUpdate(IMediaPlayer mp, int percent) {
    		Log.i("asdfasdfasdf", "BUFFER LISTENER");
    	}
    };
    
    private OnInfoListener mInfoListener = new OnInfoListener() {
        @Override
        public boolean onInfo(IMediaPlayer mp, int what, int extra) {
            Log.i("asdfasdfasdf", "onInfo: (" + what + "," + extra + ")");

            if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_START) {
                Log.i("asdfasdfasdf", "onInfo: (MEDIA_INFO_BUFFERING_START)");
                video_player.stopPlayback();
                stream_uri = "";
            } else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_END) {
                Log.i("asdfasdfasdf", "onInfo: (MEDIA_INFO_BUFFERING_END)");
            }

            return true;
        }
    };

	@Override
	public void onDialogPositiveClick(DialogFragment dialog) {
		startVideo();
	}

	@Override
	public void onDialogNegativeClick(DialogFragment dialog) {
		login();
	}
    
}
