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
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import com.cyanogenmod.lockclock.ClockWidgetProvider;
import com.cyanogenmod.lockclock.misc.Constants;
import com.cyanogenmod.lockclock.misc.Preferences;
import com.cyanogenmod.lockclock.misc.WidgetUtils;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

import java.util.Date;

public class WeatherUpdateService extends Service {
    private static final String TAG = "WeatherUpdateService";
    private static final boolean D = Constants.DEBUG;

    public static final String ACTION_FORCE_UPDATE = "com.cyanogenmod.lockclock.action.FORCE_WEATHER_UPDATE";
    private static final String ACTION_CANCEL_LOCATION_UPDATE =
            "com.cyanogenmod.lockclock.action.CANCEL_LOCATION_UPDATE";

    // Broadcast action for end of update
    public static final String ACTION_UPDATE_FINISHED = "com.cyanogenmod.lockclock.action.WEATHER_UPDATE_FINISHED";
    public static final String EXTRA_UPDATE_CANCELLED = "update_cancelled";

    private static final long LOCATION_REQUEST_TIMEOUT = 5L * 60L * 1000L; // request for at most 5 minutes
    private static final long OUTDATED_LOCATION_THRESHOLD_MILLIS = 10L * 60L * 1000L; // 10 minutes

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

        boolean active = mTask != null && mTask.getStatus() != AsyncTask.Status.FINISHED;

        if (ACTION_CANCEL_LOCATION_UPDATE.equals(intent.getAction())) {
            WeatherLocationListener.cancel(this);
            if (!active) {
                stopSelf();
            }
            return START_NOT_STICKY;
        }

        if (active) {
            if (D) Log.v(TAG, "Weather update is still active, not starting new update");
            return START_REDELIVER_INTENT;
        }

        boolean force = ACTION_FORCE_UPDATE.equals(intent.getAction());
        if (!shouldUpdate(force)) {
            Log.d(TAG, "Service started, but shouldn't update ... stopping");
            stopSelf();
            sendCancelledBroadcast();
            return START_NOT_STICKY;
        }

        mTask = new WeatherUpdateTask();
        mTask.execute();

