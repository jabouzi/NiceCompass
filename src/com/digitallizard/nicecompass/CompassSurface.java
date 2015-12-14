/*******************************************************************************
 * NiceCompass
 * Released under the BSD License. See README or LICENSE.
 * Copyright (c) 2011, Digital Lizard (Oscar Key, Thomas Boby)
 * All rights reserved.
 ******************************************************************************/
package com.digitallizard.nicecompass;

import java.text.DecimalFormat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.GradientDrawable.Orientation;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceView;

public class CompassSurface extends SurfaceView implements Runnable {
	/** constants **/
	private static final boolean DRAW_FPS = false;
	
	private static final int STATUS_NO_EVENT = -1;
	
	private static final int TARGET_FPS = 30;
	private static final int MINIMUM_SLEEP_TIME = 10;
	
	private static final int REQUIRED_BEARING_CHANGE = 5;
	private static final int REQUIRED_BEARING_REPEAT = 40;
	private static final float BEARING_X = 50f;
	private static final float BEARING_Y = 15f;
	private static final float DECLENATION_VARIATION_OFFSET = 5f;
	private static final float BEARING_TOUCH_RADIUS = 20f;
	
	private static final float INNER_COMPASS_CARD_RATIO = 7f / 11f;
	private static final float COMPASS_CENTER_X = 50f;
	private static final float COMPASS_CENTER_Y = 60f;
	private static final float CARD_DIAMETER = 90f;
	
	private static final float COMPASS_ACCEL_RATE = 0.9f;
	private static final float COMPASS_SPEED_MODIFIER = 0.26f;
	
	/** variables **/
	private CompassManager compass;
	private Thread animationThread;
	private volatile boolean isRunning;
	private boolean useTrueNorth;
	private float currentFps;
	
	// images
	GradientDrawable backgroundGradient;
	private Bitmap cardImage;
	private Bitmap interferenceImage;
	private Bitmap openPadlockImage;
	private Bitmap closedPadlockImage;
	
	// paint
	private Paint imagePaint;
	private Paint blackPaint;
	private Paint greyPaint;
	private Paint darkGreyPaint;
	private Paint creamPaint;
	private Paint redPaint;
	private Paint bluePaint;
	
	// typeface
	private Typeface roboto;
	
	private float cachedWidthScale;
	private float cachedHeightScale;
	
	private int displayedStatus;
	
	private float bearing;
	private int repeatedBearingCount;
	private volatile String bearingText;
	private DecimalFormat bearingFormat;
	private volatile String declenationText;
	private DecimalFormat declenationFormat;
	
	private float compassCurrentBearing;
	private float compassSpeed;
	
	private boolean bearingLocked;
	private float currentLockedBearing;
	
	private long totalFrames;
	private long totalTime;
	
	
	synchronized boolean isBearingLocked() {
		return bearingLocked;
	}
	
	synchronized void setLockedBearing(int bearing) {
		currentLockedBearing = bearing;
	}
	
	synchronized float getLockedBearing() {
		if(bearingLocked){
			return currentLockedBearing;
		}
		else {
			// return directly up the screen
			return compassCurrentBearing;
		}
	}
	
	synchronized void toggleBearingLock() {
		bearingLocked = !bearingLocked;
		currentLockedBearing = compassCurrentBearing;
	}
	
	float getWidthScale() {
		// check if the scale needs to be initialized
		if(cachedWidthScale == 0f) {
			cachedWidthScale = this.getWidth() / 100f;
		}
		return cachedWidthScale;
	}
	
	float getHeightScale() {
		// check if the scale needs to be initialized
		if(cachedHeightScale == 0f) {
			cachedHeightScale = this.getHeight() / 100f;
		}
		return cachedHeightScale;
	}
	
	void bearingTouched() {
		// toggle magnetic or true north
		useTrueNorth(!useTrueNorth()); // woah, what a function!
	}
	
