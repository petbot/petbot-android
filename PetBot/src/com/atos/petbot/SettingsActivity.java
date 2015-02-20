package com.atos.petbot;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Set;

import android.app.Activity;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.util.Log;


public class SettingsActivity extends Activity {

	@Override
    protected void onCreate(Bundle savedInstanceState) {
        
		super.onCreate(savedInstanceState);
        getFragmentManager().beginTransaction().replace(android.R.id.content, new SettingsFragment()).commit();
    }
	
	public static class SettingsFragment extends PreferenceFragment {
	
		public static final LinkedHashMap<String, String> log_levels = new LinkedHashMap<String, String>(){{
			put(Integer.toString(Log.ERROR), "Error");
			put(Integer.toString(Log.WARN), "Warning");
			put(Integer.toString(Log.INFO), "Info");
			put(Integer.toString(Log.DEBUG), "Debug");
			put(Integer.toString(Log.VERBOSE), "Verbose");
		}};
		
		@Override
	    public void onCreate(Bundle savedInstanceState) {
	
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.preferences);
			
			ListPreference log_preference = (ListPreference) findPreference("log_level");
			log_preference.setEntries(log_levels.values().toArray(new String[]{}));
			log_preference.setEntryValues(log_levels.keySet().toArray(new String[]{}));
			
			log_preference.setDefaultValue(log_levels.get(Integer.toString(Log.INFO)));
			log_preference.setSummary(log_preference.getEntry());
			
			log_preference.setOnPreferenceChangeListener(new OnPreferenceChangeListener(){

				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					
					String level_name = log_levels.get((String) newValue);
					((ListPreference) preference).setSummary(level_name);
					
					return true;
				}
				
			});
				
		}
		
	}
}
