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

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import com.cyanogenmod.lockclock.ClockWidgetProvider;
import com.cyanogenmod.lockclock.misc.Constants;
import com.cyanogenmod.lockclock.misc.Preferences;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import cyanogenmod.weather.CMWeatherManager;
import cyanogenmod.weather.WeatherInfo;
import cyanogenmod.weather.WeatherLocation;

import java.lang.ref.WeakReference;

public class WeatherUpdateService extends JobService {
    private static final String TAG = "WeatherUpdateService";
    private static final boolean D = Constants.DEBUG;

    public static final String ACTION_FORCE_UPDATE
            = "com.cyanogenmod.lockclock.action.FORCE_WEATHER_UPDATE";
    private static final String ACTION_CANCEL_LOCATION_UPDATE =
            "com.cyanogenmod.lockclock.action.CANCEL_LOCATION_UPDATE";

    private static final String ACTION_CANCEL_UPDATE_WEATHER_REQUEST =
            "com.cyanogenmod.lockclock.action.CANCEL_UPDATE_WEATHER_REQUEST";
    private static final long WEATHER_UPDATE_REQUEST_TIMEOUT_MS = 30L * 1000L;

    // Broadcast action for end of update
    public static final String ACTION_UPDATE_FINISHED
            = "com.cyanogenmod.lockclock.action.WEATHER_UPDATE_FINISHED";
    public static final String EXTRA_UPDATE_CANCELLED = "update_cancelled";
    public static final String EXTRA_UPDATE_FAIL_REASON = "fail_reason";

    // request for at most 5 minutes
    private static final long LOCATION_REQUEST_TIMEOUT = 5L * 60L * 1000L;
    // 10 minutes
    private static final long OUTDATED_LOCATION_THRESHOLD_MILLIS = 10L * 60L * 1000L;
    private static final float LOCATION_ACCURACY_THRESHOLD_METERS = 50000;

    private WorkerThread mWorkerThread;
    private JobParameters mJobParams;
    private CMWeatherManager mWeatherManager;

    private static final Criteria sLocationCriteria;

