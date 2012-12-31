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

import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import com.cyanogenmod.lockclock.misc.Constants;
import com.cyanogenmod.lockclock.misc.WidgetUtils;

import static com.cyanogenmod.lockclock.misc.Constants.PREF_NAME;
import static com.cyanogenmod.lockclock.misc.Constants.MAX_CALENDAR_ITEMS;
import com.cyanogenmod.lockclock.weather.HttpRetriever;
import com.cyanogenmod.lockclock.weather.WeatherInfo;
import com.cyanogenmod.lockclock.weather.WeatherXmlParser;
import com.cyanogenmod.lockclock.weather.YahooPlaceFinder;

import org.w3c.dom.Document;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.TimeZone;

public class ClockWidgetService extends Service {
    private static final String TAG = "ClockWidgetService";
    private static final boolean DEBUG = false;

    private Context mContext;
    private int[] mWidgetIds;
    private AppWidgetManager mAppWidgetManager;
    private SharedPreferences mSharedPrefs;
    private boolean mForceRefresh;
    private boolean mEvent1Visible = false;

    @Override
    public void onCreate() {
        mContext = getApplicationContext();
        mAppWidgetManager = AppWidgetManager.getInstance(mContext);
        ComponentName thisWidget = new ComponentName(mContext, ClockWidgetProvider.class);
        mWidgetIds = mAppWidgetManager.getAppWidgetIds(thisWidget);
        mSharedPrefs = mContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        mForceRefresh = false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // See if we are forcing a full weather refresh
        if (intent != null && intent.getBooleanExtra(Constants.FORCE_REFRESH, false)) {
            if (DEBUG) Log.d(TAG, "Forcing a weather refresh");
            mForceRefresh = true;
        }

        // Refresh the widgets
        if (mWidgetIds != null && mWidgetIds.length != 0) {
            refreshWidget();
        } else {
            stopSelf();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
      return null;
    }

    /**
     * Reload the widget including the Weather forecast, Alarm, Clock font and Calendar
     */
    private void refreshWidget() {
        // If we need to show the weather, do so
        boolean showWeather = mSharedPrefs.getBoolean(Constants.SHOW_WEATHER, false);
        if (showWeather) {
            // Load the required settings from preferences
            final long interval = Long.parseLong(mSharedPrefs.getString(Constants.WEATHER_REFRESH_INTERVAL, "60"));
            boolean manualSync = (interval == 0);
            if (mForceRefresh || (!manualSync && (((System.currentTimeMillis() - mWeatherInfo.last_sync) / 60000) >= interval))) {
                if (!mWeatherRefreshing) {
                    mHandler.sendEmptyMessage(QUERY_WEATHER);
                    mForceRefresh = false;
                }
            } else if (manualSync && mWeatherInfo.last_sync == 0) {
                setNoWeatherData();
            } else {
                setWeatherData(mWeatherInfo);
            }
        } else {
            // Hide the weather panel and update the rest of the widget
            RemoteViews remoteViews = new RemoteViews(mContext.getPackageName(), R.layout.digital_appwidget);
            remoteViews.setViewVisibility(R.id.weather_panel, View.GONE);
            updateAndExit(remoteViews);
        }
    }

    /**
     * Refresh Alarm and Calendar (if visible) and update the widget views 
     */
    private void updateAndExit(RemoteViews remoteViews) {
        refreshClockFont(remoteViews);
        refreshAlarmStatus(remoteViews);
        refreshCalendar(remoteViews);

        boolean showWeather = mSharedPrefs.getBoolean(Constants.SHOW_WEATHER, false);
        boolean showCalendar = mSharedPrefs.getBoolean(Constants.SHOW_CALENDAR, false) && mEvent1Visible;
        for (int id : mWidgetIds) {
            // Resize the clock font if needed
            float ratio = WidgetUtils.getScaleRatio(mContext, id);
            setClockSize(remoteViews, ratio);

            // Hide the panels if there is no space for them
            boolean canFitWeather = WidgetUtils.canFitWeather(mContext, id);
            boolean canFitCalendar = WidgetUtils.canFitCalendar(mContext, id);
            remoteViews.setViewVisibility(R.id.weather_panel, canFitWeather && showWeather ? View.VISIBLE : View.GONE);
            remoteViews.setViewVisibility(R.id.calendar_panel, canFitCalendar && showCalendar ? View.VISIBLE : View.GONE);

            // Do the update
            mAppWidgetManager.updateAppWidget(id, remoteViews);
        }
        stopSelf();
    }

    //===============================================================================================
    // Clock related functionality
    //===============================================================================================
    private void refreshClockFont(RemoteViews clockViews) {
        if (!mSharedPrefs.getBoolean(Constants.CLOCK_FONT, true)) {
            clockViews.setViewVisibility(R.id.the_clock1_regular, View.VISIBLE);
            clockViews.setViewVisibility(R.id.the_clock1, View.GONE);
        } else {
            clockViews.setViewVisibility(R.id.the_clock1_regular, View.GONE);
            clockViews.setViewVisibility(R.id.the_clock1, View.VISIBLE);
        }

        // Register an onClickListener on Clock, starting DeskClock
        ComponentName clk = new ComponentName("com.android.deskclock", "com.android.deskclock.DeskClock");
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(clk);
        PendingIntent pi = PendingIntent.getActivity(mContext, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
        clockViews.setOnClickPendingIntent(R.id.digital_clock, pi);
    }

    private void setClockSize(RemoteViews clockViews, float scale) {
        float fontSize = mContext.getResources().getDimension(R.dimen.widget_big_font_size);
        clockViews.setTextViewTextSize(R.id.the_clock1, TypedValue.COMPLEX_UNIT_PX, fontSize * scale);
        clockViews.setTextViewTextSize(R.id.the_clock1_regular, TypedValue.COMPLEX_UNIT_PX, fontSize * scale);
        clockViews.setTextViewTextSize(R.id.the_clock2, TypedValue.COMPLEX_UNIT_PX, fontSize * scale);
    }

    //===============================================================================================
    // Alarm related functionality
    //===============================================================================================
    private void refreshAlarmStatus(RemoteViews alarmViews) {
        boolean showAlarm = mSharedPrefs.getBoolean(Constants.CLOCK_SHOW_ALARM, true);

        // Update Alarm status
        if (showAlarm) {
            String nextAlarm = getNextAlarm();
            if (!TextUtils.isEmpty(nextAlarm)) {
                alarmViews.setTextViewText(R.id.nextAlarm, nextAlarm.toString().toUpperCase());
                alarmViews.setViewVisibility(R.id.nextAlarm, View.VISIBLE);
            } else {
                alarmViews.setViewVisibility(R.id.nextAlarm, View.GONE);
            }
        } else {
            alarmViews.setViewVisibility(R.id.nextAlarm, View.GONE);
        }
    }

    /**
     * @return A formatted string of the next alarm or null if there is no next alarm.
     */
    public String getNextAlarm() {
        String nextAlarm = Settings.System.getString(mContext.getContentResolver(),
                Settings.System.NEXT_ALARM_FORMATTED);
        if (nextAlarm == null || TextUtils.isEmpty(nextAlarm)) {
            return null;
        }
        return nextAlarm;
    }

    //===============================================================================================
    // Weather related functionality
    //===============================================================================================
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
                        // Load the preferences
                        boolean useCustomLoc = mSharedPrefs.getBoolean(Constants.WEATHER_USE_CUSTOM_LOCATION, false);
                        String customLoc = mSharedPrefs.getString(Constants.WEATHER_CUSTOM_LOCATION_STRING, null);

                        // Get location related stuff ready
                        LocationManager locationManager =
                                (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
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
     * Display the weather information
     * @param w
     */
    private void setWeatherData(WeatherInfo w) {
        // Load the preferences
        boolean showLocation = mSharedPrefs.getBoolean(Constants.WEATHER_SHOW_LOCATION, true);
        boolean showTimestamp = mSharedPrefs.getBoolean(Constants.WEATHER_SHOW_TIMESTAMP, true);
        boolean invertLowhigh = mSharedPrefs.getBoolean(Constants.WEATHER_INVERT_LOWHIGH, false);

        // Get the views ready
        RemoteViews weatherViews = new RemoteViews(mContext.getPackageName(), R.layout.digital_appwidget);

        // Weather Image
        final Resources res = getBaseContext().getResources();
        String conditionCode = w.condition_code;
        String condition_filename = "weather_" + conditionCode;
        int resID = res.getIdentifier(condition_filename, "drawable",
                getBaseContext().getPackageName());

        if (DEBUG)
            Log.d("Weather", "Condition:" + conditionCode + " ID:" + resID);

        if (resID != 0) {
            weatherViews.setImageViewResource(R.id.weather_image, resID);
        } else {
            weatherViews.setImageViewResource(R.id.weather_image, R.drawable.weather_na);
        }

        // City
        weatherViews.setTextViewText(R.id.weather_city, w.city);
        weatherViews.setViewVisibility(R.id.weather_city, showLocation ? View.VISIBLE : View.GONE);

        // Weather Condition
        weatherViews.setTextViewText(R.id.weather_condition, w.condition);
        weatherViews.setViewVisibility(R.id.weather_condition, View.VISIBLE);

        // Weather Update Time
        long now = System.currentTimeMillis();
        if (now - w.last_sync < 60000) {
            weatherViews.setTextViewText(R.id.update_time, res.getString(R.string.weather_last_sync_just_now));
        } else {
            weatherViews.setTextViewText(R.id.update_time, DateUtils.getRelativeTimeSpanString(
                    w.last_sync, now, DateUtils.MINUTE_IN_MILLIS));
        }
        weatherViews.setViewVisibility(R.id.update_time, showTimestamp ? View.VISIBLE : View.GONE);

        // Weather Temps Panel
        weatherViews.setTextViewText(R.id.weather_temp, w.temp);
        weatherViews.setTextViewText(R.id.weather_low_high, invertLowhigh ? w.high + " | " + w.low : w.low + " | " + w.high);
        weatherViews.setViewVisibility(R.id.weather_temps_panel, View.VISIBLE);

        // Make sure the Weather panel is visible
        weatherViews.setViewVisibility(R.id.weather_panel, View.VISIBLE);

        // Register an onClickListener on Weather
        setWeatherClickListener(weatherViews);

        // Update the rest of the widget and stop
        updateAndExit(weatherViews);
    }

    /**
     * There is no data to display, display 'empty' fields and the
     * 'Tap to reload' message
     */
    private void setNoWeatherData() {
        final Resources res = getBaseContext().getResources();
        RemoteViews weatherViews = new RemoteViews(mContext.getPackageName(), R.layout.digital_appwidget);

        // Update the appropriate views
        weatherViews.setImageViewResource(R.id.weather_image, R.drawable.weather_na);
        weatherViews.setTextViewText(R.id.weather_city, res.getString(R.string.weather_no_data));
        weatherViews.setViewVisibility(R.id.weather_city, View.VISIBLE);
        weatherViews.setTextViewText(R.id.weather_condition, res.getString(R.string.weather_tap_to_refresh));
        weatherViews.setViewVisibility(R.id.update_time, View.GONE);
        weatherViews.setViewVisibility(R.id.weather_temps_panel, View.GONE);

        // Make sure the Weather panel is visible
        weatherViews.setViewVisibility(R.id.weather_panel, View.VISIBLE);

        // Register an onClickListener on Weather
        setWeatherClickListener(weatherViews);

        // Update the rest of the widget and stop
        updateAndExit(weatherViews);
    }

    private void setWeatherClickListener(RemoteViews weatherViews) {
        Intent weatherClickIntent = new Intent(mContext, ClockWidgetProvider.class);
        weatherClickIntent.putExtra(Constants.FORCE_REFRESH, true);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0, weatherClickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        weatherViews.setOnClickPendingIntent(R.id.weather_panel, pendingIntent);
    }

    /**
     * Get the weather forecast XML document for a specific location
     * @param woeid
     * @return
     */
    private Document getDocument(String woeid) {
        try {
            boolean celcius = mSharedPrefs.getBoolean(Constants.WEATHER_USE_METRIC, true);
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
    

    //===============================================================================================
    // Calendar related functionality
    //===============================================================================================
    private void refreshCalendar(RemoteViews calendarViews) {
        // Load the settings
        boolean lockCalendar = mSharedPrefs.getBoolean(Constants.SHOW_CALENDAR, false);
        Set<String> calendars = mSharedPrefs.getStringSet(Constants.CALENDAR_LIST, null);
        boolean lockCalendarRemindersOnly = mSharedPrefs.getBoolean(Constants.CALENDAR_REMINDERS_ONLY, false);
        long lockCalendarLookahead = Long.parseLong(mSharedPrefs.getString(Constants.CALENDAR_LOOKAHEAD, "10800000"));

        // Assume we are not showing the views
        mEvent1Visible = false;
        boolean event2Visible = false;
        boolean event3Visible = false;

        if (lockCalendar) {
            String[][] nextCalendar = null;
            nextCalendar = getNextCalendarAlarm(lockCalendarLookahead, calendars, lockCalendarRemindersOnly);
            // Iterate through the calendars, up to the maximum
            for (int i = 0; i < MAX_CALENDAR_ITEMS; i++) {
                if (nextCalendar[i][0] != null) {
                    // TODO: change this to dynamically add views to the widget
                    // Hard code this to 3 for now
                    if (i == 0) {
                        calendarViews.setTextViewText(R.id.calendar_event_title, nextCalendar[i][0].toString());
                        if (nextCalendar[0][1] != null) {
                            calendarViews.setTextViewText(R.id.calendar_event_details, nextCalendar[i][1]);
                        }
                        mEvent1Visible = true;
                    } else if (i == 1) {
                        calendarViews.setTextViewText(R.id.calendar_event2_title, nextCalendar[i][0].toString());
                        if (nextCalendar[0][1] != null) {
                            calendarViews.setTextViewText(R.id.calendar_event2_details, nextCalendar[i][1]);
                        }
                        event2Visible = true;
                    } else if (i == 2) {
                        calendarViews.setTextViewText(R.id.calendar_event3_title, nextCalendar[i][0].toString());
                        if (nextCalendar[0][1] != null) {
                            calendarViews.setTextViewText(R.id.calendar_event3_details, nextCalendar[i][1]);
                        }
                        event3Visible = true;
                    }
                }
            }
            // Deal with the visibility of the event items
            calendarViews.setViewVisibility(R.id.calendar_event2, event2Visible ? View.VISIBLE : View.GONE);
            calendarViews.setViewVisibility(R.id.calendar_event3, event3Visible ? View.VISIBLE : View.GONE);
        }

        // Deal with overall panel visibility
        calendarViews.setViewVisibility(R.id.calendar_panel, mEvent1Visible ? View.VISIBLE : View.GONE);
        if (mEvent1Visible) {
            // Register an onClickListener on Calendar, starting the Calendar app
            ComponentName cal = new ComponentName("com.android.calendar", "com.android.calendar.AllInOneActivity");
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(cal);
            PendingIntent pi = PendingIntent.getActivity(mContext, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
            calendarViews.setOnClickPendingIntent(R.id.calendar_panel, pi);
        }
    }

    /**
     * @return A formatted string of the next calendar event with a reminder
     * (for showing on the lock screen), or null if there is no next event
     * within a certain look-ahead time.
     */
    private String[][] getNextCalendarAlarm(long lookahead, Set<String> calendars,
            boolean remindersOnly) {
        long now = System.currentTimeMillis();
        long later = now + lookahead;

        StringBuilder where = new StringBuilder();
        if (remindersOnly) {
            where.append(CalendarContract.Events.HAS_ALARM + "=1");
        }
        if (calendars != null && calendars.size() > 0) {
            if (remindersOnly) {
                where.append(" AND ");
            }
            where.append(CalendarContract.Events.CALENDAR_ID + " in (");
            int i = 0;
            for (String s : calendars) {
                where.append(s);
                if (i != calendars.size() - 1) {
                    where.append(",");
                }
                i++;
            }
            where.append(") ");
        }

        // Projection array
        String[] projection = new String[] {
            CalendarContract.Events.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.ALL_DAY
        };

        // The indices for the projection array
        int TITLE_INDEX = 0;
        int BEGIN_TIME_INDEX = 1;
        int DESCRIPTION_INDEX = 2;
        int LOCATION_INDEX = 3;
        int ALL_DAY_INDEX = 4;

        Uri uri = Uri.withAppendedPath(CalendarContract.Instances.CONTENT_URI,
                String.format("%d/%d", now, later));
        String[][] nextCalendarAlarm = new String[MAX_CALENDAR_ITEMS][2];
        Cursor cursor = null;

        try {
            cursor = mContext.getContentResolver().query(uri, projection,
                    where.toString(), null, "begin ASC");

            if (cursor != null) {
                cursor.moveToFirst();

                // Iterate through rows to a maximum number of calendar entries
                for (int i = 0; i < cursor.getCount() && i < MAX_CALENDAR_ITEMS; i++) {
                    String title = cursor.getString(TITLE_INDEX);
                    long begin = cursor.getLong(BEGIN_TIME_INDEX);
                    String description = cursor.getString(DESCRIPTION_INDEX);
                    String location = cursor.getString(LOCATION_INDEX);
                    boolean allDay = cursor.getInt(ALL_DAY_INDEX) != 0;

                    // Check the next event in the case of all day event. As UTC is used for all day
                    // events, the next event may be the one that actually starts sooner
                    if (allDay && !cursor.isLast()) {
                        cursor.moveToNext();
                        long nextBegin = cursor.getLong(BEGIN_TIME_INDEX);
                        if (nextBegin < begin + TimeZone.getDefault().getOffset(begin)) {
                            title = cursor.getString(TITLE_INDEX);
                            begin = nextBegin;
                            description = cursor.getString(DESCRIPTION_INDEX);
                            location = cursor.getString(LOCATION_INDEX);
                            allDay = cursor.getInt(ALL_DAY_INDEX) != 0;
                        }
                        // Go back since we are still iterating
                        cursor.moveToPrevious();
                    }

                    // Set the event title as the first array item
                    nextCalendarAlarm[i][0] = title.toString();

                    // Start building the event details string
                    // Starting with the date
                    Date start = new Date(begin);
                    StringBuilder sb = new StringBuilder();

                    if (allDay) {
                        SimpleDateFormat sdf = new SimpleDateFormat(
                                mContext.getString(R.string.abbrev_wday_month_day_no_year));
                        // Calendar stores all-day events in UTC -- setting the time zone ensures
                        // the correct date is shown.
                        sdf.setTimeZone(TimeZone.getTimeZone(Time.TIMEZONE_UTC));
                        sb.append(sdf.format(start));
                    } else {
                        sb.append(DateFormat.format("E", start));
                        sb.append(" ");
                        sb.append(DateFormat.getTimeFormat(mContext).format(start));
                    }

                    // Add the event location if it should be shown
                    int showLocation = Integer.parseInt(mSharedPrefs.getString(Constants.CALENDAR_SHOW_LOCATION, "0"));
                    if (showLocation != 0 && !TextUtils.isEmpty(location)) {
                        switch(showLocation) {
                            case 1:
                                // Show first line
                                int end = location.indexOf('\n');
                                if(end == -1) {
                                    sb.append(": " + location);
                                } else {
                                    sb.append(": " + location.substring(0, end));
                                }
                                break;
                            case 2:
                                // Show all
                                sb.append(": " + location);
                                break;
                        }
                    }

                    // Add the event description if it should be shown
                    int showDescription = Integer.parseInt(mSharedPrefs.getString(Constants.CALENDAR_SHOW_DESCRIPTION, "0"));
                    if (showDescription != 0 && !TextUtils.isEmpty(description)) {

                        // Show the appropriate separator
                        if (showLocation == 0) {
                            sb.append(": ");
                        } else {
                            sb.append(" - ");
                        }

                        switch(showDescription) {
                            case 1:
                                // Show first line
                                int end = description.indexOf('\n');
                                if(end == -1) {
                                    sb.append(description);
                                } else {
                                    sb.append(description.substring(0, end));
                                }
                                break;
                            case 2:
                                // Show all
                                sb.append(description);
                                break;
                        }
                    }

                    // Set the time, location and description as the second array item
                    nextCalendarAlarm[i][1] = sb.toString();
                    cursor.moveToNext();
                }
            }
        } catch (Exception e) {
            // Do nothing
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return nextCalendarAlarm;
    }
}
