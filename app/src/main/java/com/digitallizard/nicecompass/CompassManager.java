/*******************************************************************************
 * NiceCompass
 * Released under the BSD License. See README or LICENSE.
 * Copyright (c) 2011, Digital Lizard (Oscar Key, Thomas Boby)
 * All rights reserved.
 ******************************************************************************/
package com.digitallizard.nicecompass;

import android.content.Context;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

public class CompassManager implements SensorEventListener {
	/** constants **/
	private static final int LOCATION_UPDATE_MIN_TIME = 60000; // the min time in millisecs
	private static final int LOCATION_UPDATE_MIN_DISTANCE = 10000; // the min distance in metres
	public static final int STATUS_GOOD = 0;
	public static final int STATUS_INTERFERENCE = 1;
	public static final int STATUS_INACTIVE = 2;
	private static final float MAGNETIC_INTERFERENCE_THRESHOLD_MODIFIER = 1.05f;
	
	/** variables **/
	private final LocationManager locationManager;
	private final LocationListener locationListener;
	private final SensorManager sensorManager;
	private final Sensor magSensor;
	private final Sensor accelSensor;
	private GeomagneticField geoField;
	private boolean sensorsRegistered; // stores the event listener state
	private boolean sensorHasNewData; // improves performance by only computing the data when required
	private float[] magValues;
	private float[] accelValues;
	private float[] orientationDataCache;
	private Location locationCache;
	private int status;
	
	private boolean useManualDeclination;
	private float manualDeclination;
	
	
	private synchronized float[] getAccelValues() {
		return accelValues;
	}

	private synchronized void setAccelValues(float[] accelValues) {
		this.accelValues = accelValues;
	}

	private synchronized Location getLocation() {
		return locationCache;
	}

	private synchronized void updateLocation(Location locationCache) {
		this.locationCache = locationCache;
	}

	private synchronized float[] getMagValues() {
		return magValues;
	}
	
	private synchronized void setMagValues(float[] values) {
		magValues = values;
	}
	
	private synchronized boolean sensorHasNewData() {
		return sensorHasNewData;
	}
	
	private synchronized void setSensorHasNewData(boolean newData) {
		sensorHasNewData = newData;
	}
	
	
	private void interferenceTest(float[] values) {
		// get the expected values
		float threshold = getExpectedFieldStrength() * MAGNETIC_INTERFERENCE_THRESHOLD_MODIFIER;
		float totalStrength = 1f;
		// loop through the values and test that they are not more than X% above the expected values
		for(int i = 0; i < values.length; i++){
			totalStrength *= values[i];
		}
		if(totalStrength > threshold){
			// report possible interference
			status = STATUS_INTERFERENCE;
		} else {
			status = STATUS_GOOD;
		}
	}
	
	private float getExpectedFieldStrength(){
		// a geo field is required for accurate data
		if(getGeoField() != null){
			return geoField.getFieldStrength();
		} else {
			// provide a field strength over average
			return 60*60*60f;
		}
	}
	
	private synchronized GeomagneticField getGeoField() {
		return geoField;
	}
	
	private synchronized void updateGeoField() {
		Location location = getLocation();
		// we can do nothing without location
		if(location != null) {
			// update the geomagnetic field
			geoField = new GeomagneticField(
		             Double.valueOf(location.getLatitude()).floatValue(),
		             Double.valueOf(location.getLongitude()).floatValue(),
		             Double.valueOf(location.getAltitude()).floatValue(),
		             System.currentTimeMillis());
		}
	}
	
	private float convertToTrueNorth(float bearing){
		return bearing + getDeclination();
	}
	
	private synchronized void setOrientationData(float[] orientationDataCache) {
		this.orientationDataCache = orientationDataCache;
	}
	
	private synchronized float[] getOrientationData() {
		// if there is no new data, bail here
		if(!sensorHasNewData() || getMagValues() == null || getAccelValues() == null){
			return orientationDataCache;
		}
		
		// compute the orientation data
		float[] R = new float[16];
        float[] I = new float[16];
        SensorManager.getRotationMatrix(R, I, getAccelValues(), getMagValues());
        setOrientationData(new float[3]);
        SensorManager.getOrientation(R, orientationDataCache);
		
		// flag the data as computed
        setSensorHasNewData(false);
		
		// return the new data
		return orientationDataCache;
	}
	
