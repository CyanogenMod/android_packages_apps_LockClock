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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.IBinder;
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
import com.cyanogenmod.lockclock.misc.WidgetUtils.EventInfo;

import static com.cyanogenmod.lockclock.misc.Constants.PREF_NAME;
import static com.cyanogenmod.lockclock.misc.Constants.MAX_CALENDAR_ITEMS;
import com.cyanogenmod.lockclock.weather.HttpRetriever;
import com.cyanogenmod.lockclock.weather.WeatherInfo;
import com.cyanogenmod.lockclock.weather.WeatherXmlParser;
import com.cyanogenmod.lockclock.weather.YahooPlaceFinder;

import org.w3c.dom.Document;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

public class ClockWidgetService extends Service {
    private static final String TAG = "ClockWidgetService";
    private static final boolean DEBUG = false;

    private Context mContext;
    private int[] mWidgetIds;
    private AppWidgetManager mAppWidgetManager;
    private SharedPreferences mSharedPrefs;
    private boolean mRefreshWeather;
    private List<EventInfo> mEventInfos;

    @Override
    public void onCreate() {
        mContext = getApplicationContext();
        mAppWidgetManager = AppWidgetManager.getInstance(mContext);
        ComponentName thisWidget = new ComponentName(mContext, ClockWidgetProvider.class);
        mWidgetIds = mAppWidgetManager.getAppWidgetIds(thisWidget);
        mSharedPrefs = mContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        mRefreshWeather = false;
        mEventInfos = new ArrayList<EventInfo>(Constants.MAX_CALENDAR_ITEMS);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // See if we are forcing a weather refresh
        if (intent != null && intent.getBooleanExtra(Constants.REFRESH_WEATHER, false)) {
            if (DEBUG) Log.d(TAG, "Forcing a weather refresh");
            mRefreshWeather = true;
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
        // Get things ready
        RemoteViews remoteViews = new RemoteViews(mContext.getPackageName(), R.layout.appwidget);
        boolean digitalClock = mSharedPrefs.getBoolean(Constants.CLOCK_DIGITAL, true);
        boolean showWeather = mSharedPrefs.getBoolean(Constants.SHOW_WEATHER, false);

        // Refresh the non-weather widget panels
        refreshClock(remoteViews, digitalClock);
        refreshAlarmStatus(remoteViews);
        boolean hasCalEvents = refreshCalendar(remoteViews);
        setNoWeatherData(false); // placeholder weather
        update(remoteViews, digitalClock, showWeather, hasCalEvents);

        // If we need to show the weather, do so
        if (showWeather) {
            // Load the required settings from preferences
            final long interval = Long.parseLong(mSharedPrefs.getString(Constants.WEATHER_REFRESH_INTERVAL, "60"));
            boolean manualSync = (interval == 0);
            if (mRefreshWeather || (!manualSync && (((System.currentTimeMillis() - mWeatherInfo.last_sync) / 60000) >= interval))) {
                if (mWeatherQueryTask == null || mWeatherQueryTask.getStatus() == AsyncTask.Status.FINISHED) {
                    mWeatherQueryTask = new WeatherQueryTask();
                    mWeatherQueryTask.execute();
                    mRefreshWeather = false;
                }
            } else if (manualSync && mWeatherInfo.last_sync == 0) {
                setNoWeatherData(true);
            } else {
                setWeatherData(mWeatherInfo);
            }
        } else {
            stopSelf();
        }
    }

    /**
     * Update Clock, Alarm and Calendar widget views without exiting
     */
    private void update(RemoteViews remoteViews, boolean digitalClock, boolean showWeather, boolean showCalendar) {
        // Hide the Loading indicator
        remoteViews.setViewVisibility(R.id.loading_indicator, View.GONE);

        // Update the widgets
        for (int id : mWidgetIds) {
            // Resize the clock font if needed
            if (digitalClock) {
                float ratio = WidgetUtils.getScaleRatio(mContext, id);
                setClockSize(remoteViews, ratio);
            }

            if (showWeather) {
                boolean canFitWeather = WidgetUtils.canFitWeather(mContext, id, digitalClock);
                remoteViews.setViewVisibility(R.id.weather_panel, canFitWeather ? View.VISIBLE : View.GONE);
            }

            // Hide the calendar panel if there is no space for it
            if (showCalendar) {
                boolean canFitCalendar = WidgetUtils.canFitCalendar(mContext, id, digitalClock);
                remoteViews.setViewVisibility(R.id.calendar_panel, canFitCalendar ? View.VISIBLE : View.GONE);
            }

            // Do the update
            mAppWidgetManager.updateAppWidget(id, remoteViews);
        }
    }

    /**
     * This is called from the weather service to refresh the widget and exit
     * when done
     */
    private void updateAndExit(RemoteViews remoteViews) {
        boolean digitalClock = mSharedPrefs.getBoolean(Constants.CLOCK_DIGITAL, true);

        // Update the widgets
        for (int id : mWidgetIds) {
            // Hide the weather panel if there is no space for them
            boolean canFitWeather = WidgetUtils.canFitWeather(mContext, id, digitalClock);
            remoteViews.setViewVisibility(R.id.weather_panel, canFitWeather ? View.VISIBLE : View.GONE);

            // Do the update
            mAppWidgetManager.updateAppWidget(id, remoteViews);
        }
        stopSelf();
    }

    //===============================================================================================
    // Clock related functionality
    //===============================================================================================
    private void refreshClock(RemoteViews clockViews, boolean digitalClock) {
        // Analog or Digital clock
        if (digitalClock) {
            // Hours/Minutes is specific to Didital, set it's size
            refreshClockFont(clockViews);
            clockViews.setViewVisibility(R.id.digital_clock, View.VISIBLE);
            clockViews.setViewVisibility(R.id.analog_clock, View.GONE);
        } else {
            clockViews.setViewVisibility(R.id.analog_clock, View.VISIBLE);
            clockViews.setViewVisibility(R.id.digital_clock, View.GONE);
        }

        // Date/Alarm is to both clocks common, set it's size
        refreshDateAlarmFont(clockViews);

        // Register an onClickListener on Clock, starting DeskClock
        ComponentName clk = new ComponentName("com.android.deskclock", "com.android.deskclock.DeskClock");
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(clk);
        PendingIntent pi = PendingIntent.getActivity(mContext, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
        clockViews.setOnClickPendingIntent(R.id.clock_panel, pi);
    }

    private void refreshClockFont(RemoteViews clockViews) {
        // Hours
        if (mSharedPrefs.getBoolean(Constants.CLOCK_FONT, true)) {
            clockViews.setViewVisibility(R.id.clock1_bold, View.VISIBLE);
            clockViews.setViewVisibility(R.id.clock1_regular, View.GONE);
        } else {
            clockViews.setViewVisibility(R.id.clock1_regular, View.VISIBLE);
            clockViews.setViewVisibility(R.id.clock1_bold, View.GONE);
        }

        // Minutes
        if (mSharedPrefs.getBoolean(Constants.CLOCK_FONT_MINUTES, false)) {
            clockViews.setViewVisibility(R.id.clock2_bold, View.VISIBLE);
            clockViews.setViewVisibility(R.id.clock2_regular, View.GONE);
        } else {
            clockViews.setViewVisibility(R.id.clock2_regular, View.VISIBLE);
            clockViews.setViewVisibility(R.id.clock2_bold, View.GONE);
        }
    }

    private void refreshDateAlarmFont(RemoteViews clockViews) {
        // Date and Alarm font
        if (mSharedPrefs.getBoolean(Constants.CLOCK_FONT_DATE, true)) {
            clockViews.setViewVisibility(R.id.date_bold, View.VISIBLE);
            clockViews.setViewVisibility(R.id.date_regular, View.GONE);
        } else {
            clockViews.setViewVisibility(R.id.date_regular, View.VISIBLE);
            clockViews.setViewVisibility(R.id.date_bold, View.GONE);
        }

        // Show the panel
        clockViews.setViewVisibility(R.id.date_alarm, View.VISIBLE);
    }

    private void setClockSize(RemoteViews clockViews, float scale) {
        float fontSize = mContext.getResources().getDimension(R.dimen.widget_big_font_size);
        clockViews.setTextViewTextSize(R.id.clock1_bold, TypedValue.COMPLEX_UNIT_PX, fontSize * scale);
        clockViews.setTextViewTextSize(R.id.clock1_regular, TypedValue.COMPLEX_UNIT_PX, fontSize * scale);
        clockViews.setTextViewTextSize(R.id.clock2_bold, TypedValue.COMPLEX_UNIT_PX, fontSize * scale);
        clockViews.setTextViewTextSize(R.id.clock2_regular, TypedValue.COMPLEX_UNIT_PX, fontSize * scale);
    }

    //===============================================================================================
    // Alarm related functionality
    //===============================================================================================
    private void refreshAlarmStatus(RemoteViews alarmViews) {
        boolean showAlarm = mSharedPrefs.getBoolean(Constants.CLOCK_SHOW_ALARM, true);
        boolean isBold = mSharedPrefs.getBoolean(Constants.CLOCK_FONT_DATE, true);

        // Update Alarm status
        if (showAlarm) {
            String nextAlarm = getNextAlarm();
            if (!TextUtils.isEmpty(nextAlarm)) {
                // An alarm is set, deal with displaying it
                alarmViews.setTextViewText(isBold ? R.id.nextAlarm_bold : R.id.nextAlarm_regular,
                        nextAlarm.toString().toUpperCase());
                alarmViews.setViewVisibility(R.id.nextAlarm_bold, isBold ? View.VISIBLE : View.GONE);
                alarmViews.setViewVisibility(R.id.nextAlarm_regular, isBold ? View.GONE : View.VISIBLE);
                return;
            }
        }

        // No alarm set or Alarm display is hidden, hide the views
        alarmViews.setViewVisibility(R.id.nextAlarm_bold, View.GONE);
        alarmViews.setViewVisibility(R.id.nextAlarm_regular, View.GONE);
    }

    /**
     * @return A formatted string of the next alarm or null if there is no next alarm.
     */
    private String getNextAlarm() {
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
    private WeatherQueryTask mWeatherQueryTask;

    private class WeatherQueryTask extends AsyncTask<Void, Void, WeatherInfo> {
        @Override
        protected WeatherInfo doInBackground(Void... params) {
            // Load the preferences
            boolean useCustomLoc = mSharedPrefs.getBoolean(Constants.WEATHER_USE_CUSTOM_LOCATION, false);
            String customLoc = mSharedPrefs.getString(Constants.WEATHER_CUSTOM_LOCATION_STRING, null);

            // Get location related stuff ready
            LocationManager locationManager =
                    (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
            String woeid = null;

            if (customLoc != null && useCustomLoc) {
                // custom location
                try {
                    woeid = YahooPlaceFinder.GeoCode(mContext, customLoc);
                    if (DEBUG)
                        Log.d(TAG, "Yahoo location code for " + customLoc + " is " + woeid);
                } catch (Exception e) {
                    Log.e(TAG, "ERROR: Could not get Location code", e);
                }
            } else {
                // network location
                Location loc = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);

                if (loc != null) {
                    try {
                        woeid = YahooPlaceFinder.reverseGeoCode(mContext,
                                loc.getLatitude(), loc.getLongitude());
                        if (DEBUG)
                            Log.d(TAG, "Yahoo location code for current geolocation is " + woeid);
                    } catch (Exception e) {
                        Log.e(TAG, "ERROR: Could not get Location code", e);
                    }
                } else {
                    Log.e(TAG, "ERROR: Location returned null");
                }
                if (DEBUG) {
                    Log.d(TAG, "Location code is " + woeid);
                }
            }

            if (woeid != null) {
                try {
                    return parseXml(getDocument(woeid));
                } catch (Exception e) {
                    Log.e(TAG, "ERROR: Could not parse weather return info", e);
                }
            }

            return null;
        }

        @Override
        protected void onPostExecute(WeatherInfo info) {
            if (info != null) {
                setWeatherData(info);
                mWeatherInfo = info;
            } else if (mWeatherInfo.temp.equals(WeatherInfo.NODATA)) {
                setNoWeatherData(true);
            } else {
                setWeatherData(mWeatherInfo);
            }
        }
    }

    /**
     * Display the weather information
     * @param w
     */
    private void setWeatherData(WeatherInfo w) {
        // Load the preferences
        boolean showLocation = mSharedPrefs.getBoolean(Constants.WEATHER_SHOW_LOCATION, true);
        boolean showTimestamp = mSharedPrefs.getBoolean(Constants.WEATHER_SHOW_TIMESTAMP, true);
        boolean invertLowhigh = mSharedPrefs.getBoolean(Constants.WEATHER_INVERT_LOWHIGH, false);
        boolean defaultIcons = !mSharedPrefs.getBoolean(Constants.WEATHER_USE_ALTERNATE_ICONS, false);

        // Get the views ready
        RemoteViews weatherViews = new RemoteViews(mContext.getPackageName(), R.layout.appwidget);

        // Weather Image - Either the default or alternate set
        String prefix = defaultIcons ? "weather_" : "weather2_";
        String conditionCode = w.condition_code;
        String condition_filename = prefix + conditionCode;

        // Get the resource id based on the constructed name
        final Resources res = getBaseContext().getResources();
        int resID = res.getIdentifier(condition_filename, "drawable",
                getBaseContext().getPackageName());

        if (DEBUG)
            Log.d("Weather", "Condition:" + conditionCode + " ID:" + resID);

        if (resID != 0) {
            weatherViews.setImageViewResource(R.id.weather_image, resID);
        } else {
            weatherViews.setImageViewResource(R.id.weather_image,
                    defaultIcons ? R.drawable.weather_na : R.drawable.weather2_na);
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

        // Register an onClickListener on Weather
        setWeatherClickListener(weatherViews);

        // Update the rest of the widget and stop
        updateAndExit(weatherViews);
    }

    /**
     * There is no data to display, display 'empty' fields and the
     * 'Tap to reload' message
     */
    private void setNoWeatherData(boolean exit) {
        boolean defaultIcons = !mSharedPrefs.getBoolean(Constants.WEATHER_USE_ALTERNATE_ICONS, false);

        final Resources res = getBaseContext().getResources();
        RemoteViews weatherViews = new RemoteViews(mContext.getPackageName(), R.layout.appwidget);

        // Weather Image - Either the default or alternate set
        weatherViews.setImageViewResource(R.id.weather_image,
                defaultIcons ? R.drawable.weather_na : R.drawable.weather2_na);

        // Rest of the data
        weatherViews.setTextViewText(R.id.weather_city, res.getString(R.string.weather_no_data));
        weatherViews.setViewVisibility(R.id.weather_city, View.VISIBLE);
        weatherViews.setViewVisibility(R.id.update_time, View.GONE);
        weatherViews.setViewVisibility(R.id.weather_temps_panel, View.GONE);

        if (exit) {
            // final state
            weatherViews.setTextViewText(R.id.weather_condition, res.getString(R.string.weather_tap_to_refresh));

            // Register an onClickListener on Weather
            setWeatherClickListener(weatherViews);

            // Update the rest of the widget and stop
            updateAndExit(weatherViews);
        }
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
            Log.e(TAG, "Error parsing Yahoo weather XML document", e);
        }
        return null;
    }

    //===============================================================================================
    // Calendar related functionality
    //===============================================================================================
    private boolean refreshCalendar(RemoteViews calendarViews) {
        // Load the settings
        boolean showCalendar = mSharedPrefs.getBoolean(Constants.SHOW_CALENDAR, false);
        Set<String> calendarList = mSharedPrefs.getStringSet(Constants.CALENDAR_LIST, null);
        boolean remindersOnly = mSharedPrefs.getBoolean(Constants.CALENDAR_REMINDERS_ONLY, false);
        boolean hideAllDay = mSharedPrefs.getBoolean(Constants.CALENDAR_HIDE_ALLDAY, false);
        long lookAhead = Long.parseLong(mSharedPrefs.getString(Constants.CALENDAR_LOOKAHEAD, "10800000"));
        boolean hasEvents = false;

        if (showCalendar) {
            // Remove all the views to start
            calendarViews.removeAllViews(R.id.calendar_panel);

            // Get the next batch of events and add them to the panel
            getCalendarEvents(lookAhead, calendarList, remindersOnly, hideAllDay);
            for (EventInfo event : mEventInfos) {
                final RemoteViews itemViews = new RemoteViews(mContext.getPackageName(),
                        R.layout.calendar_item);

                // Only set the icon on the first event
                if (!hasEvents) {
                    itemViews.setImageViewResource(R.id.calendar_icon, R.drawable.ic_lock_idle_calendar);
                }

                // Add the event text fields
                itemViews.setTextViewText(R.id.calendar_event_title, event.title);
                itemViews.setTextViewText(R.id.calendar_event_details, event.description);

                // Add the view to the panel
                calendarViews.addView(R.id.calendar_panel, itemViews);
                hasEvents = true;
            }
        }

        // Register an onClickListener on Calendar if it contains any events, starting the Calendar app
        // and schedule an alarm to deal with the event end
        if (hasEvents) {
            ComponentName cal = new ComponentName("com.android.calendar", "com.android.calendar.AllInOneActivity");
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(cal);
            PendingIntent pi = PendingIntent.getActivity(mContext, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
            calendarViews.setOnClickPendingIntent(R.id.calendar_panel, pi);

            // Schedule an alarm to trigger an update after the event ends/next event starts
            i = new Intent(mContext, ClockWidgetService.class);
            pi = PendingIntent.getService(mContext, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);

            // Clear any old alarms and schedule the new alarm that only triggers if the device is ON (RTC)
            // TODO: for now force the repeating to be at least every hour but this will need to be changed
            //       once the weather updating becomes a broadcast receiver and the repeating alarm is no
            //       longer needed
            AlarmManager am = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
            am.cancel(pi);
            am.setRepeating(AlarmManager.RTC, calculateUpdateTime(), 60000 * 60, pi);

        }


        return hasEvents;
    }

    /**
     * @return A formatted string of the next calendar event with a reminder
     * (for showing on the lock screen), or null if there is no next event
     * within a certain look-ahead time.
     */
    private void getCalendarEvents(long lookahead, Set<String> calendars,
            boolean remindersOnly, boolean hideAllDay) {
        long now = System.currentTimeMillis();
        long later = now + lookahead;

        // Build the 'where' clause
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
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.CALENDAR_ID
        };

        // The indices for the projection array
        int EVENT_ID_INDEX = 0;
        int TITLE_INDEX = 1;
        int BEGIN_TIME_INDEX = 2;
        int END_TIME_INDEX = 3;
        int DESCRIPTION_INDEX = 4;
        int LOCATION_INDEX = 5;
        int ALL_DAY_INDEX = 6;
        int CALENDAR_ID_INDEX = 7;

        Uri uri = Uri.withAppendedPath(CalendarContract.Instances.CONTENT_URI,
                String.format("%d/%d", now, later));
        Cursor cursor = null;
        mEventInfos.clear();

        try {
            cursor = mContext.getContentResolver().query(uri, projection,
                    where.toString(), null, "begin ASC");

            if (cursor != null) {
                cursor.moveToFirst();
                // Iterate through returned rows to a maximum number of calendar events
                for (int i = 0, eventCount = 0; i < cursor.getCount() && eventCount < MAX_CALENDAR_ITEMS; i++) {
                    long eventId = cursor.getLong(EVENT_ID_INDEX);
                    String title = cursor.getString(TITLE_INDEX);
                    long begin = cursor.getLong(BEGIN_TIME_INDEX);
                    long end = cursor.getLong(END_TIME_INDEX);
                    String description = cursor.getString(DESCRIPTION_INDEX);
                    String location = cursor.getString(LOCATION_INDEX);
                    boolean allDay = cursor.getInt(ALL_DAY_INDEX) != 0;
                    int calendarId = cursor.getInt(CALENDAR_ID_INDEX);
                    if (DEBUG) {
                        Log.d(TAG, "Event: " + title + " from calendar with id: " + calendarId);
                    }

                    // If skipping all day events, continue the loop without incementing eventCount
                    if (allDay && hideAllDay) {
                        cursor.moveToNext();
                        continue;
                    }

                    // Check the next event in the case of all day event. As UTC is used for all day
                    // events, the next event may be the one that actually starts sooner
                    if (allDay && !cursor.isLast()) {
                        cursor.moveToNext();
                        long nextBegin = cursor.getLong(BEGIN_TIME_INDEX);
                        if (nextBegin < begin + TimeZone.getDefault().getOffset(begin)) {
                            eventId = cursor.getLong(EVENT_ID_INDEX);
                            title = cursor.getString(TITLE_INDEX);
                            begin = nextBegin;
                            end = cursor.getLong(END_TIME_INDEX);
                            description = cursor.getString(DESCRIPTION_INDEX);
                            location = cursor.getString(LOCATION_INDEX);
                            allDay = cursor.getInt(ALL_DAY_INDEX) != 0;
                            calendarId = cursor.getInt(CALENDAR_ID_INDEX);
                        }
                        // Go back since we are still iterating
                        cursor.moveToPrevious();
                    }

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
                                int stringEnd = location.indexOf('\n');
                                if(stringEnd == -1) {
                                    sb.append(": " + location);
                                } else {
                                    sb.append(": " + location.substring(0, stringEnd));
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
                                int stringEnd = description.indexOf('\n');
                                if(stringEnd == -1) {
                                    sb.append(description);
                                } else {
                                    sb.append(description.substring(0, stringEnd));
                                }
                                break;
                            case 2:
                                // Show all
                                sb.append(description);
                                break;
                        }
                    }

                    // Add the event details to the eventinfo object and move to next record
                    mEventInfos.add(populateEventInfo(eventId, title, sb.toString(), begin, end, allDay));
                    eventCount++;
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
    }

    private EventInfo populateEventInfo(long eventId, String title, String description,
            long begin, long end,  boolean allDay) {
        EventInfo eventInfo = new EventInfo();

        // Populate
        eventInfo.id = eventId;
        eventInfo.title = title;
        eventInfo.description = description;
        eventInfo.start = begin;
        eventInfo.end = end;
        eventInfo.allDay = allDay;

        return eventInfo;
    }

    //===============================================================================================
    // Update timer related functionality
    //===============================================================================================
    /**
     * Calculates and returns the next time we should push widget updates.
     */
    private long calculateUpdateTime() {
        // Make sure an update happens at the next weather interval
        final long now = System.currentTimeMillis();
        long minUpdateTime = getNextWeatherUpdateTime(now);

        // TODO: Not sure if I need to do some timezone related computations here or not
        // See if an events expires earlier
        for (EventInfo event : mEventInfos) {
            final long start;
            final long end;
            start = event.start;
            end = event.end;

            // We want to update widget when we enter/exit time range of an event.
            if (now < start) {
                minUpdateTime = Math.min(minUpdateTime, start);
            } else if (now < end) {
                minUpdateTime = Math.min(minUpdateTime, end);
            }
        }
        return minUpdateTime;
    }

    private long getNextWeatherUpdateTime(long now) {
        final long interval = Long.parseLong(mSharedPrefs.getString(Constants.WEATHER_REFRESH_INTERVAL, "60"));
        return ((now - mWeatherInfo.last_sync) + (interval * 60000));
    }
}
