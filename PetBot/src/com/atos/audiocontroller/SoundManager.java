package com.atos.audiocontroller;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

import org.apache.xmlrpc.XmlRpcException;
import org.json.JSONArray;
import org.json.JSONObject;

import com.atos.petbot.DeviceNotFoundDialog;
import com.atos.petbot.R;
import com.atos.petbot.ServerInfo;

import android.app.Activity;
import android.app.ActionBar;
import android.app.Fragment;
import android.content.Context;

import com.atos.audiocontroller.MediaPlayer;

import android.media.AudioManager;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaRecorder;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.atos.audiocontroller.MediaController;

import android.os.Build;

public class SoundManager extends Activity {

	MediaController audio_controller;
	MediaPlayer media_player;
	MediaRecorder audio_recorder;
	boolean recording = false;
	String sound_name;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_sound_manager);

		audio_controller = new MediaController(this, false);
		audio_controller.setAnchorView((ViewGroup) findViewById(R.id.container));
		getSounds();
		
		final ListView sounds_list = (ListView) findViewById(R.id.sounds_list);
		sounds_list.setOnItemClickListener(new OnItemClickListener(){
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				loadSound((String) sounds_list.getItemAtPosition(position));
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
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
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
	
}
