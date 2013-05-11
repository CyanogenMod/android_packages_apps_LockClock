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
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

import com.cyanogenmod.lockclock.ClockWidgetProvider;
import com.cyanogenmod.lockclock.misc.Constants;
import com.cyanogenmod.lockclock.misc.Preferences;

import java.io.IOException;
import java.util.Date;

import org.w3c.dom.Document;

public class WeatherUpdateService extends Service {
    private static final String TAG = "WeatherUpdateService";
    private static final boolean D = Constants.DEBUG;

    private static final String URL_YAHOO_API_WEATHER = "http://weather.yahooapis.com/forecastrss?w=%s&u=";

    public static final String ACTION_FORCE_UPDATE = "com.cyanogenmod.lockclock.action.FORCE_WEATHER_UPDATE";

    private WeatherUpdateTask mTask;

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
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
            mContext = WeatherUpdateService.this;
        }

        @Override
        protected void onPreExecute() {
            mWakeLock.acquire();
        }

        private String getWoeidForCustomLocation(String location) {
            // first try with the cached woeid, no need to constantly query constant information
            String woeid = Preferences.getCachedWoeid(mContext);
            if (woeid == null) {
                woeid = YahooPlaceFinder.geoCode(mContext, location);
            }
            if (D) Log.v(TAG, "Yahoo location code for " + location + " is " + woeid);
            return woeid;
        }

        private String getWoeidForCurrentLocation(Location location) {
            String woeid = YahooPlaceFinder.reverseGeoCode(mContext,
                    location.getLatitude(), location.getLongitude());
            if (woeid == null) {
                // we couldn't fetch up-to-date information, fall back to cache
                woeid = Preferences.getCachedWoeid(mContext);
            }
            if (D) Log.v(TAG, "Yahoo location code for current geolocation " + location + " is " + woeid);
            return woeid;
        }

        private Location getCurrentLocation() {
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            Location location = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
            if (D) Log.v(TAG, "Current location is " + location);
            return location;
        }

        private Document getDocument(String woeid) {
            boolean celcius = Preferences.useMetricUnits(mContext);
            String urlWithUnit = URL_YAHOO_API_WEATHER + (celcius ? "c" : "f");

            try {
                return new HttpRetriever().getDocumentFromURL(String.format(urlWithUnit, woeid));
            } catch (IOException e) {
                Log.e(TAG, "Couldn't fetch weather data", e);
            }
            return null;
        }

        @Override
        protected WeatherInfo doInBackground(Void... params) {
            String customLocation = null;
            String woeid = null;

            if (Preferences.useCustomWeatherLocation(mContext)) {
                customLocation = Preferences.customWeatherLocation(mContext);
            }

            if (customLocation != null) {
                woeid = getWoeidForCustomLocation(customLocation);
            } else {
                Location location = getCurrentLocation();
                if (location != null) {
                    woeid = getWoeidForCurrentLocation(location);
                } else {
                    // If lastKnownLocation is not present because none of the apps in the
                    // device has requested the current location to the system yet,
                    // then try to get the current location use an non-accuracy/network provider.
                    WeatherLocationListener.registerIfNeeded(mContext, LocationManager.NETWORK_PROVIDER);
                }
            }

            if (woeid == null || isCancelled()) {
                return null;
            }

            Document doc = getDocument(woeid);
            if (doc == null || isCancelled()) {
                return null;
            }

            return new WeatherXmlParser(mContext).parseWeatherResponse(doc);
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
                long now = System.currentTimeMillis();
                Preferences.setCachedWeatherInfo(mContext, now, result);
                scheduleUpdate(mContext, Preferences.weatherRefreshIntervalInMs(mContext), false);

                Intent updateIntent = new Intent(mContext, ClockWidgetProvider.class);
                sendBroadcast(updateIntent);
            } else if (isCancelled()) {
                /* cancelled, likely due to lost network - we'll get restarted
                 * when network comes back */
            } else {
                /* failure, schedule next download in 30 minutes */
                long interval = 30 * 60 * 1000;
                scheduleUpdate(mContext, interval, false);
            }

            mWakeLock.release();
            stopSelf();
        }
    }

    private static class WeatherLocationListener implements LocationListener {
        private Context mContext;
        private static WeatherLocationListener sInstance = null;

        public static void registerIfNeeded(Context context, String provider) {
            synchronized (WeatherLocationListener.class) {
                if (sInstance == null) {
                    final Context realContext = context.getApplicationContext();
                    final LocationManager locationManager =
                            (LocationManager) realContext.getSystemService(Context.LOCATION_SERVICE);

                    sInstance = new WeatherLocationListener(realContext);
                    locationManager.requestSingleUpdate(provider, sInstance, realContext.getMainLooper());
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

        if (D) Log.v(TAG, "Scheduling next update at " + new Date(due));
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
