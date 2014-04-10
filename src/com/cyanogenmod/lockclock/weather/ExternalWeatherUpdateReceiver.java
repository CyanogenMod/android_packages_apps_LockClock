package com.cyanogenmod.lockclock.weather;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class ExternalWeatherUpdateReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if(action != null) {
            Log.e("ExternalWeatherUpdateReceiver", action);
        }
        if("com.cyanogenmod.lockclock.action.FORCE_WEATHER_UPDATE".equals(action)) {
            context.startService(new Intent("com.cyanogenmod.lockclock.action.FORCE_WEATHER_UPDATE").setClass(context, WeatherUpdateService.class));
        } else if ("com.cyanogenmod.lockclock.action.REQUEST_WEATHER_UPDATE".equals(action)) {
            context.startService(new Intent().setClass(context, WeatherUpdateService.class));
        }
    }

}
