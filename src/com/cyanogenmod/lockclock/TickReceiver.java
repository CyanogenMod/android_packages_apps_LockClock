package com.cyanogenmod.lockclock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class TickReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		// Forces a refresh of the widget when the time has ticked
		String action = intent.getAction();
		if(Intent.ACTION_TIME_TICK.equals(action)){
			Intent refreshIntent = new Intent(context, ClockWidgetProvider.class);
	        refreshIntent.setAction(ClockWidgetService.ACTION_REFRESH);
	        context.sendBroadcast(refreshIntent);
		}
	}

}
