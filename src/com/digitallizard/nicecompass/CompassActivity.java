/*******************************************************************************
 * NiceCompass
 * Released under the BSD License. See README or LICENSE.
 * Copyright (c) 2011, Digital Lizard (Oscar Key, Thomas Boby)
 * All rights reserved.
 ******************************************************************************/
package com.digitallizard.nicecompass;

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.Menu;;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

public class CompassActivity extends FragmentActivity {
	public static final String PREF_FILE_NAME = "com.digitallizard.nicecompass_preferences";
	public static final String PREFKEY_USE_TRUE_NORTH = "useTrueNorth";
	public static final String PREFKEY_USE_MANUAL_DECLINATION = "useManualDeclination";
	public static final String PREFKEY_MANUAL_DECLINATION_VALUE = "manualDeclinationValue";
	public static final boolean DEFAULT_USE_TRUE_NORTH = true;
	public static final float DEFAULT_MANUAL_DECLINATION = 0.0f;
	
	public static final int DIALOG_SELECT_LOCKED_BEARING = 0;
	public static final int DIALOG_SELECT_VARIATION = 1;
	
	private CompassManager compass;
	private CompassSurface surface;
	private LinearLayout surfaceContainer;
	
	EditText lockedBearingEditText; // the bearing selection textbox inside the popup
	EditText selectVariationEditText; // the bearing selection textbox inside the popup
	
	private Dialog createSelectBearingDialog() {
		Dialog dialog = new Dialog(this);
		dialog.setContentView(R.layout.bearing_dialog);
		
		EditText bearingText = (EditText) dialog.findViewById(R.id.bearingSelectionText);
		bearingText.setText(""); // set the initial text
		bearingText.requestFocus(); // get the focus
		
		return dialog;
	}
	
	public void closeBearingDialog(int id) {
		dismissDialog(id);
	}

