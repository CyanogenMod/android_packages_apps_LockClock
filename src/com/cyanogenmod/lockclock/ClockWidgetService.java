/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import com.cyanogenmod.lockclock.misc.Constants;
import com.cyanogenmod.lockclock.weather.HttpRetriever;
import com.cyanogenmod.lockclock.weather.WeatherInfo;
import com.cyanogenmod.lockclock.weather.WeatherXmlParser;
import com.cyanogenmod.lockclock.weather.YahooPlaceFinder;

import org.w3c.dom.Document;

import java.io.IOException;

public class ClockWidgetService extends Service {
    private static final String TAG = "ClockWidgetService";
    private static final boolean DEBUG = true;

    private Context mContext;
    private int[] mWidgetIds;
    private AppWidgetManager mAppWidgetManager;

    @Override
    public void onCreate() {
        mContext = getApplicationContext();
        mAppWidgetManager = AppWidgetManager.getInstance(mContext);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Iterate through all the widgets supported by this provider
        mWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
        if (mWidgetIds != null && mWidgetIds.length != 0) {
            refreshWeather();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
      return null;
    }

    /*
     * CyanogenMod Weather related functionality
     */
    private static final String URL_YAHOO_API_WEATHER = "http://weather.yahooapis.com/forecastrss?w=%s&u=";
    private static WeatherInfo mWeatherInfo = new WeatherInfo();
    private static final int QUERY_WEATHER = 0;
    private static final int UPDATE_WEATHER = 1;
    private boolean mWeatherRefreshing;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case QUERY_WEATHER:
                Thread queryWeather = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        LocationManager locationManager =
                                (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);

                        final ContentResolver resolver = getBaseContext().getContentResolver();
                        boolean useCustomLoc = false; //Settings.System.getInt(resolver,
                                //Settings.System.WEATHER_USE_CUSTOM_LOCATION, 0) == 1;
                        String customLoc = "Toronto, Canada"; //Settings.System.getString(resolver,
                                    //Settings.System.WEATHER_CUSTOM_LOCATION);
                        String woeid = null;

                        // custom location
                        if (customLoc != null && useCustomLoc) {
                            try {
                                woeid = YahooPlaceFinder.GeoCode(mContext, customLoc);
                                if (DEBUG)
                                    Log.d(TAG, "Yahoo location code for " + customLoc + " is " + woeid);
                            } catch (Exception e) {
                                Log.e(TAG, "ERROR: Could not get Location code");
                                e.printStackTrace();
                            }
                        // network location
                        } else {
                            Criteria crit = new Criteria();
                            crit.setAccuracy(Criteria.ACCURACY_COARSE);
                            String bestProvider = locationManager.getBestProvider(crit, true);
                            Location loc = null;
                            if (bestProvider != null) {
                                loc = locationManager.getLastKnownLocation(bestProvider);
                            } else {
                                loc = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
                            }
                            try {
                                if (loc != null) {
                                    woeid = YahooPlaceFinder.reverseGeoCode(mContext, loc.getLatitude(),
                                            loc.getLongitude());
                                    if (DEBUG)
                                        Log.d(TAG, "Yahoo location code for current geolocation is " + woeid);
                                } else {
                                    Log.e(TAG, "ERROR: Location returned null");
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "ERROR: Could not get Location code");
                                e.printStackTrace();
                            }
                        }
                        if (DEBUG) {
                            Log.d(TAG, "Location code is " + woeid);
                        }
                        WeatherInfo w = null;
                        if (woeid != null) {
                            try {
                                w = parseXml(getDocument(woeid));
                            } catch (Exception e) {
                            }
                        }
                        Message msg = Message.obtain();
                        msg.what = UPDATE_WEATHER;
                        msg.obj = w;
                        mHandler.sendMessage(msg);
                    }
                });
                mWeatherRefreshing = true;
                queryWeather.setPriority(Thread.MIN_PRIORITY);
                queryWeather.start();
                break;
            case UPDATE_WEATHER:
                WeatherInfo w = (WeatherInfo) msg.obj;
                if (w != null) {
                    mWeatherRefreshing = false;
                    setWeatherData(w);
                    mWeatherInfo = w;
                } else {
                    mWeatherRefreshing = false;
                    if (mWeatherInfo.temp.equals(WeatherInfo.NODATA)) {
                        setNoWeatherData();
                    } else {
                        setWeatherData(mWeatherInfo);
                    }
                }
                break;
            }
        }
    };

    /**
     * Reload the weather forecast
     */
    private void refreshWeather() {
        final ContentResolver resolver = getBaseContext().getContentResolver();
        SharedPreferences prefs = mContext.getSharedPreferences("LockClock", Context.MODE_MULTI_PROCESS);

        // TODO: figure out when to show that we are refreshing to give the user a visual cue
        // should only be on manual refresh
        //showRefreshing();

        // Load the required settings from preferences
        final long interval = prefs.getInt(Constants.UPDATE_CHECK_PREF, Constants.UPDATE_FREQ_DEFAULT);
        boolean manualSync = (interval == Constants.UPDATE_FREQ_MANUAL);
        if (!manualSync && (((System.currentTimeMillis() - mWeatherInfo.last_sync) / 60000) >= interval)) {
            if (!mWeatherRefreshing) {
                mHandler.sendEmptyMessage(QUERY_WEATHER);
            }
        } else if (manualSync && mWeatherInfo.last_sync == 0) {
            setNoWeatherData();
        } else {
            setWeatherData(mWeatherInfo);
        }
    }

    /**
     * Indicate that the widget is refreshing
     */
    private void showRefreshing() {
        final Resources res = getBaseContext().getResources();
        RemoteViews remoteViews = new RemoteViews(mContext.getPackageName(), R.layout.digital_appwidget);
        remoteViews.setTextViewText(R.id.weather_condition, res.getString(R.string.weather_refreshing));
        for (int widgetId : mWidgetIds) {
            if (DEBUG)
                Log.d(TAG, "Showing refreshing status for Widget ID:" + widgetId);
            mAppWidgetManager.updateAppWidget(widgetId, remoteViews);
        }
    }

    /**
     * Display the weather information
     * @param w
     */
    private void setWeatherData(WeatherInfo w) {
        final ContentResolver resolver = getBaseContext().getContentResolver();
        final Resources res = getBaseContext().getResources();
        boolean showLocation = true; //Settings.System.getInt(resolver,
                //Settings.System.WEATHER_SHOW_LOCATION, 1) == 1;
        boolean showTimestamp = true; //Settings.System.getInt(resolver,
                //Settings.System.WEATHER_SHOW_TIMESTAMP, 1) == 1;
        boolean invertLowhigh = false; //Settings.System.getInt(resolver,
                //Settings.System.WEATHER_INVERT_LOWHIGH, 0) == 1;
        RemoteViews remoteViews = new RemoteViews(mContext.getPackageName(), R.layout.digital_appwidget);

        // Weather Image
        String conditionCode = w.condition_code;
        String condition_filename = "weather_" + conditionCode;
        int resID = res.getIdentifier(condition_filename, "drawable",
                getBaseContext().getPackageName());

        if (DEBUG)
            Log.d("Weather", "Condition:" + conditionCode + " ID:" + resID);

        if (resID != 0) {
            remoteViews.setImageViewResource(R.id.weather_image, resID);
        } else {
            remoteViews.setImageViewResource(R.id.weather_image, R.drawable.weather_na);
        }

        // City
        remoteViews.setTextViewText(R.id.weather_city, w.city);
        remoteViews.setViewVisibility(R.id.weather_city, showLocation ? View.VISIBLE : View.GONE);

        // Weather Condition
        remoteViews.setTextViewText(R.id.weather_condition, w.condition);
        remoteViews.setViewVisibility(R.id.weather_condition, View.VISIBLE);

        // Weather Update Time
        long now = System.currentTimeMillis();
        if (now - w.last_sync < 60000) {
            remoteViews.setTextViewText(R.id.update_time, res.getString(R.string.weather_last_sync_just_now));
        } else {
            remoteViews.setTextViewText(R.id.update_time, DateUtils.getRelativeTimeSpanString(
                    w.last_sync, now, DateUtils.MINUTE_IN_MILLIS));
        }
        remoteViews.setViewVisibility(R.id.update_time, showTimestamp ? View.VISIBLE : View.GONE);

        // Weather Temps Panel
        remoteViews.setTextViewText(R.id.weather_temp, w.temp);
        remoteViews.setTextViewText(R.id.weather_low_high, invertLowhigh ? w.high + " | " + w.low : w.low + " | " + w.high);
        remoteViews.setViewVisibility(R.id.weather_temps_panel, View.VISIBLE);

        // TODO: Make these listeners do something useful
        // Register an onClickListener on Weather
        Intent weatherClickIntent = new Intent(mContext, ClockWidgetProvider.class);
        weatherClickIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        weatherClickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, mWidgetIds);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0, weatherClickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        remoteViews.setOnClickPendingIntent(R.id.weather_panel, pendingIntent);

        // Register an onClickListener on Clock
        Intent clockClickIntent = new Intent(mContext, ClockWidgetProvider.class);
        clockClickIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        clockClickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, mWidgetIds);
        PendingIntent pi = PendingIntent.getBroadcast(mContext, 0, clockClickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        remoteViews.setOnClickPendingIntent(R.id.digital_clock, pi);

        // Update all the widgets and stop
        for (int widgetId : mWidgetIds) {
            mAppWidgetManager.updateAppWidget(widgetId, remoteViews);
        }
        stopSelf();
    }

    /**
     * There is no data to display, display 'empty' fields and the
     * 'Tap to reload' message
     */
    private void setNoWeatherData() {
        final Resources res = getBaseContext().getResources();
        RemoteViews remoteViews = new RemoteViews(mContext.getPackageName(), R.layout.digital_appwidget);
        if (remoteViews != null) {
            remoteViews.setImageViewResource(R.id.weather_image, R.drawable.weather_na);
            remoteViews.setTextViewText(R.id.weather_city, res.getString(R.string.weather_no_data));
            remoteViews.setViewVisibility(R.id.weather_city, View.VISIBLE);
            remoteViews.setTextViewText(R.id.weather_condition, res.getString(R.string.weather_tap_to_refresh));
            remoteViews.setViewVisibility(R.id.update_time, View.GONE);
            remoteViews.setViewVisibility(R.id.weather_temps_panel, View.GONE);
        }

        // Update all the widgets and stop
        for (int widgetId : mWidgetIds) {
            mAppWidgetManager.updateAppWidget(widgetId, remoteViews);
        }
        stopSelf();
    }

    /**
     * Get the weather forecast XML document for a specific location
     * @param woeid
     * @return
     */
    private Document getDocument(String woeid) {
        try {
            boolean celcius = true; //Settings.System.getInt(getBaseContext().getContentResolver(),
                    //Settings.System.WEATHER_USE_METRIC, 1) == 1;
            String urlWithDegreeUnit;

            if (celcius) {
                urlWithDegreeUnit = URL_YAHOO_API_WEATHER + "c";
            } else {
                urlWithDegreeUnit = URL_YAHOO_API_WEATHER + "f";
            }

            return new HttpRetriever().getDocumentFromURL(String.format(urlWithDegreeUnit, woeid));
        } catch (IOException e) {
            Log.e(TAG, "Error querying Yahoo weather");
        }

        return null;
    }

    /**
     * Parse the weather XML document
     * @param wDoc
     * @return
     */
    private WeatherInfo parseXml(Document wDoc) {
        try {
            return new WeatherXmlParser(getBaseContext()).parseWeatherResponse(wDoc);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing Yahoo weather XML document");
            e.printStackTrace();
        }
        return null;
    }
}