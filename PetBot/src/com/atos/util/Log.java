package com.atos.util;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;

//import android.util.Log;

public class Log {

	static int log_level = android.util.Log.INFO;
	static String tag = "com.atos.petbot";
	
	public static OnSharedPreferenceChangeListener preference_listener = new OnSharedPreferenceChangeListener(){

		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
			
			switch(key){
			case "log_level":
			
				String value = sharedPreferences.getString(key, Integer.toString(android.util.Log.INFO));
				log_level = Integer.parseInt(value);
				break;
			}
		}
		
	};
	
	public static void setLogLevel(int level){
		log_level = level;
	}
	
	public static int e(String tag, String message){
		return log_level <= android.util.Log.ERROR ? android.util.Log.e(tag, message) : 0;
	}
	
	public static int w(String tag, String message){
		return log_level <= android.util.Log.WARN ? android.util.Log.w(tag, message) : 0;
	}
	
	public static int i(String tag, String message){
		return log_level <= android.util.Log.INFO ? android.util.Log.i(tag, message) : 0;
	}
	
	public static int d(String tag, String message){
		return log_level <= android.util.Log.DEBUG ? android.util.Log.d(tag, message) : 0;
	}
	
	public static int v(String tag, String message){
		return log_level <= android.util.Log.VERBOSE ? android.util.Log.v(tag, message) : 0;
	}
	
}