    static {
        sLocationCriteria = new Criteria();
        sLocationCriteria.setPowerRequirement(Criteria.POWER_LOW);
        sLocationCriteria.setAccuracy(Criteria.ACCURACY_COARSE);
        sLocationCriteria.setCostAllowed(true);
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        mWorkerThread = new WorkerThread(this);
        mWorkerThread.start();
        mWorkerThread.prepareHandler();
        mWeatherManager = CMWeatherManager.getInstance(this);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        cancelLocationChangeTimeoutAlarm();
        mWorkerThread.tearDown();
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        if (!mWorkerThread.isProcessing()) {
            mJobParams = params;
            mWorkerThread.getHandler().obtainMessage(WorkerThread.MSG_WEATHER_REQUESTED)
                    .sendToTarget();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        final LocationManager locationManager =
                (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationManager.removeUpdates(mWorkerThread);
        return false;
    }

    private static class WorkerThread extends HandlerThread
            implements CMWeatherManager.WeatherUpdateRequestListener, LocationListener {

        public static final int MSG_WEATHER_REQUESTED = 1;
        public static final int MSG_WEATHER_REQUEST_COMPLETED = 2;
        public static final int MSG_WEATHER_REQUEST_FAILED = 3;
        public static final int MSG_WEATHER_REQUEST_TIMED_OUT = 4;
        public static final int MSG_LOCATION_UPDATE_TIMED_OUT = 5;

        private Handler mHandler;
        private volatile boolean mIsProcessingWeatherUpdate = false;
        private WeakReference<WeatherUpdateService> mServiceRef;

        public WorkerThread(WeatherUpdateService service) {
            super("weather-service-worker");
            mServiceRef = new WeakReference<>(service);
        }

        public synchronized void prepareHandler() {
            mHandler = new Handler(getLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    if (D) Log.d(TAG, "Msg " + msg.what);

                    final WeatherUpdateService service = mServiceRef.get();
                    if (service == null) {
                        //can't continue
                        Log.e(TAG, "WeatherUpdateService not available!");
                        return;
                    }

                    switch (msg.what) {
                        case MSG_WEATHER_REQUESTED:
                            mIsProcessingWeatherUpdate = true;
                            service.onNewWeatherRequest();
                            break;
                        case MSG_WEATHER_REQUEST_COMPLETED:
                            mIsProcessingWeatherUpdate = false;
                            WeatherInfo info = (WeatherInfo) msg.obj;
                            service.onWeatherRequestCompleted(info);
                            break;
                        case MSG_WEATHER_REQUEST_FAILED:
                            mIsProcessingWeatherUpdate = false;
                            int status = msg.arg1;
                            service.onWeatherRequestFailed(status);
                            break;
                        case MSG_WEATHER_REQUEST_TIMED_OUT:
                            mIsProcessingWeatherUpdate = false;
                            int requestId = msg.arg1;
                            service.onCancelUpdateWeatherRequest(requestId);
                            break;
                        case MSG_LOCATION_UPDATE_TIMED_OUT:
                            mIsProcessingWeatherUpdate = false;
                            service.onWeatherRequestFailed(CMWeatherManager.RequestStatus.FAILED);
                        default:
                            //Unknown message, pass it on...
                            super.handleMessage(msg);
                    }
                }
            };
        }

        public Handler getHandler() {
            return mHandler;
        }

        public void tearDown() {
            if (D) Log.d(TAG, "Tearing down worker thread");
            quit();
        }

        public boolean isProcessing() {
            return mIsProcessingWeatherUpdate;
        }

        @Override
        public void onWeatherRequestCompleted(int state, WeatherInfo weatherInfo) {
            if (state == CMWeatherManager.RequestStatus.COMPLETED) {
                mHandler.obtainMessage(WorkerThread.MSG_WEATHER_REQUEST_COMPLETED, weatherInfo)
                        .sendToTarget();
            } else {
                mHandler.obtainMessage(WorkerThread.MSG_WEATHER_REQUEST_FAILED, state, 0)
                        .sendToTarget();
            }
        }

        @Override
        public void onLocationChanged(Location location) {
            // Now, we have a location to use. Schedule a weather update right now.
            if (D) Log.d(TAG, "The location has changed, schedule an update ");
            mHandler.removeMessages(MSG_WEATHER_REQUEST_TIMED_OUT);
            mHandler.obtainMessage(MSG_WEATHER_REQUESTED).sendToTarget();
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            if (D) Log.d(TAG, "The location service has become available, schedule an update ");
            if (status == LocationProvider.AVAILABLE) {
                mHandler.removeMessages(MSG_WEATHER_REQUEST_TIMED_OUT);
                mHandler.obtainMessage(MSG_WEATHER_REQUESTED).sendToTarget();
            }
        }

        @Override
        public void onProviderEnabled(String provider) {
            //No-Op
        }

        @Override
        public void onProviderDisabled(String provider) {
            //No-Op
        }
    }

    private void onNewWeatherRequest() {
        WeatherLocation customWeatherLocation = null;
        if (Preferences.useCustomWeatherLocation(this)) {
            customWeatherLocation = Preferences.getCustomWeatherLocation(this);
        }
        int requestId;
        if (customWeatherLocation != null) {
            requestId = mWeatherManager.requestWeatherUpdate(customWeatherLocation, mWorkerThread);
            if (D) Log.d(TAG, "Request submitted using WeatherLocation");
            startWeatherRequestTimeoutAlarm(requestId);
        } else {
            final Location location = getCurrentLocation();
            if (location != null) {
                requestId = mWeatherManager.requestWeatherUpdate(location, mWorkerThread);
                if (D) Log.d(TAG, "Request submitted using Location");
                startWeatherRequestTimeoutAlarm(requestId);
            } else {
                // work with cached location from last request for now
                // a listener to update it is already scheduled if possible
                WeatherInfo cachedInfo = Preferences.getCachedWeatherInfo(this);
                if (cachedInfo != null) {
                    mWorkerThread.getHandler().obtainMessage(
                            WorkerThread.MSG_WEATHER_REQUEST_COMPLETED, cachedInfo)
                                    .sendToTarget();
                    if (D) Log.d(TAG, "Returning cached weather data [ "
                            + cachedInfo.toString()+ " ]");
                } else {
                    mWorkerThread.getHandler().obtainMessage(
                            WorkerThread.MSG_WEATHER_REQUEST_FAILED,
                                    CMWeatherManager.RequestStatus.FAILED, 0).sendToTarget();
                }
            }
        }
    }

    private void startWeatherRequestTimeoutAlarm(int requestId) {
        final long timeout = SystemClock.uptimeMillis() + WEATHER_UPDATE_REQUEST_TIMEOUT_MS;
        if (D) Log.v(TAG, "Timeout alarm set to expire in " + timeout + " ms");
        Handler handler = mWorkerThread.getHandler();
        handler.sendMessageAtTime(handler.obtainMessage(
                WorkerThread.MSG_WEATHER_REQUEST_TIMED_OUT, requestId), timeout);
    }

    private void cancelWeatherRequestTimeoutAlarm() {
        mWorkerThread.getHandler().removeMessages(WorkerThread.MSG_WEATHER_REQUEST_TIMED_OUT);
        if (D) Log.v(TAG, "Timeout alarm cancelled");
    }

    private void onWeatherRequestCompleted(WeatherInfo result) {
        if (D) Log.d(TAG, "Weather update received, caching data and updating widget");

        cancelWeatherRequestTimeoutAlarm();
        long now = SystemClock.elapsedRealtime();
        Preferences.setCachedWeatherInfo(this, now, result);

        jobFinished(mJobParams, false);

        Intent updateIntent = new Intent(this, ClockWidgetProvider.class);
        sendBroadcast(updateIntent);

        Intent finishedIntent = new Intent(ACTION_UPDATE_FINISHED);
        finishedIntent.putExtra(EXTRA_UPDATE_CANCELLED, false);
        sendBroadcast(finishedIntent);
    }

    private void onWeatherRequestFailed(int status) {
        if (D) Log.d(TAG, "Weather refresh failed ["+status+"]");
        cancelWeatherRequestTimeoutAlarm();
        jobFinished(mJobParams, true);
        Intent finishedIntent = new Intent(ACTION_UPDATE_FINISHED);
        finishedIntent.putExtra(EXTRA_UPDATE_CANCELLED, true);
        finishedIntent.putExtra(EXTRA_UPDATE_FAIL_REASON, status);
        sendBroadcast(finishedIntent);
    }

    private void onCancelUpdateWeatherRequest(int requestId) {
        final CMWeatherManager weatherManager = CMWeatherManager.getInstance(this);
        if (D) Log.d(TAG, "Cancelling active weather request");
        cancelWeatherRequestTimeoutAlarm();
        weatherManager.cancelRequest(requestId);
        jobFinished(mJobParams, false);
        Intent finishedIntent = new Intent(ACTION_UPDATE_FINISHED);
        finishedIntent.putExtra(EXTRA_UPDATE_CANCELLED, true);
        sendBroadcast(finishedIntent);
    }

    private Location getCurrentLocation() {
        final LocationManager lm
                = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Location location = lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
        if (D) Log.v(TAG, "Current location is " + location);

        if (location != null && location.getAccuracy() > LOCATION_ACCURACY_THRESHOLD_METERS) {
            if (D) Log.d(TAG, "Ignoring inaccurate location");
            location = null;
        }

        // If lastKnownLocation is not present (because none of the apps in the
        // device has requested the current location to the system yet) or outdated,
        // then try to get the current location using the provider that best matches the criteria.
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
                registerForLocationChanges(locationProvider);
            }
        }
        return location;
    }

    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability gAPI = GoogleApiAvailability.getInstance();
        int result = gAPI.isGooglePlayServicesAvailable(this);
        return result == ConnectionResult.SUCCESS
                || result == ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED;
    }

    private void registerForLocationChanges(String provider) {
        if (D) Log.d(TAG, "Registering location listener");
        final LocationManager locationManager =
                (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        LocationProvider lp = locationManager.getProvider(provider);
        if (lp != null) {
            if (D) Log.d(TAG, "LocationManager - Requesting single update");
            locationManager.requestSingleUpdate(provider, mWorkerThread, mWorkerThread.getLooper());
            startLocationChangeTimeoutAlarm();
        }
    }

    private void startLocationChangeTimeoutAlarm() {
        final long timeout = SystemClock.uptimeMillis() + LOCATION_REQUEST_TIMEOUT;
        if (D) Log.v(TAG, "Location timeout alarm set to expire in " + timeout + " ms");
        Handler handler = mWorkerThread.getHandler();
        handler.sendMessageAtTime(handler.obtainMessage(
                WorkerThread.MSG_LOCATION_UPDATE_TIMED_OUT), timeout);
    }

    private void cancelLocationChangeTimeoutAlarm() {
        if (D) Log.d(TAG, "Cancel location change alarm");
        mWorkerThread.getHandler().removeMessages(WorkerThread.MSG_LOCATION_UPDATE_TIMED_OUT);
    }
}