	void innerCardTouched() {
		// toggle the locked bearing if no status is being displayed
		if(displayedStatus == STATUS_NO_EVENT) {
			toggleBearingLock();
		}
		// dismiss any statuses
		displayedStatus = STATUS_NO_EVENT;
	}
	
	GradientDrawable getBackgroundGradientDrawable() {
		// check if the background is initialised
		if(backgroundGradient == null){
			int[] colors = {0xff3f403f, 0xff666666, 0xff3f403f};
			//int[] colors = {0xff610606, 0xff666666, 0xff610606};
			//int[] colors = {0xff610606, 0xffffffff};
			backgroundGradient = new GradientDrawable(Orientation.TOP_BOTTOM, colors);
			backgroundGradient.setGradientType(GradientDrawable.LINEAR_GRADIENT);
			backgroundGradient.setGradientRadius(10 * getWidthScale());
			backgroundGradient.setDither(true);
			backgroundGradient.setGradientCenter(50 * getWidthScale(), 50 * getHeightScale());
			Rect bounds = new Rect(0, 0, (int)Math.floor(100 * getWidthScale()), (int)Math.floor(100 * getHeightScale()));
			backgroundGradient.setBounds(bounds);
		}
		return backgroundGradient;
	}
	
	void initDrawing() {
		cardImage = BitmapFactory.decodeResource(getResources(), R.drawable.card);
		interferenceImage = BitmapFactory.decodeResource(getResources(), R.drawable.interference);
		openPadlockImage = BitmapFactory.decodeResource(getResources(), R.drawable.padlock_open);
		closedPadlockImage = BitmapFactory.decodeResource(getResources(), R.drawable.padlock_closed);
		
		imagePaint = new Paint();
		imagePaint.setDither(true);
		blackPaint = new Paint();
		blackPaint.setColor(Color.BLACK);
		greyPaint = new Paint();  
		greyPaint.setARGB(255, 179, 179, 179);
		darkGreyPaint = new Paint();
		darkGreyPaint.setARGB(255, 112, 112, 112);
		creamPaint = new Paint();
		creamPaint.setARGB(255, 222, 222, 222);
		redPaint = new Paint();
		redPaint.setColor(Color.RED);
		bluePaint = new Paint();
		bluePaint.setARGB(255, 0, 94, 155);
		
		roboto = Typeface.create("Roboto", Typeface.NORMAL);
		
	}
	 
	float getTextCenterOffset(String text, Paint paint) {
		float[] widths = new float[text.length()];
		paint.getTextWidths(text, widths);
		float totalWidth = 0;
		for(int i = 0; i < text.length(); i++){
			totalWidth += widths[i];
		}
		return totalWidth / 2;
	}
	
	void updateAccuracy() {
		int status = compass.getStatus();
		// check in case the status is already set to an event
		if(displayedStatus == STATUS_NO_EVENT) {
			// only display statuses we can handle
			if(status == CompassManager.STATUS_INTERFERENCE) {
				displayedStatus = status;
			}
		}
	}
	
	void updateCompass() {
		float newBearing = compass.getPositiveBearing(useTrueNorth());
		//float newBearing = bearing;
		// adjust the new bearing to prevent problems involving 360 -- 0
		if(compassCurrentBearing < 90 && newBearing > 270){
			newBearing -= 360;
		}
		if(compassCurrentBearing > 270 && newBearing < 90){
			newBearing +=360; 
		}
		//accuracyText = "target: "+newBearing+" position:"+compassCurrentBearing;
		
		float distance = newBearing - compassCurrentBearing;
		float targetSpeed =  distance * COMPASS_SPEED_MODIFIER;
		// accelerate the compass accordingly
		if(targetSpeed > compassSpeed){
			compassSpeed += COMPASS_ACCEL_RATE;
		}
		if(targetSpeed < compassSpeed){
			compassSpeed -= COMPASS_ACCEL_RATE;
		}
		// stop the compass speed dropping too low
		/*if(Math.abs(compassSpeed) < COMPASS_MINIMUM_SPEED && compassSpeed < 0 && Math.abs(distance) > COMPASS_LOCKON_DISTANCE){
			compassSpeed = -COMPASS_MINIMUM_SPEED;
		}
		if(Math.abs(compassSpeed) < COMPASS_MINIMUM_SPEED && compassSpeed > 0 && Math.abs(distance) > COMPASS_LOCKON_DISTANCE){
			compassSpeed = COMPASS_MINIMUM_SPEED;
		}*/
		compassCurrentBearing += compassSpeed; 
		
		// adjust the bearing for a complete circle
		if(compassCurrentBearing >= 360) {
			compassCurrentBearing -= 360;
		}
		if(compassCurrentBearing < 0) {
			compassCurrentBearing += 360;
		}
	}
	
