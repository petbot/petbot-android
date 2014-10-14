package com.atos.petbot;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.ice4j.StunException;
import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.ice4j.stack.StunStack;
import org.ice4j.stunclient.NetworkConfigurationDiscoveryProcess;
import org.ice4j.stunclient.StunDiscoveryReport;
import org.json.JSONArray;
import org.json.JSONObject;

import com.atos.petbot.xmlrpc.XmlRpcHttpCookieTransportFactory;

import android.support.v7.app.ActionBarActivity;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentTransaction;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.media.SoundPool.OnLoadCompleteListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
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

public class MainActivity extends ActionBarActivity implements DeviceNotFoundDialog.NoticeDialogListener {

	private Menu menu;
	private MediaPlayer media_player = new MediaPlayer();
	
	private VideoView video_player;
	private View buffering_indicator;
	private String stream_uri = "";

	private XmlRpcClient rpc_client;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        
    	super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
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
	    
		login();
    }

    private void login(){
    	
    	final AccountManager account_manager = AccountManager.get(this);
    	final Account[] accounts = account_manager.getAccountsByType("com.atos.petbot");
    	if(accounts.length > 0){
    		account_manager.getAuthToken(accounts[0], "", null, this, null, null);
    		new Thread(new Runnable(){
    			public void run(){
    				ServerInfo.login(accounts[0].name, account_manager.getPassword(accounts[0]));
    				runOnUiThread(new Runnable(){
    					public void run(){
    						startVideo();
    						getSounds();
    					}
    				});
    			}
    		}).start();
    	} else {
    		account_manager.addAccount("com.atos.petbot", "", null, null, this, null, null);
    		startVideo();
    	}
    	
    }

    
    public void startVideo(){
    	
		new Thread(new Runnable() {
			public void run() {

				
				try{
					
					ServerSocket local_socket = new ServerSocket(0);
					StunStack stun_stack = new StunStack();

					// set up local and server addresses
					TransportAddress local_address = new TransportAddress(getLocalIpAddress(), local_socket.getLocalPort(), Transport.UDP);
					TransportAddress server_address = new TransportAddress("stun.sipgate.net", 10000, Transport.UDP);
					
					// query stun server to determine advertised port
					NetworkConfigurationDiscoveryProcess stun_discovery = new NetworkConfigurationDiscoveryProcess(stun_stack, local_address, server_address);
					stun_discovery.start();
					TransportAddress public_address = stun_discovery.determineAddress().getPublicAddress();
					
					// set local and advertised ports for video player
					video_player.setLocalPort(local_socket.getLocalPort());
					video_player.setAdvertisedPort(public_address.getPort());

					stun_discovery.shutDown();
					stun_stack.shutDown();
					local_socket.close();
					
					Object[] result = null;
				
					while(true) {
					
						
						result = (Object[]) rpc_client.execute("streamVideo", new Object[]{});
						
						Map<String,String> info = (Map<String,String>) result[1];
						String new_stream_uri = info.get("rtsp");
						if(new_stream_uri == null || new_stream_uri.isEmpty()){
							
							try {
								Thread.sleep(5000);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							continue;
						}
						
						if (!stream_uri.equals(new_stream_uri)) {
							
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
					try {
						Thread.sleep(5000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}

			}
		}).start();
	}
    
    public void dropTreat(){
    	new Thread(new Runnable() {
			public void run() {
				
				try {
					Boolean result = false;
					result = (Boolean) rpc_client.execute("sendCookie", new Object[]{});
					Log.i("??? treat ???", " " + result);					
				} catch (XmlRpcException e) {
					e.printStackTrace();
				}
			}
    	}).start();
    }
    
    public void getSounds(){
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
						sounds_menu.add(R.id.sounds, Menu.NONE, Menu.NONE, sounds_list.getString(index));
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
		    		URL server = new URL(ServerInfo.url + "/get_sound/" + sound_name);
		    		HttpsURLConnection connection = (HttpsURLConnection) server.openConnection();
		    		connection.setRequestMethod("GET");
		    		
		    		/*CookieManager manager = (CookieManager) CookieManager.getDefault();
					CookieStore cookieJar =  manager.getCookieStore();

					List<HttpCookie> cookies = null;
					cookies = cookieJar.getCookies();

					connection.setRequestProperty("Cookie", cookies.get(0).toString());*/
		    		InputStream in_stream = connection.getInputStream();
		    		byte[] buffer = new byte[32000];

		    		FileOutputStream sound_file = openFileOutput("sound.mp3", Context.MODE_PRIVATE);
		    		//FileOutputStream sound_file = new FileOutputStream("sound.mp3");
		    		int read;
		    		while ((read = in_stream.read(buffer)) != -1) {
		    			sound_file.write(buffer, 0, read);
		    		}
		    		sound_file.flush();
		    		sound_file.close();
		    		
					//media_player.setDataSource(sound_file.getFD());
		    		media_player.reset();
		    		media_player.setAudioStreamType(AudioManager.STREAM_MUSIC);
		    		media_player.setDataSource(getFileStreamPath("sound.mp3").getPath());
					media_player.prepare();
					media_player.start();
					
				} catch (IOException exc) {
					// TODO Auto-generated catch block
					exc.printStackTrace();
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
    			
    			// grab the video frame and save as bitmap
				ByteBuffer data = ByteBuffer.wrap(video_player.grabFrame());
				Bitmap bitmap = Bitmap.createBitmap(video_player.getVideoWidth(), video_player.getVideoHeight(), Bitmap.Config.RGB_565);
				bitmap.copyPixelsFromBuffer(data);
				String image_url = MediaStore.Images.Media.insertImage(getContentResolver(), bitmap, "foo", "Thats a lot of bar.");
				
				// bring up intent to view and share the picture
				Intent view_picture = new Intent();
				view_picture.setAction(Intent.ACTION_VIEW);
				view_picture.setDataAndType(Uri.parse(image_url), "image/*");
				startActivity(view_picture);
				
    			return true;
    			
    		case R.id.action_sound:
    			//playSound(null);
    			return true;
    		default:
    			return super.onOptionsItemSelected(item);
    	}
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
    
    OnPreparedListener mPreparedListener = new OnPreparedListener() {
		@Override
		public void onPrepared(IMediaPlayer mp) {
			buffering_indicator.setVisibility(View.GONE);
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
		startVideo();
	}

	@Override
	public void onDialogNegativeClick(DialogFragment dialog) {
		login();
	}
    
}
