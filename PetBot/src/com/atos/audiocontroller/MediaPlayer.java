package com.atos.audiocontroller;

public class MediaPlayer extends android.media.MediaPlayer implements MediaController.MediaPlayerControl {

	boolean seekable = true;
	int buffered = 0;
	boolean prepared;
	OnInfoListener info_listener = null;
	OnBufferingUpdateListener buffer_listener = null;
	OnPreparedListener prepared_listener = null;
	
	public MediaPlayer(){
		
		super();
		
		super.setOnInfoListener(new OnInfoListener(){
			@Override
			public boolean onInfo(android.media.MediaPlayer mp, int what, int extra) {
				
				boolean handled = false;	
				if(info_listener != null){
					handled = info_listener.onInfo(mp, what, extra);
				}	
				if(what == MEDIA_INFO_NOT_SEEKABLE){
					seekable = false;
					handled = true;
				}
				
				return handled;
			}
		});
		
		super.setOnBufferingUpdateListener(new OnBufferingUpdateListener(){
			@Override
			public void onBufferingUpdate(android.media.MediaPlayer mp, int percent) {
				if(buffer_listener != null){
					buffer_listener.onBufferingUpdate(mp, percent);
				}
				buffered = percent;
			}
		});
		
		super.setOnPreparedListener(new OnPreparedListener(){
			@Override
			public void onPrepared(android.media.MediaPlayer mp) {
				if(prepared_listener != null){
					prepared_listener.onPrepared(mp);
				}
				
				prepared = true;
			}
		});
		
	}
	
	@Override
	public void setOnInfoListener(OnInfoListener listener){
		info_listener = listener;
	}
	
	@Override
	public void setOnBufferingUpdateListener(OnBufferingUpdateListener listener){
		buffer_listener = listener;
	}
	
	@Override
	public void setOnPreparedListener(OnPreparedListener listener){
		prepared_listener = listener;
	}
	
	@Override
	public int getDuration(){
		if(prepared){
			return super.getDuration();
		} else {
			return -1;
		}
	}
	
	@Override
	public int getCurrentPosition(){
		if(prepared){
			return super.getCurrentPosition();
		} else {
			return -1;
		}
	}
	
	@Override
	public int getBufferPercentage() {
		return buffered;
	}

	@Override
	public boolean canPause() {
		return true;
	}

	@Override
	public boolean canSeekBackward() {
		return seekable;
	}

	@Override
	public boolean canSeekForward() {
		return seekable;
	}

}