	void updateBearing() {
		// work out the bearing, dampening jitter
		float newBearing = compass.getPositiveBearing(useTrueNorth());
		if(Math.abs(bearing - newBearing) > REQUIRED_BEARING_CHANGE) {
			bearing = newBearing; // the change is to insignificant to be displayed
			repeatedBearingCount = 0; // reset the repetition count
		} else {
			repeatedBearingCount ++;
			if(repeatedBearingCount > REQUIRED_BEARING_REPEAT) {
				bearing = newBearing;
				repeatedBearingCount = 0;
			}
		}
		bearingText = bearingFormat.format(bearing);
		bearingText += "\u00B0 "; // add the degrees symbol
		bearingText += CardinalConverter.cardinalFromPositiveBearing(bearing); // add the cardinal information
		// add the magnetic 
		bearingText += " " + CardinalConverter.convertUseTrueNorth(useTrueNorth());
		
		declenationText = "";
		if(compass.isUsingManualDeclination()) {
			declenationText += "manual ";
		}
		declenationText += "variation: "+declenationFormat.format(compass.getDeclination())+"\u00B0"; // u00B0 is degrees sign
	}
	
	void update(float delta) {
		updateBearing();
		updateCompass();
		updateAccuracy();
	}
	
	synchronized void triggerDraw() {
		Canvas canvas = null;
		try {
			canvas = this.getHolder().lockCanvas();
			if(canvas != null) {
				this.onDraw(canvas);
			}
		} finally {
			if (canvas != null) {
				this.getHolder().unlockCanvasAndPost(canvas);
			}
		}
	}
	
	@Override
	public void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		
		// update the scales
		float widthScale = getWidthScale();
		float heightScale = getHeightScale();
		
		canvas.drawColor(creamPaint.getColor()); // blank the screen
		//getBackgroundGradientDrawable().draw(canvas);
		
		// draw the bearing information
		blackPaint.setTextSize(70f);
		blackPaint.setTypeface(roboto);
		canvas.drawText(bearingText, (BEARING_X * widthScale) - getTextCenterOffset(bearingText, blackPaint), BEARING_Y * heightScale, blackPaint);
		
		// only draw the declenation text in true north mode
		if(useTrueNorth()) {
			blackPaint.setTextSize(25f);
			canvas.drawText(declenationText, (BEARING_X * widthScale) - getTextCenterOffset(declenationText, blackPaint), 
					(BEARING_Y + DECLENATION_VARIATION_OFFSET) * heightScale, blackPaint);
		}
		
		// draw the inside of the compass card
		int cardDiameter = (int)Math.floor(CARD_DIAMETER * widthScale);
		if(!isBearingLocked()){
			canvas.drawCircle(50 * widthScale, 60 * heightScale, (cardDiameter * INNER_COMPASS_CARD_RATIO) / 2, greyPaint);
		}
		else {
			bluePaint.setStyle(Paint.Style.FILL_AND_STROKE);
			canvas.drawCircle(50 * widthScale, 60 * heightScale, (cardDiameter * INNER_COMPASS_CARD_RATIO) / 2, bluePaint);
		}
		Rect centerRect = new Rect((int)Math.floor(COMPASS_CENTER_X * widthScale - ((cardDiameter * INNER_COMPASS_CARD_RATIO) / 2)), 
				(int)Math.floor(COMPASS_CENTER_Y * heightScale - ((cardDiameter * INNER_COMPASS_CARD_RATIO) / 2)), 
				(int)Math.floor(COMPASS_CENTER_X * widthScale + ((cardDiameter * INNER_COMPASS_CARD_RATIO) / 2)), 
				(int)Math.floor(COMPASS_CENTER_Y * heightScale + ((cardDiameter * INNER_COMPASS_CARD_RATIO) / 2)));
		// draw the right status
		if(displayedStatus == CompassManager.STATUS_INTERFERENCE) {
			canvas.drawBitmap(interferenceImage, null, centerRect, imagePaint);
		}
		
