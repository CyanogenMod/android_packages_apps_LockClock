/*
 * Copyright (C) 2012 The CyanogenMod Project (DvTonder)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cyanogenmod.lockclock;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

public class ClockApplication extends Application {
	BroadcastReceiver mTickReceiver = new TickReceiver();
	BroadcastReceiver mScreenReceiver = new ScreenReceiver();
	
	private boolean mTickEnabled;
	private boolean mScreenReceiverEnabled;
	
	
	public void disableReceivers() {
		disableTickReceiver();
		disableScreenReciever();
	}
	
	public void enableReceivers() {
		enableTickReceiver();
		enableScreenReceiver();
	}
	
	private void enableTickReceiver() {
		mTickEnabled = true;
		registerReceiver(mTickReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
	}
	
	private void disableTickReceiver() {
		if(mTickReceiver != null && mTickEnabled){
			mTickEnabled = false;
			unregisterReceiver(mTickReceiver);
		}
	}
	
	private void enableScreenReceiver() {
		mScreenReceiverEnabled = true;
		registerReceiver(mScreenReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
		registerReceiver(mScreenReceiver, new IntentFilter(Intent.ACTION_SCREEN_ON));
	}
	
	private void disableScreenReciever() {
		if(mScreenReceiver != null && mScreenReceiverEnabled){
			mScreenReceiverEnabled = false;
			unregisterReceiver(mScreenReceiver);
		}
	}
	
	private void sendRefreshIntent(Context context) {
		// Our refresh intent
		Intent refreshIntent = new Intent(context, ClockWidgetProvider.class);
        refreshIntent.setAction(ClockWidgetService.ACTION_REFRESH);
        context.sendBroadcast(refreshIntent);
	}
	
	private class ScreenReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			// Disables the Tick Receiver on screen off
			// Re-enables it on screen on, also causes a refresh
			String action = intent.getAction();
			if (Intent.ACTION_SCREEN_OFF.equals(action)) {
				disableTickReceiver();
	        } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
	        	sendRefreshIntent(context);
	        	enableTickReceiver();	        	
	        }	
		}		
	}

	private class TickReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			// Forces a refresh of the widget when the time has ticked
			String action = intent.getAction();
			if(Intent.ACTION_TIME_TICK.equals(action)){
				sendRefreshIntent(context);
			}
		}
	}
}
