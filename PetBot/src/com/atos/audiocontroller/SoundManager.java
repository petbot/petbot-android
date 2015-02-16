package com.atos.audiocontroller;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

import org.apache.xmlrpc.XmlRpcException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.atos.petbot.DeviceNotFoundDialog;
import com.atos.petbot.R;
import com.atos.petbot.ServerInfo;

import android.app.Activity;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;

import com.atos.audiocontroller.MediaPlayer;
import com.atos.audiocontroller.SaveSoundDialog.SaveSoundListener;

import android.media.AudioManager;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaRecorder;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.app.DialogFragment;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.atos.audiocontroller.MediaController;

import android.os.Build;

public class SoundManager extends Activity implements SaveSoundListener {

	MediaController audio_controller;
	MediaPlayer media_player;
	MediaRecorder audio_recorder;
	boolean recording = false;
	String sound_name;
	private Menu menu;
	ListView sounds_list;
	
	public static String PACKAGE_NAME;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_sound_manager);

		PACKAGE_NAME = getApplicationContext().getPackageName();
		
		audio_controller = new MediaController(this, false);
		audio_controller.setAnchorView((ViewGroup) findViewById(R.id.container));
		getSounds();
		
		sounds_list = (ListView) findViewById(R.id.sounds_list);
		sounds_list.setOnItemClickListener(new OnItemClickListener(){
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				
				loadSound((String) sounds_list.getItemAtPosition(position));
				
				if(menu != null){
					menu.findItem(R.id.action_delete).setEnabled(true);
				}
			}
			
		});
	}

	@Override
	public void onStart(){
		super.onStart();
		media_player = new MediaPlayer();
		media_player.setOnCompletionListener(new OnCompletionListener(){
			@Override
			public void onCompletion(android.media.MediaPlayer mp) {
				audio_controller.updatePausePlay();
			}
		});
		audio_controller.setMediaPlayer(media_player);
		audio_controller.setRecordListener(onRecord);
		audio_controller.setEnabled(false);
	}
	
	@Override
	public void onStop(){
		super.onStop();
		media_player.release();
	}
	
	public void onAttachedToWindow(){
		super.onAttachedToWindow();
		audio_controller.show(0);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		getMenuInflater().inflate(R.menu.sound_manager, menu);
		this.menu = menu;
		
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch(item.getItemId()){
		case R.id.action_settings:
			return true;
			
		case R.id.action_delete:
			deleteSound();
			return true;
		
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	private View.OnClickListener onRecord = new View.OnClickListener() {
		
		@Override
		public void onClick(View v) {
			
			if(!recording){
				audio_recorder = new MediaRecorder();
				audio_recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
				audio_recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
				audio_recorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
				audio_recorder.setOutputFile(getFilesDir() + "/record.mp4");
				
				Toast toast = Toast.makeText(getApplicationContext(), "Recording", Toast.LENGTH_SHORT);
				toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
				toast.show();

				try {
					audio_recorder.prepare();
					
					Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
				    Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
				    r.play();
					
					audio_recorder.start();
				} catch (Exception exc) {
					exc.printStackTrace();
				}
				
			} else {
				audio_recorder.stop();
				audio_recorder.release();
				audio_controller.setEnabled(false);
	    		media_player.reset();
	    		try {
					media_player.setDataSource(getFilesDir() + "/record.mp4");
					media_player.prepare();
				} catch (Exception exc) {
					exc.printStackTrace();
				}
	    		
	    		audio_controller.setMediaPlayer(media_player);
	    			
	    		runOnUiThread(new Runnable(){
					@Override
					public void run() {
						audio_controller.setEnabled(true);
						audio_controller.show(0);
					}	
	    		});
	    		
	    		DialogFragment save_dialog = new SaveSoundDialog();
	    	    save_dialog.show(getFragmentManager(), "save sound");
			}
			
			recording = !recording;
		}
	};
	
	public void getSounds(){
		
		final Context context = this.getBaseContext();
		
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
	
					JSONArray sound_names = (new JSONObject(response.toString())).getJSONObject("result").getJSONArray("sounds");
					final ArrayAdapter<String> sounds_adapter = new ArrayAdapter<String>(context, android.R.layout.simple_list_item_activated_1);
					for(int index = 0; index < sound_names.length(); index++){
						sounds_adapter.add(sound_names.getString(index));
					}
					
					runOnUiThread(new Runnable(){
						public void run(){
							ListView sounds_list = (ListView) findViewById(R.id.sounds_list);
							sounds_list.setAdapter(sounds_adapter);
						}
					});
					
				} catch(Exception exc){
					exc.printStackTrace();
				}
			}
        }).start();
    }

	public void loadSound(final String sound_name){

		final Context context = this.getBaseContext();
		audio_controller.setEnabled(false);
		
    	new Thread(new Runnable() {
			public void run() {
		    	try {
		    		
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
		    		
		    		audio_controller.setEnabled(false);
		    		media_player.reset();
		    		media_player.setDataSource(getFileStreamPath("sound.mp3").getPath());
		    		media_player.prepare();
		    		audio_controller.setMediaPlayer(media_player);
		    			
		    		runOnUiThread(new Runnable(){
						@Override
						public void run() {
							audio_controller.setEnabled(true);
							audio_controller.show(0);
						}	
		    		});
		    		

					
				} catch (IOException exc) {
					// TODO Auto-generated catch block
					exc.printStackTrace();
				}
			}
    	}).start();
    }

	@Override
	public void saveSound(final String name) {

			new Thread(new Runnable(){

				@Override
				public void run() {
					
					try{
						URL server;
						server = new URL("https://petbot.ca/post_sound");
						HttpsURLConnection connection = (HttpsURLConnection) server.openConnection();
						connection.setDoInput(true);
						connection.setDoOutput(true);
						connection.setRequestMethod("POST");
						connection.setRequestProperty("Connection", "Keep-Alive");
						
						CookieManager manager = (CookieManager) CookieManager.getDefault();
						CookieStore cookieJar =  manager.getCookieStore();

						List<HttpCookie> cookies = null;
						cookies = cookieJar.getCookies();

						connection.setRequestProperty("Cookie", cookies.get(0).toString());
						
						String boundary = "---------------------------14737809831466499882746641449";
						connection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
						
						DataOutputStream out_stream = new DataOutputStream(connection.getOutputStream());
						out_stream.writeBytes("--" + boundary + "\r\n");
						out_stream.writeBytes("Content-Disposition: form-data; name=\"file\";filename=\"" + name +".mp4\"" + "\r\n");
						out_stream.writeBytes("Content-Type: application/octet-stream\r\n\r\n");
						
						byte[] buffer = new byte[32000];
						FileInputStream file_stream = openFileInput("record.mp4");
						int read = file_stream.read(buffer, 0, buffer.length);
						while (read > 0){
			                    out_stream.write(buffer, 0, buffer.length);
			                    read = file_stream.read(buffer, 0, buffer.length);
			            }
						out_stream.writeBytes("\r\n");
			            out_stream.writeBytes("--" + boundary + "--\r\n");
			            
			            file_stream.close();
			            out_stream.flush();
			            out_stream.close();
			         
			            // get status and raise alert if not successful
			            int status = connection.getResponseCode();
			            if(status != 200){
			            	
			            	Log.i(PACKAGE_NAME, "save sound: resonse code: " + status);
			            	
			            	runOnUiThread(new Runnable(){
			            		public void run(){
			            			AlertDialog.Builder builder = new AlertDialog.Builder(getApplicationContext());
					            	builder.setMessage("Unexpected response from server.");
					            	builder.create().show();
			            		}
			            	});
			            	
			            	return;
			            }
                        
                        // retrieve the response from server
			            BufferedReader in_stream = new BufferedReader(new InputStreamReader(connection.getInputStream()));
						StringBuilder http_response = new StringBuilder();
						String line = null;
						while ((line = in_stream.readLine()) != null) {
							http_response.append(line);
						}
						
						// check if saving sound was successful
						JSONObject response = new JSONObject(http_response.toString());
						if(!response.getBoolean("success")){
			           
			            	Log.i(PACKAGE_NAME, "save sound unsuccessful: " + response.getString("msg"));
			            	
			            	runOnUiThread(new Runnable(){
			            		public void run(){
			            			AlertDialog.Builder builder = new AlertDialog.Builder(getApplicationContext());
					            	builder.setMessage("Problem saving sound.");
					            	builder.create().show();
			            		}
			            	});
			            	
			            	return;
			            }
						
						runOnUiThread(new Runnable(){
							public void run(){
								Toast toast = Toast.makeText(getApplicationContext(), "Sound uploaded", Toast.LENGTH_SHORT);
								toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
								toast.show();
							}
						});
						
					} catch (MalformedURLException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (JSONException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				
					getSounds();
				}
				
			}).start();
		
	}

	public void deleteSound(){
		
		new Thread(new Runnable() {
			public void run() {
				
				try{
					
					String sound_name = (String) sounds_list.getItemAtPosition(sounds_list.getCheckedItemPosition());
					Log.i(PACKAGE_NAME, sound_name);
					
					URL server;
					server = new URL(ServerInfo.url + "/remove_sound/" + sound_name);
					HttpsURLConnection connection = (HttpsURLConnection) server.openConnection();
					connection.setRequestMethod("POST");
					connection.setRequestProperty("Accept", "application/json");
					connection.setRequestProperty("Content-Type", "application/json");
					
					// retrieve the response from server
		            BufferedReader in_stream = new BufferedReader(new InputStreamReader(connection.getInputStream()));
					StringBuilder http_response = new StringBuilder();
					String line = null;
					while ((line = in_stream.readLine()) != null) {
						http_response.append(line);
					}
					
					Log.i(PACKAGE_NAME, http_response.toString());
					
					// check if saving sound was successful
					JSONObject response = new JSONObject(http_response.toString()).getJSONObject("result");
					if(!response.getBoolean("status")){
		           
		            	Log.i(PACKAGE_NAME, "save sound unsuccessful: " + response.getString("msg"));
		            	
		            	runOnUiThread(new Runnable(){
		            		public void run(){
		            			AlertDialog.Builder builder = new AlertDialog.Builder(getApplicationContext());
				            	builder.setMessage("Problem removing sound.");
				            	builder.create().show();
		            		}
		            	});
		            	
		            	return;
		            }
					
					runOnUiThread(new Runnable(){
						public void run(){
							Toast toast = Toast.makeText(getApplicationContext(), "Sound removed", Toast.LENGTH_SHORT);
							toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
							toast.show();
						}
					});
					
				} catch(MalformedURLException e){
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				getSounds();
				
			}
		}).start();
	}
	
	@Override
	public void playRecordedSound() {
		media_player.start();
	}
	
}