		// if not status draw the bearing lock indicator
		if(displayedStatus == STATUS_NO_EVENT) {
			if(!isBearingLocked()) {
				canvas.drawBitmap(openPadlockImage, null, centerRect, imagePaint);
			}
			if(isBearingLocked()) {
				canvas.drawBitmap(closedPadlockImage, null, centerRect, imagePaint);
				greyPaint.setTextSize(30f);
				String lockedBearingText = bearingFormat.format(getLockedBearing());
				canvas.drawText(lockedBearingText + "\u00B0", 50 * widthScale - getTextCenterOffset(lockedBearingText, greyPaint), 
						(float)((0.17 * CARD_DIAMETER + COMPASS_CENTER_Y) * heightScale), greyPaint);
			}
		}
		
		// draw the compass card
		canvas.rotate(compassCurrentBearing * -1, COMPASS_CENTER_X * widthScale, COMPASS_CENTER_Y * heightScale);
		int cardX = (int)Math.floor(COMPASS_CENTER_X * widthScale - (cardDiameter / 2));
		int cardY = (int)Math.floor(COMPASS_CENTER_Y * heightScale - (cardDiameter / 2));
		Rect cardRect = new Rect(cardX, cardY, cardX + cardDiameter, cardY + cardDiameter);
		canvas.drawBitmap(cardImage, null, cardRect, imagePaint);
		//canvas.restore();
		
		// draw the locked bearing
		canvas.rotate(getLockedBearing(), COMPASS_CENTER_X * widthScale, COMPASS_CENTER_Y * heightScale);
		bluePaint.setStyle(Paint.Style.STROKE);
		bluePaint.setStrokeWidth(3f);
		canvas.drawLine(COMPASS_CENTER_X * widthScale, cardY, COMPASS_CENTER_X * widthScale, cardY + ((1 - INNER_COMPASS_CARD_RATIO) * cardDiameter / 2), bluePaint);
		canvas.restore();
		