	public synchronized boolean isActive() {
		// are the sensors registered
		return sensorsRegistered;
	}
	
	public int getStatus() {
		return status;
	}
	
	public float getDeclination() {
		// if the user wanted manual declination, return this
		if(useManualDeclination) {
			return manualDeclination; // this exits here
		}
		
		// if there is no geomagnetic field, just use the normal bearing
		if(getGeoField() != null) {
			return getGeoField().getDeclination(); // convert magnetic north into true north
		}
		else {
			return 0f; // set the declination to 0
		}
	}
	
	public synchronized void setManualDeclination(float declination) {
		useManualDeclination = true;
		manualDeclination = declination;
	}
	
	public synchronized void useAutoDeclination() {
		useManualDeclination = false;
	}
	
	public boolean isUsingManualDeclination() {
		return useManualDeclination;
	}
	
	public String getCardinal(boolean trueNorth) {
		return CardinalConverter.cardinalFromBearing(getBearing(trueNorth));
	}
	
	public float getBearing(boolean trueNorth) {
		// update the values
		float[] orientationData = getOrientationData();
		
		// bail if the orientation data was null
		if(orientationData == null) {
			return 0f;
		}
		
		// convert the orientation data into a bearing
		float azimuth = orientationData[0];
		float bearing = azimuth * (360 / (2 * (float)Math.PI)); // convert from radians into degrees
		
		// check if we need to convert this into true
		if(trueNorth) {
			bearing = convertToTrueNorth(bearing);
		}
		
		return bearing;
	}
	
	public float getPositiveBearing(boolean trueNorth) {
		// take the given bearing and convert it into 0 <= x < 360
		float bearing = getBearing(trueNorth);
		if(bearing < 0){
			bearing += 360;
		}
		return bearing;
	}
	
	public void unregisterSensors() {
		if(sensorsRegistered){
			// unregister our sensor listeners
			locationManager.removeUpdates(locationListener);
			sensorManager.unregisterListener(this, magSensor);
			sensorManager.unregisterListener(this, accelSensor);
			setSensorHasNewData(false);
			status = STATUS_INACTIVE;
			sensorsRegistered = false; // flag the sensors as unregistered
		}
	}
	
	public void registerSensors() {
		if(!sensorsRegistered) {
			// register our sensor listeners
			// an exception will be thrown if the network provider does not exist
			try {
				locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, LOCATION_UPDATE_MIN_TIME, LOCATION_UPDATE_MIN_DISTANCE, locationListener);
			} catch(IllegalArgumentException e) {
				// TODO: tell the user that their device does not provide network location data
			}
			sensorManager.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_UI);
			sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_UI);
			setSensorHasNewData(true);
			sensorsRegistered = true; // flag the sensors as registered
		}
	}
	
	public void onSensorChanged(SensorEvent event) {
		// save the data from the sensor
		switch(event.sensor.getType()){
		case Sensor.TYPE_MAGNETIC_FIELD:
			setMagValues(event.values.clone());
			// check for interference
			interferenceTest(getMagValues());
			setSensorHasNewData(true);
			break;
		case Sensor.TYPE_ACCELEROMETER:
			setAccelValues(event.values.clone());
			setSensorHasNewData(true);
			break;
		}
	}
	
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
	} 
	
	public CompassManager(Context context) {
		// initialize variables
		locationManager = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
		sensorManager = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
		magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
		accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		sensorsRegistered = false;
		setSensorHasNewData(false);
		status = STATUS_INACTIVE;
		
		// define a listener that listens for location updates
		locationListener = new LocationListener() {
			public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
				// TODO Auto-generated method stub
			}
			
			public void onProviderEnabled(String arg0) {
				// TODO Auto-generated method stub
			}
			
			public void onProviderDisabled(String arg0) {
				// TODO Auto-generated method stub
			}
			
			public void onLocationChanged(Location location) {
				// store the new location
				updateLocation(location);
				updateGeoField(); // update the geomagnetic field
			}
		};
	}
}
