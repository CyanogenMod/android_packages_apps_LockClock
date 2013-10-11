/*
 * Copyright (C) 2012 The CyanogenMod Project
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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.text.TextUtils;
import android.util.Log;

import com.cyanogenmod.lockclock.ClockWidgetProvider;
import com.cyanogenmod.lockclock.misc.Constants;
import com.cyanogenmod.lockclock.misc.Preferences;

import java.util.Date;

public class WeatherUpdateService extends Service {
    private static final String TAG = "WeatherUpdateService";
    private static final boolean D = Constants.DEBUG;

    public static final String ACTION_FORCE_UPDATE = "com.cyanogenmod.lockclock.action.FORCE_WEATHER_UPDATE";

    private WeatherUpdateTask mTask;

    private static final Criteria sLocationCriteria;
    static {
        sLocationCriteria = new Criteria();
        sLocationCriteria.setPowerRequirement(Criteria.POWER_LOW);
        sLocationCriteria.setAccuracy(Criteria.ACCURACY_COARSE);
        sLocationCriteria.setCostAllowed(false);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (D) Log.v(TAG, "Got intent " + intent);

        if (mTask != null && mTask.getStatus() != AsyncTask.Status.FINISHED) {
            if (D) Log.v(TAG, "Weather update is still active, not starting new update");
            return START_REDELIVER_INTENT;
        }

        boolean force = ACTION_FORCE_UPDATE.equals(intent.getAction());
        if (force) {
            Preferences.setCachedWeatherInfo(this, 0, null);
        }
        if (!shouldUpdate(force)) {
            Log.d(TAG, "Service started, but shouldn't update ... stopping");
            stopSelf();
            return START_NOT_STICKY;
        }

        mTask = new WeatherUpdateTask();
        mTask.execute();

        return START_REDELIVER_INTENT;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (mTask != null && mTask.getStatus() != AsyncTask.Status.FINISHED) {
            mTask.cancel(true);
            mTask = null;
        }
    }

    private boolean shouldUpdate(boolean force) {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();

        if (info == null || !info.isConnected()) {
            if (D) Log.d(TAG, "No network connection is available for weather update");
            return false;
        }

        if (!Preferences.showWeather(this)) {
            if (D) Log.v(TAG, "Weather isn't shown, skip update");
            return false;
        }

        long interval = Preferences.weatherRefreshIntervalInMs(this);
        if (interval == 0 && !force) {
            if (D) Log.v(TAG, "Interval set to manual and update not forced, skip update");
            return false;
        }

        long now = System.currentTimeMillis();
        long lastUpdate = Preferences.lastWeatherUpdateTimestamp(this);
        long due = lastUpdate + interval;

        if (D) Log.d(TAG, "Now " + now + " due " + due + "(" + new Date(due) + ")");

        if (lastUpdate != 0 && now < due) {
            if (D) Log.v(TAG, "Weather update is not due yet");
            return false;
        }

        return true;
    }

    private class WeatherUpdateTask extends AsyncTask<Void, Void, WeatherInfo> {
        private WakeLock mWakeLock;
        private Context mContext;

        public WeatherUpdateTask() {
            if (D) Log.d(TAG, "Starting weather update task");
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
            mContext = WeatherUpdateService.this;
        }

        @Override
        protected void onPreExecute() {
            if (D) Log.d(TAG, "ACQUIRING WAKELOCK");
            mWakeLock.acquire();
        }

        private Location getCurrentLocation() {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            Location location = lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
            if (D) Log.v(TAG, "Current location is " + location);
            return location;
        }

        @Override
        protected WeatherInfo doInBackground(Void... params) {
            WeatherProvider provider = new YahooWeatherProvider(mContext);
            String customLocationId = null, customLocationName = null;

            if (Preferences.useCustomWeatherLocation(mContext)) {
                customLocationId = Preferences.customWeatherLocationId(mContext);
                customLocationName = Preferences.customWeatherLocationCity(mContext);
            }

            if (customLocationId != null) {
                return provider.getWeatherInfo(customLocationId, customLocationName);
            }

            Location location = getCurrentLocation();
            if (location != null) {
                WeatherInfo info = provider.getWeatherInfo(location);
                if (info != null) {
                    return info;
                }
            }
            // work with cached location from last request for now
            WeatherInfo cachedInfo = Preferences.getCachedWeatherInfo(mContext);
            if (cachedInfo != null) {
                return provider.getWeatherInfo(cachedInfo.getId(), cachedInfo.getCity());
            }
            // If lastKnownLocation is not present because none of the apps in the
            // device has requested the current location to the system yet, then try to
            // get the current location use the provider that best matches the criteria.
            if (D) Log.d(TAG, "Getting best location provider");
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            String locationProvider = lm.getBestProvider(sLocationCriteria, true);
            if (TextUtils.isEmpty(locationProvider)) {
                Log.e(TAG, "No available location providers matching criteria.");
            } else {
                WeatherLocationListener.registerIfNeeded(mContext, locationProvider);
            }

            return null;
        }

        @Override
        protected void onPostExecute(WeatherInfo result) {
            finish(result);
        }

        @Override
        protected void onCancelled() {
            finish(null);
        }

        private void finish(WeatherInfo result) {
            if (result != null) {
                if (D) Log.d(TAG, "Weather update received, caching data and updating widget");
                long now = System.currentTimeMillis();
                Preferences.setCachedWeatherInfo(mContext, now, result);
                scheduleUpdate(mContext, Preferences.weatherRefreshIntervalInMs(mContext), false);

                Intent updateIntent = new Intent(mContext, ClockWidgetProvider.class);
                sendBroadcast(updateIntent);
            } else if (isCancelled()) {
                // cancelled, likely due to lost network - we'll get restarted
                // when network comes back
            } else {
                // failure, schedule next download in 30 minutes
                if (D) Log.d(TAG, "Weather refresh failed, scheduling update in 30 minutes");
                long interval = 30 * 60 * 1000;
                scheduleUpdate(mContext, interval, false);
            }

            if (D) Log.d(TAG, "RELEASING WAKELOCK");
            mWakeLock.release();
            stopSelf();
        }
    }

    private static class WeatherLocationListener implements LocationListener {
        private Context mContext;
        private static WeatherLocationListener sInstance = null;

        static void registerIfNeeded(Context context, String provider) {
            synchronized (WeatherLocationListener.class) {
                if (D) Log.d(TAG, "Registering location listener");
                if (sInstance == null) {
                    final Context appContext = context.getApplicationContext();
                    final LocationManager locationManager =
                            (LocationManager) appContext.getSystemService(Context.LOCATION_SERVICE);

                    // Check location provider after set sInstance, so, if the provider is not
                    // supported, we never enter here again.
                    sInstance = new WeatherLocationListener(appContext);
                    // Check whether the provider is supported.
                    // NOTE!!! Actually only WeatherUpdateService class is calling this function
                    // with the NETWORK_PROVIDER, so setting the instance is safe. We must
                    // change this if this call receive differents providers
                    LocationProvider lp = locationManager.getProvider(provider);
                    if (lp != null) {
                        if (D) Log.d(TAG, "LocationManager - Requesting single update");
                        locationManager.requestSingleUpdate(provider, sInstance,
                                appContext.getMainLooper());
                    }
                }
            }
        }

        private WeatherLocationListener(Context context) {
            super();
            mContext = context;
        }

        @Override
        public void onLocationChanged(Location location) {
            // Now, we have a location to use. Schedule a weather update right now.
            if (D) Log.d(TAG, "The location has changed, schedule an update ");
            synchronized (WeatherLocationListener.class) {
                WeatherUpdateService.scheduleUpdate(mContext, 0, true);
                sInstance = null;
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            // Not used
        }

        @Override
        public void onProviderEnabled(String provider) {
            // Not used
        }

        @Override
        public void onProviderDisabled(String provider) {
            // Not used
        }
    }

    private static void scheduleUpdate(Context context, long timeFromNow, boolean force) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        long due = System.currentTimeMillis() + timeFromNow;

        if (D) Log.d(TAG, "Scheduling next update at " + new Date(due));
        am.set(AlarmManager.RTC_WAKEUP, due, getUpdateIntent(context, force));
    }

    public static void scheduleNextUpdate(Context context) {
        long lastUpdate = Preferences.lastWeatherUpdateTimestamp(context);
        if (lastUpdate == 0) {
            scheduleUpdate(context, 0, false);
        } else {
            long interval = Preferences.weatherRefreshIntervalInMs(context);
            scheduleUpdate(context, lastUpdate + interval - System.currentTimeMillis(), false);
        }
    }

    public static PendingIntent getUpdateIntent(Context context, boolean force) {
        Intent i = new Intent(context, WeatherUpdateService.class);
        if (force) {
            i.setAction(ACTION_FORCE_UPDATE);
        }
        return PendingIntent.getService(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static void cancelUpdates(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(getUpdateIntent(context, true));
        am.cancel(getUpdateIntent(context, false));
    }
}
