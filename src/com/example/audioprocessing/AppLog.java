package com.example.audioprocessing;

import android.util.Log;

public class AppLog
{
	private static final String APP_TAG = "AudioProcessing";

	public static int logString(String message)
	{
		return Log.i(APP_TAG, message);
	}
}