		// draw the bezel
		darkGreyPaint.setStyle(Paint.Style.STROKE);
		darkGreyPaint.setStrokeWidth(6f); 
		canvas.drawCircle(COMPASS_CENTER_X * widthScale, COMPASS_CENTER_Y * heightScale, cardDiameter / 2 + 2f, darkGreyPaint);
		canvas.drawLine(COMPASS_CENTER_X * widthScale, cardY, COMPASS_CENTER_X * widthScale, cardY + ((1 - INNER_COMPASS_CARD_RATIO) * cardDiameter / 2), darkGreyPaint);
		darkGreyPaint.setStyle(Paint.Style.FILL);
		
		
		// draw the fps
		if(DRAW_FPS) {
			greyPaint.setTextSize(15f);
			canvas.drawText(Float.toString(currentFps) + " FPS", 1 * widthScale, 98 * heightScale, greyPaint);
		}
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if(event.getAction() == MotionEvent.ACTION_DOWN) {
			float x = event.getX();
			float y = event.getY();
			
			// check if the user touched inside the card centre
			float compassX = COMPASS_CENTER_X * getWidthScale();
			float compassY = COMPASS_CENTER_Y * getHeightScale();
			float distance = (float)Math.sqrt(Math.pow(x - compassX, 2) + Math.pow(y - compassY, 2));
			if(distance < ((CARD_DIAMETER / 2) * INNER_COMPASS_CARD_RATIO * getWidthScale())) {
				innerCardTouched();
				return true; // we used the touch
			}
			
			// check if the user touched the bearing
			float bearingX = BEARING_X * getWidthScale();
			float bearingY = BEARING_Y * getHeightScale();
			distance = (float)Math.sqrt(Math.pow(x - bearingX, 2) + Math.pow(y - bearingY, 2));
			if(distance < (BEARING_TOUCH_RADIUS * getWidthScale())) {
				bearingTouched();
				return true; // we used the touch
			}
		}
		return false; // we did not use the touch
	}
	
	public synchronized void useTrueNorth(boolean useTrueNorth) {
		this.useTrueNorth = useTrueNorth;
	}
	
	public synchronized boolean useTrueNorth() {
		return useTrueNorth;
	}
	
	public synchronized void setManualDeclination(float declination) {
		// this is a thread safe wrapper
		compass.setManualDeclination(declination);
	}
	
	public synchronized void useAutoDeclination() {
		// this is a thread safe wrapper
		compass.useAutoDeclination();
	}
	
	public float getManualDeclination() {
		// return the current declination, manual or not
		return compass.getDeclination();
	}
	
	public boolean isUsingManualDeclination() {
		return compass.isUsingManualDeclination();
	}
	
	public void lockBearingTo(int bearing) {
		// check if the bearing is locked or not
		if(!isBearingLocked()) {
			// lock the bearing
			toggleBearingLock();
		}
		// set the locked bearing to what was requested
		setLockedBearing(bearing);
	}
	
	public void unlockBearing() {
		// toggle the lock if the bearing is locked
		if(isBearingLocked()) {
			toggleBearingLock();
		}
	}
	
	public void stopAnimation() {
		isRunning = false; // stop the animation loop
		float avgFps = (totalFrames * 1000l) / totalTime;
		if(DRAW_FPS) {
			Log.v("compass", "total frames:"+totalFrames+" total time:"+totalTime+" avg. fps:"+Float.toString(avgFps));
		}
	}
	
	public void startAnimation() {
		// set the compass position to prevent spinning
		compassCurrentBearing = compass.getPositiveBearing(useTrueNorth());
		
		// reset the status
		displayedStatus = STATUS_NO_EVENT;
		
		// set variables for working out avg fps
		totalFrames = 0;
		totalTime = 0;
		
		isRunning = true; // flag the loop as running
		// create and start the thread
		animationThread = new Thread(this);
		animationThread.start();
	}
	
	public void run() {
		// initialize a timing variable
		long maxSleepTime = (long) Math.floor(1000 / TARGET_FPS);
		// loop whilst we are told to
		while (isRunning) {
			// record the start time
			long startTime = System.currentTimeMillis();
			
			// update the animation
			update(1); // TODO set up a delta system
			triggerDraw(); // draw the update
			
	 		// work out how long to sleep for
			long finishTime = System.currentTimeMillis();
			long requiredSleepTime = maxSleepTime - (finishTime - startTime);
			// check if the sleep time was too low
			if(requiredSleepTime < MINIMUM_SLEEP_TIME) {  
				requiredSleepTime = MINIMUM_SLEEP_TIME;
			}
			currentFps = 1000 / (requiredSleepTime + (finishTime - startTime));
			totalFrames ++;
			totalTime += (requiredSleepTime + (finishTime - startTime));
			// try to sleep for this time
			try {
				Thread.sleep(requiredSleepTime);
			} catch (InterruptedException e) {
				// do nothing
			}
		}
	}
	
	public CompassSurface(Context context, CompassManager compass, boolean useTrueNorth) {
		super(context);
		this.compass = compass;
		useTrueNorth(useTrueNorth);
				
		// initialize the number formatters
		bearingFormat = new DecimalFormat("000");
		declenationFormat = new DecimalFormat("00.0");
		
		// initialize images
		initDrawing();
	}
}
