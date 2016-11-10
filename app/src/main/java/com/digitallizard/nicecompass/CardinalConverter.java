package com.digitallizard.nicecompass;

public class CardinalConverter {
	public static final String NORTH = "N";
	public static final String NORTH_WEST = "NW";
	public static final String WEST = "W";
	public static final String SOUTH_WEST = "SW";
	public static final String SOUTH = "S";
	public static final String SOUTH_EAST = "SE";
	public static final String EAST = "E";
	public static final String NORTH_EAST = "NE";
	
	public static final String TRUE_NORTH = "T";
	public static final String MAGNETIC_NORTH = "M";
	
	public static String cardinalFromBearing(float bearing) {
		// convert it to be positive, and call the main function
		if(bearing < 0){
			bearing += 360;
		}
		return cardinalFromPositiveBearing(bearing);
	}
	
	public static String cardinalFromPositiveBearing(float bearing) {
		// decide what string to use
		if(bearing >= 0 && bearing < 22.5){
			return NORTH;
		}
		if(bearing >= 22.5 && bearing < 67.5){
			return NORTH_EAST;
		}
		if(bearing >= 67.5 && bearing < 112.5){
			return EAST;
		}
		if(bearing >= 112.5 && bearing < 157.5){
			return SOUTH_EAST;
		}
		if(bearing >= 157.5 && bearing < 202.5){
			return SOUTH;
		}
		if(bearing >= 202.5 && bearing < 247.5){
			return SOUTH_WEST;
		}
		if(bearing >= 247.5 && bearing < 292.5){
			return WEST;
		}
		if(bearing >= 292.5 && bearing < 337.5){
			return NORTH_WEST;
		}
		if(bearing >= 337.5 && bearing < 360){
			return NORTH;
		}
		
		// in the very unlikely case that the bearing was not recognised:
		return "??";
	}
	
	public static String convertUseTrueNorth(boolean useTrueNorth) {
		if(useTrueNorth) {
			return TRUE_NORTH;
		}
		else {
			return MAGNETIC_NORTH;
		}
	}
}