	protected Dialog onCreateDialog(int id) {
		Dialog dialog = null;
		
		// create a generic select bearing dialog
		dialog = createSelectBearingDialog();
		
		Button setButton = (Button) dialog.findViewById(R.id.bearingSetButton);
		Button autoButton = (Button) dialog.findViewById(R.id.bearingAutoButton);
		
		switch(id) {
		case DIALOG_SELECT_LOCKED_BEARING:
			// set the title
			dialog.setTitle(this.getResources().getString(R.string.menu_title_manual_locked_brearing));
			
			// get a reference to the textview for the callbacks
			//FIXME shouldn't store the reference like this
			lockedBearingEditText = (EditText) dialog.findViewById(R.id.bearingSelectionText);
			
			// add the appropriate listeners to the dialog buttons
			// add a listener to the set button
			setButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					// get the value and save it as required
					float value = 0;
					try {
						value = Float.parseFloat(lockedBearingEditText.getText().toString());
					} catch (NumberFormatException e) {
						// probably a temporary error, just give up for now
						return;
					}
					
					// convert the value to a whole number
					value = (float) Math.floor(value);
					// make the value positive
					if(value < 0) {
						value *= -1f;
					}
					// if the value is out of range, move it to within range
					value = value % 360;
					lockedBearingEditText.setText(Integer.toString((int) value));
					
					// lock the bearing to the value
					surface.lockBearingTo((int) value);
					
					// close the dialog
					closeBearingDialog(DIALOG_SELECT_LOCKED_BEARING);
				}
			});
			
			
			// add a listener to the auto button
			autoButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					surface.unlockBearing();
					
					// close the dialog
					closeBearingDialog(DIALOG_SELECT_LOCKED_BEARING);
				}
			});
			
			break;
		case DIALOG_SELECT_VARIATION:
			// set the title
			dialog.setTitle(this.getResources().getString(R.string.menu_title_manual_variation));
			
			// get a reference to the textview for the callbacks
			//FIXME shouldn't store the reference like this
			selectVariationEditText = (EditText) dialog.findViewById(R.id.bearingSelectionText);
			
			// add the appropriate listeners to the dialog buttons
			// add a listener to the set button
			setButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					// get the value and save it as required
					float value = 0;
					try {
						value = Float.parseFloat(selectVariationEditText.getText().toString());
					} catch (NumberFormatException e) {
						// probably a temporary error, just give up for now
						return;
					}
					
					// set the manual declination
					surface.setManualDeclination(value);
					
					// close the dialog
					closeBearingDialog(DIALOG_SELECT_VARIATION);
				}
			});
			
			
			// add a listener to the auto button
			autoButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					// disable manual declination
					surface.useAutoDeclination();
					
					// close the dialog
					closeBearingDialog(DIALOG_SELECT_VARIATION);
				}
			});
			
			break;
		default:
			dialog = null;
		}
		return dialog;
	}
	
    @Override
	public boolean onOptionsItemSelected(MenuItem item) {
    	if(item.getItemId() == R.id.menuItemHelp) {
    		Intent intent = new Intent(this, HelpActivity.class);
    		startActivity(intent);
    		return true; // we have received the press so we can report true
    	} else if(item.getItemId() == R.id.menuItemManualVariation) {
    		showDialog(DIALOG_SELECT_VARIATION);
    		return true; // we have received the press so we can report true
    	} else if(item.getItemId() == R.id.menuItemManualLockedBearing) {
    		showDialog(DIALOG_SELECT_LOCKED_BEARING);
    		return true; // we have received the press so we can report true
    	} else {
    		return super.onOptionsItemSelected(item);
    	}
    }
    
    @Override
	public boolean onCreateOptionsMenu(Menu menu) {
    	super.onCreateOptionsMenu(menu);
    	// inflate the menu XML file
    	getMenuInflater().inflate(R.menu.menu, menu);
    	return true; // we have made the menu so we can return true
    }

	@Override
	public void onPause() {
		// save the current north state
		SharedPreferences settings = this.getSharedPreferences(PREF_FILE_NAME, MODE_PRIVATE);
		Editor editor = settings.edit();
		editor.putBoolean(PREFKEY_USE_TRUE_NORTH, surface.useTrueNorth());
		editor.putBoolean(PREFKEY_USE_MANUAL_DECLINATION, surface.isUsingManualDeclination());
		editor.putFloat(PREFKEY_MANUAL_DECLINATION_VALUE, surface.getManualDeclination());
		editor.commit();
		
		// unregister from the compass to prevent undue battery drain
		compass.unregisterSensors();
		// stop the animation
		surface.stopAnimation();
		// call the superclass
		super.onPause();
	}
	
	@Override
	public void onResume() {
		// class the superclass
		super.onResume();
		// register to receive events from the compass
		compass.registerSensors();
		// start the animation
		surface.startAnimation();
	}
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // create the gui
        setContentView(R.layout.main);
        
        // load in the settings
        SharedPreferences settings = this.getSharedPreferences(PREF_FILE_NAME, MODE_PRIVATE);
        boolean useTrueNorth = settings.getBoolean(PREFKEY_USE_TRUE_NORTH, DEFAULT_USE_TRUE_NORTH);
        
        // initialize variables
        compass = new CompassManager(this);
        surface = new CompassSurface(this, compass, useTrueNorth);
        surfaceContainer = (LinearLayout)findViewById(R.id.compassSurfaceContainer);
        
        // check if we need to enable manual declination
        if(settings.getBoolean(PREFKEY_USE_MANUAL_DECLINATION, false)) {
        	surface.setManualDeclination(settings.getFloat(PREFKEY_MANUAL_DECLINATION_VALUE, DEFAULT_MANUAL_DECLINATION));
        }
        
        // prevent gradient banding
        surface.getHolder().setFormat(android.graphics.PixelFormat.TRANSPARENT);
        
        // add the compass
        surfaceContainer.addView(surface);
    }
}