        return START_REDELIVER_INTENT;
    }

    private void sendCancelledBroadcast() {
        Intent finishedIntent = new Intent(ACTION_UPDATE_FINISHED);
        finishedIntent.putExtra(EXTRA_UPDATE_CANCELLED, true);
        sendBroadcast(finishedIntent);
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
        long interval = Preferences.weatherRefreshIntervalInMs(this);
        if (interval == 0 && !force) {
            if (D) Log.v(TAG, "Interval set to manual and update not forced, skip update");
            return false;
        }

        if (force) {
            Preferences.setCachedWeatherInfo(this, 0, null);
        }
        
        long now = System.currentTimeMillis();
        long lastUpdate = Preferences.lastWeatherUpdateTimestamp(this);
        long due = lastUpdate + interval;

        if (D) Log.d(TAG, "Now " + now + " due " + due + "(" + new Date(due) + ")");

        if (lastUpdate != 0 && now < due) {
            if (D) Log.v(TAG, "Weather update is not due yet");
            return false;
        }

        return WidgetUtils.isNetworkAvailable(this);
    }

    private class WeatherUpdateTask extends AsyncTask<Void, Void, WeatherInfo> {
        private WakeLock mWakeLock;
        private Context mContext;

        public WeatherUpdateTask() {
            if (D) Log.d(TAG, "Starting weather update task");
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
            mWakeLock.setReferenceCounted(false);
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

            // If lastKnownLocation is not present (because none of the apps in the
            // device has requested the current location to the system yet) or outdated,
            // then try to get the current location use the provider that best matches the criteria.
            boolean needsUpdate = location == null;
            if (location != null) {
                long delta = System.currentTimeMillis() - location.getTime();
                needsUpdate = delta > OUTDATED_LOCATION_THRESHOLD_MILLIS;
            }
            if (needsUpdate) {
                if (D) Log.d(TAG, "Getting best location provider");
                String locationProvider = lm.getBestProvider(sLocationCriteria, true);
                if (TextUtils.isEmpty(locationProvider)) {
                    Log.e(TAG, "No available location providers matching criteria.");
                } else if (isGooglePlayServicesAvailable()
                        && locationProvider.equals(LocationManager.GPS_PROVIDER)) {
                    // Since Google Play services is available,
                    // let's conserve battery power and not depend on the device's GPS.
                    Log.i(TAG, "Google Play Services available; Ignoring GPS provider.");
                } else {
                    WeatherLocationListener.registerIfNeeded(mContext, locationProvider);
                }
            }

            return location;
        }

        private boolean isGooglePlayServicesAvailable() {
            int result = GooglePlayServicesUtil.isGooglePlayServicesAvailable(mContext);
            return result == ConnectionResult.SUCCESS
                    || result == ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED;
        }

        @Override
        protected WeatherInfo doInBackground(Void... params) {
            WeatherProvider provider = Preferences.weatherProvider(mContext);
            boolean metric = Preferences.useMetricUnits(mContext);
            String customLocationId = null, customLocationName = null;

            if (Preferences.useCustomWeatherLocation(mContext)) {
                customLocationId = Preferences.customWeatherLocationId(mContext);
                customLocationName = Preferences.customWeatherLocationCity(mContext);
            }

            if (customLocationId != null) {
                return provider.getWeatherInfo(customLocationId, customLocationName, metric);
            }

            Location location = getCurrentLocation();
            if (location != null) {
                WeatherInfo info = provider.getWeatherInfo(location, metric);
                if (info != null) {
                    return info;
                }
            }

            // work with cached location from last request for now
            // a listener to update it is already scheduled if possible
            WeatherInfo cachedInfo = Preferences.getCachedWeatherInfo(mContext);
            if (cachedInfo != null) {
                return provider.getWeatherInfo(cachedInfo.getId(), cachedInfo.getCity(), metric);
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
            WeatherContentProvider.updateCachedWeatherInfo(mContext, result);

            Intent finishedIntent = new Intent(ACTION_UPDATE_FINISHED);
            finishedIntent.putExtra(EXTRA_UPDATE_CANCELLED, result == null);
            sendBroadcast(finishedIntent);

            if (D) Log.d(TAG, "RELEASING WAKELOCK");
            mWakeLock.release();
            stopSelf();
        }
    }

    private static class WeatherLocationListener implements LocationListener {
        private Context mContext;
        private PendingIntent mTimeoutIntent;
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
                    // change this if this call receive different providers
                    LocationProvider lp = locationManager.getProvider(provider);
                    if (lp != null) {
                        if (D) Log.d(TAG, "LocationManager - Requesting single update");
                        locationManager.requestSingleUpdate(provider, sInstance,
                                appContext.getMainLooper());
                        sInstance.setTimeoutAlarm();
                    }
                }
            }
        }

        static void cancel(Context context) {
            synchronized (WeatherLocationListener.class) {
                if (sInstance != null) {
                    final Context appContext = context.getApplicationContext();
                    final LocationManager locationManager =
                        (LocationManager) appContext.getSystemService(Context.LOCATION_SERVICE);
                    if (D) Log.d(TAG, "Aborting location request after timeout");
                    locationManager.removeUpdates(sInstance);
                    sInstance.cancelTimeoutAlarm();
                    sInstance = null;
                }
            }
        }

        private WeatherLocationListener(Context context) {
            super();
            mContext = context;
        }

        private void setTimeoutAlarm() {
            Intent intent = new Intent(mContext, WeatherUpdateService.class);
            intent.setAction(ACTION_CANCEL_LOCATION_UPDATE);

            mTimeoutIntent = PendingIntent.getService(mContext, 0, intent,
                    PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_CANCEL_CURRENT);

            AlarmManager am = (AlarmManager) mContext.getSystemService(ALARM_SERVICE);
            long elapseTime = SystemClock.elapsedRealtime() + LOCATION_REQUEST_TIMEOUT;
            am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, elapseTime, mTimeoutIntent);
        }

        private void cancelTimeoutAlarm() {
            if (mTimeoutIntent != null) {
                AlarmManager am = (AlarmManager) mContext.getSystemService(ALARM_SERVICE);
                am.cancel(mTimeoutIntent);
                mTimeoutIntent = null;
            }
        }

        @Override
        public void onLocationChanged(Location location) {
            // Now, we have a location to use. Schedule a weather update right now.
            if (D) Log.d(TAG, "The location has changed, schedule an update ");
            synchronized (WeatherLocationListener.class) {
                WeatherUpdateService.scheduleUpdate(mContext, 0, true);
                cancelTimeoutAlarm();
                sInstance = null;
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            // Now, we have a location to use. Schedule a weather update right now.
            if (D) Log.d(TAG, "The location service has become available, schedule an update ");
            if (status == LocationProvider.AVAILABLE) {
                synchronized (WeatherLocationListener.class) {
                    WeatherUpdateService.scheduleUpdate(mContext, 0, true);
                    cancelTimeoutAlarm();
                    sInstance = null;
                }
            }
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

    public static void scheduleNextUpdate(Context context, boolean force) {
        long lastUpdate = Preferences.lastWeatherUpdateTimestamp(context);
        if (lastUpdate == 0 || force) {
            scheduleUpdate(context, 0, true);
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
        WeatherLocationListener.cancel(context);
    }
}
