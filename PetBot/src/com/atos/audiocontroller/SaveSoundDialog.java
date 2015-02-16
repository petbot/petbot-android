package com.atos.audiocontroller;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class SaveSoundDialog extends DialogFragment {
	
	AlertDialog save_dialog;
	Button save_button;
	
	// event listener interface
	SaveSoundListener save_listener;
	public interface SaveSoundListener {
		public void saveSound(String name);
		public void playRecordedSound();
	}
	
	@Override
    public void onAttach(Activity activity) {
		
		// get event listener methods from activity
		super.onAttach(activity);
		try {
            save_listener = (SaveSoundListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement SaveSoundListener");
        }
	}
	
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState){
		
		// view to set name for sound
		final EditText name_field = new EditText(getActivity());
		name_field.setHint("Enter name for sound");
		
		// disable saving if name empty
		name_field.addTextChangedListener(new TextWatcher(){

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after){}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				save_button.setEnabled(!TextUtils.isEmpty(s));
			}

			@Override
			public void afterTextChanged(Editable s){}
			
		});
		
		// add title, buttons, and name field to dialog
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle("Save sound");
		builder.setView(name_field);
		builder.setPositiveButton("Save", new DialogInterface.OnClickListener(){
			@Override
			public void onClick(DialogInterface dialog, int which){
				save_listener.saveSound(name_field.getText().toString());
			}
		});
		builder.setNeutralButton("Play", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				
			}
		});
		builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener(){
		    @Override
			public void onClick(DialogInterface dialog, int id){
		    	SaveSoundDialog.this.getDialog().cancel();
		    }
	    });

		// make dialog and disable save button
		final AlertDialog save_dialog =  builder.create();
		save_dialog.setOnShowListener(new DialogInterface.OnShowListener(){
			@Override
			public void onShow(DialogInterface dialog) {
				
				save_button = save_dialog.getButton(AlertDialog.BUTTON_POSITIVE);
				save_button.setEnabled(false);
				
				Button play_button = save_dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
				play_button.setOnClickListener(new View.OnClickListener(){
					@Override
					public void onClick(View v) {
						save_listener.playRecordedSound();
					}
				});
			}
		});
		
		return save_dialog;
	}
	
}
