/*
 * Copyright (C) 2016 The CyanogenMod Project
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

package com.cyanogenmod.lockclock.weather;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import com.cyanogenmod.lockclock.ClockWidgetService;
import com.cyanogenmod.lockclock.misc.Constants;
import com.cyanogenmod.lockclock.misc.Preferences;
import cyanogenmod.weather.CMWeatherManager;

public class WeatherSourceListenerService extends Service
        implements CMWeatherManager.WeatherServiceProviderChangeListener {

    private static final String TAG = WeatherSourceListenerService.class.getSimpleName();
    private static final boolean D = Constants.DEBUG;
    private Context mContext;

    @Override
    public void onWeatherServiceProviderChanged(String providerLabel) {
        if (D) Log.d(TAG, "Weather Source changed " + providerLabel);
        Preferences.setWeatherSource(mContext, providerLabel);
        Preferences.setCachedWeatherInfo(mContext, 0, null);
        //The data contained in WeatherLocation is tightly coupled to the weather provider
        //that generated that data, so we need to clear the cached weather location and let the new
        //weather provider regenerate the data if the user decides to use custom location again
        Preferences.setCustomWeatherLocationCity(mContext, null);
        Preferences.setCustomWeatherLocation(mContext, null);
        Preferences.setUseCustomWeatherLocation(mContext, false);

        //Refresh the widget
        mContext.startService(new Intent(mContext, ClockWidgetService.class)
                .setAction(ClockWidgetService.ACTION_REFRESH));

        if (providerLabel != null) {
            mContext.startService(new Intent(mContext, WeatherUpdateService.class)
                    .putExtra(WeatherUpdateService.ACTION_FORCE_UPDATE, true));
        }
    }

    @Override
    public void onCreate() {
        mContext = getApplicationContext();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final CMWeatherManager weatherManager
                = CMWeatherManager.getInstance(mContext);
        weatherManager.registerWeatherServiceProviderChangeListener(this);
        if (D) Log.d(TAG, "Listener registered");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        final CMWeatherManager weatherManager = CMWeatherManager.getInstance(mContext);
        weatherManager.unregisterWeatherServiceProviderChangeListener(this);
        if (D) Log.d(TAG, "Listener unregistered");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
