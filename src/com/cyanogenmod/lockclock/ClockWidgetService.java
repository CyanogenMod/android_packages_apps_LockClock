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
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.database.Cursor;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
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

    private static final String ACTION_LOC_UPDATE = "com.cyanogenmod.lockclock.action.LOCATION_UPDATE";

    private static final long MIN_LOC_UPDATE_INTERVAL = 15 * 60 * 1000; /* 15 minutes */
    private static final float MIN_LOC_UPDATE_DISTANCE = 5000f; /* 5 km */

    private AppWidgetManager mAppWidgetManager;
    private ConnectivityManager mConnManager;
    private LocationManager mLocManager;

    private int[] mWidgetIds;
    private SharedPreferences mSharedPrefs;
    private boolean mForceRefresh;
    private PendingIntent mLocUpdateIntent;
    private boolean mNeedsRefresh;
    private boolean mReceiversRegistered;

    @Override
    public void onCreate() {
        mAppWidgetManager = AppWidgetManager.getInstance(this);
        mLocManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        mConnManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        ComponentName thisWidget = new ComponentName(this, ClockWidgetProvider.class);
        mWidgetIds = mAppWidgetManager.getAppWidgetIds(thisWidget);

        mSharedPrefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        mSharedPrefs.registerOnSharedPreferenceChangeListener(mLocationSettingListener);

        mLocUpdateIntent = PendingIntent.getService(this, 0, new Intent(ACTION_LOC_UPDATE), 0);
        mForceRefresh = false;
        mNeedsRefresh = false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // See if we are forcing a full weather refresh
        if (intent != null && intent.getBooleanExtra(Constants.FORCE_REFRESH, false)) {
            if (DEBUG) Log.d(TAG, "Forcing a weather refresh");
            mForceRefresh = true;
        }

        if (intent != null && ACTION_LOC_UPDATE.equals(intent.getAction())) {
            Location location = (Location) intent.getParcelableExtra(LocationManager.KEY_LOCATION_CHANGED);
            triggerLocationQueryWithLocation(location);
        } else if (mWidgetIds != null && mWidgetIds.length != 0) {
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

    @Override
    public void onDestroy() {
        mSharedPrefs.unregisterOnSharedPreferenceChangeListener(mLocationSettingListener);
        if (mReceiversRegistered) {
            mLocManager.removeUpdates(mLocUpdateIntent);
            unregisterReceiver(mConnectionChangeReceiver);
        }
        super.onDestroy();
    }

    /**
     * Reload the widget including the Weather forecast, Alarm, Clock font and Calendar
     */
    private void refreshWidget() {
        // If we need to show the weather, do so
        boolean showWeather = mSharedPrefs.getBoolean(Constants.SHOW_WEATHER, false);

        if (showWeather) {
            if (!mReceiversRegistered) {
                registerReceiver(mConnectionChangeReceiver,
                        new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
                updateLocationListenerState();
                mReceiversRegistered = true;
            }

            // Load the required settings from preferences
            final long interval = Long.parseLong(mSharedPrefs.getString(Constants.WEATHER_REFRESH_INTERVAL, "60"));
            boolean manualSync = (interval == 0);
            if (mForceRefresh || (!manualSync && (((System.currentTimeMillis() - mWeatherInfo.last_sync) / 60000) >= interval))) {
                if (triggerWeatherQuery(false)) {
                    mForceRefresh = false;
                }
            } else if (manualSync && mWeatherInfo.last_sync == 0) {
                setNoWeatherData();
            } else {
                setWeatherData(mWeatherInfo);
            }
        } else {
            updateAndExit();
        }
    }

    private boolean shouldRemainLoaded() {
        // We need to be kept loaded if weather is shown with a non-custom location
        // as we rely on the location listener in that case
        return mReceiversRegistered && mLocationInfo.customLocation == null;
    }

    private void updateAndExit() {
        RemoteViews remoteViews = new RemoteViews(getPackageName(), R.layout.appwidget);
        updateAndExit(remoteViews);
    }

    /**
     * Refresh Alarm and Calendar (if visible) and update the widget views 
     */
    private void updateAndExit(RemoteViews remoteViews) {
        // Refresh the remaining widget panels.
        //NOTE: Weather is updated prior to this method being called
        refreshClock(remoteViews);
        refreshAlarmStatus(remoteViews);
        boolean hasCalEvents = refreshCalendar(remoteViews);

        // Hide the Loading indicator
        remoteViews.setViewVisibility(R.id.loading_indicator, View.GONE);

        // Update the widgets
        boolean showWeather = mSharedPrefs.getBoolean(Constants.SHOW_WEATHER, false);
        boolean showCalendar = hasCalEvents && mSharedPrefs.getBoolean(Constants.SHOW_CALENDAR, false);
        boolean digitalClock = mSharedPrefs.getBoolean(Constants.CLOCK_DIGITAL, true);
        for (int id : mWidgetIds) {
            // Resize the clock font if needed
            if (digitalClock) {
                float ratio = WidgetUtils.getScaleRatio(this, id);
                setClockSize(remoteViews, ratio);
            }

            // Hide the panels if there is no space for them
            boolean canFitWeather = WidgetUtils.canFitWeather(this, id, digitalClock);
            boolean canFitCalendar = WidgetUtils.canFitCalendar(this, id, digitalClock);
            remoteViews.setViewVisibility(R.id.weather_panel, canFitWeather && showWeather ? View.VISIBLE : View.GONE);
            remoteViews.setViewVisibility(R.id.calendar_panel, canFitCalendar && showCalendar ? View.VISIBLE : View.GONE);

            // Do the update
            mAppWidgetManager.updateAppWidget(id, remoteViews);
        }
        if (!shouldRemainLoaded()) {
            stopSelf();
        }
    }

    //===============================================================================================
    // Clock related functionality
    //===============================================================================================
    private void refreshClock(RemoteViews clockViews) {
        // Analog or Digital clock
        if (mSharedPrefs.getBoolean(Constants.CLOCK_DIGITAL, true)) {
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
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
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
        float fontSize = getResources().getDimension(R.dimen.widget_big_font_size);
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
        String nextAlarm = Settings.System.getString(
                getContentResolver(), Settings.System.NEXT_ALARM_FORMATTED);
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
    private LocationQueryTask mLocationQueryTask;
    private LocationInfo mLocationInfo = new LocationInfo();
    private String mLastKnownWoeid;
    private boolean mNeedsWeatherRefresh;

    private BroadcastReceiver mConnectionChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mNeedsRefresh && !intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false)) {
                mNeedsRefresh = false;
                triggerLocationQueryWithLocation(null);
            }
        }
    };

    private OnSharedPreferenceChangeListener mLocationSettingListener = new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            if (Constants.SHOW_WEATHER.equals(key)
                    || Constants.WEATHER_USE_CUSTOM_LOCATION.equals(key)
                    || Constants.WEATHER_CUSTOM_LOCATION_STRING.equals(key)) {
                refreshWidget();
            }
        }
    };

    private void updateLocationListenerState() {
        boolean useCustomLoc = mSharedPrefs.getBoolean(Constants.WEATHER_USE_CUSTOM_LOCATION, false);
        String customLoc = mSharedPrefs.getString(Constants.WEATHER_CUSTOM_LOCATION_STRING, null);

        if (useCustomLoc && customLoc != null) {
            mLocManager.removeUpdates(mLocUpdateIntent);
            mLocationInfo.customLocation = customLoc;
            triggerLocationQueryWithLocation(null);
        } else {
            mLocManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER,
                    MIN_LOC_UPDATE_INTERVAL, MIN_LOC_UPDATE_DISTANCE, mLocUpdateIntent);
            mLocationInfo.customLocation = null;
            triggerLocationQueryWithLocation(
                    mLocManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER));
        }
    }

    private void triggerLocationQueryWithLocation(Location location) {
        if (DEBUG)
            Log.d(TAG, "Triggering location query with location " + location);

        if (location != null) {
            mLocationInfo.location = location;
        }
        if (mLocationQueryTask != null) {
            mLocationQueryTask.cancel(true);
        }
        mLocationQueryTask = new LocationQueryTask();
        mLocationQueryTask.execute(mLocationInfo);
    }

    private boolean triggerWeatherQuery(boolean force) {
        if (!force) {
            if (mLocationQueryTask != null && mLocationQueryTask.getStatus() != AsyncTask.Status.FINISHED) {
                /* the location query task will trigger the weather query */
                return true;
            }
        }
        if (mWeatherQueryTask != null) {
            if (force) {
                mWeatherQueryTask.cancel(true);
            } else if (mWeatherQueryTask.getStatus() != AsyncTask.Status.FINISHED) {
                return false;
            }
        }
        mWeatherQueryTask = new WeatherQueryTask();
        mWeatherQueryTask.execute(mLastKnownWoeid);
        return true;
    }

    private static class LocationInfo {
        Location location;
        String customLocation;
    }

    private class LocationQueryTask extends AsyncTask<LocationInfo, Void, String> {
        @Override
        protected String doInBackground(LocationInfo... params) {
            LocationInfo info = params[0];

            try {
                if (info.customLocation != null) {
                    String woeid = YahooPlaceFinder.GeoCode(
                            ClockWidgetService.this, info.customLocation);
                    if (DEBUG)
                        Log.d(TAG, "Yahoo location code for " + info.customLocation + " is " + woeid);
                    return woeid;
                } else if (info.location != null) {
                    String woeid = YahooPlaceFinder.reverseGeoCode(ClockWidgetService.this,
                            info.location.getLatitude(), info.location.getLongitude());
                    if (DEBUG)
                        Log.d(TAG, "Yahoo location code for geolocation " + info.location + " is " + woeid);
                    return woeid;
                }
            } catch (Exception e) {
                Log.e(TAG, "ERROR: Could not get Location code", e);
                mNeedsWeatherRefresh = true;
            }

            return null;
        }

        @Override
        protected void onPostExecute(String woeid) {
            mLastKnownWoeid = woeid;
            triggerWeatherQuery(true);
        }
    }

    private class WeatherQueryTask extends AsyncTask<String, Void, WeatherInfo> {
        private Document getDocument(String woeid) throws IOException {
            final boolean celsius = mSharedPrefs.getBoolean(Constants.WEATHER_USE_METRIC, true);
            final String urlWithUnit = URL_YAHOO_API_WEATHER + (celsius ? "c" : "f");
            return new HttpRetriever().getDocumentFromURL(String.format(urlWithUnit, woeid));
        }

        private WeatherInfo parseXml(Document doc) {
            WeatherXmlParser parser = new WeatherXmlParser(ClockWidgetService.this);
            return parser.parseWeatherResponse(doc);
        }

        @Override
        protected WeatherInfo doInBackground(String... params) {
            String woeid = params[0];

            if (DEBUG)
                Log.d(TAG, "Querying weather for woeid " + woeid);

            if (woeid != null) {
                try {
                    return parseXml(getDocument(woeid));
                } catch (Exception e) {
                    Log.e(TAG, "ERROR: Could not parse weather return info", e);
                    mNeedsWeatherRefresh = true;
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
                setNoWeatherData();
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
        RemoteViews weatherViews = new RemoteViews(getPackageName(), R.layout.appwidget);

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
    private void setNoWeatherData() {
        boolean defaultIcons = !mSharedPrefs.getBoolean(Constants.WEATHER_USE_ALTERNATE_ICONS, false);

        final Resources res = getBaseContext().getResources();
        RemoteViews weatherViews = new RemoteViews(getPackageName(), R.layout.appwidget);

        // Weather Image - Either the default or alternate set
        weatherViews.setImageViewResource(R.id.weather_image,
                defaultIcons ? R.drawable.weather_na : R.drawable.weather2_na);

        // Rest of the data
        weatherViews.setTextViewText(R.id.weather_city, res.getString(R.string.weather_no_data));
        weatherViews.setViewVisibility(R.id.weather_city, View.VISIBLE);
        weatherViews.setTextViewText(R.id.weather_condition, res.getString(R.string.weather_tap_to_refresh));
        weatherViews.setViewVisibility(R.id.update_time, View.GONE);
        weatherViews.setViewVisibility(R.id.weather_temps_panel, View.GONE);

        // Register an onClickListener on Weather
        setWeatherClickListener(weatherViews);

        // Update the rest of the widget and stop
        updateAndExit(weatherViews);
    }

    private void setWeatherClickListener(RemoteViews weatherViews) {
        Intent weatherClickIntent = new Intent(this, ClockWidgetProvider.class);
        weatherClickIntent.putExtra(Constants.FORCE_REFRESH, true);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, weatherClickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        weatherViews.setOnClickPendingIntent(R.id.weather_panel, pendingIntent);
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
            String[][] nextCalendar = null;
            nextCalendar = getNextCalendarAlarm(lookAhead, calendarList, remindersOnly, hideAllDay);

            // Remove all the views to start
            calendarViews.removeAllViews(R.id.calendar_panel);

            // Iterate through the calendars, up to the maximum
            for (int i = 0; i < MAX_CALENDAR_ITEMS; i++) {
                if (nextCalendar[i][0] != null) {
                    final RemoteViews itemViews = new RemoteViews(getPackageName(), R.layout.calendar_item);

                    // Only set the icon on the first event
                    if (!hasEvents) {
                        itemViews.setImageViewResource(R.id.calendar_icon, R.drawable.ic_lock_idle_calendar);
                    }

                    // Add the event text fields
                    itemViews.setTextViewText(R.id.calendar_event_title, nextCalendar[i][0]);
                    if (nextCalendar[i][1] != null) {
                        itemViews.setTextViewText(R.id.calendar_event_details, nextCalendar[i][1]);
                    }

                    // Add the view to the panel
                    calendarViews.addView(R.id.calendar_panel, itemViews);
                    hasEvents = true;
                }
            }
        }

        // Register an onClickListener on Calendar if it contains any events, starting the Calendar app
        if (hasEvents) {
            ComponentName cal = new ComponentName("com.android.calendar", "com.android.calendar.AllInOneActivity");
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(cal);
            PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
            calendarViews.setOnClickPendingIntent(R.id.calendar_panel, pi);
        }

        return hasEvents;
    }

    /**
     * @return A formatted string of the next calendar event with a reminder
     * (for showing on the lock screen), or null if there is no next event
     * within a certain look-ahead time.
     */
    private String[][] getNextCalendarAlarm(long lookahead, Set<String> calendars,
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
            CalendarContract.Events.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.CALENDAR_ID
        };

        // The indices for the projection array
        int TITLE_INDEX = 0;
        int BEGIN_TIME_INDEX = 1;
        int DESCRIPTION_INDEX = 2;
        int LOCATION_INDEX = 3;
        int ALL_DAY_INDEX = 4;
        int CALENDAR_ID_INDEX = 5;

        Uri uri = Uri.withAppendedPath(CalendarContract.Instances.CONTENT_URI,
                String.format("%d/%d", now, later));
        String[][] nextCalendarAlarm = new String[MAX_CALENDAR_ITEMS][2];
        Cursor cursor = null;

        try {
            cursor = getContentResolver().query(uri, projection, where.toString(), null, "begin ASC");

            if (cursor != null) {
                cursor.moveToFirst();
                // Iterate through returned rows to a maximum number of calendar events
                for (int i = 0, eventCount = 0; i < cursor.getCount() && eventCount < MAX_CALENDAR_ITEMS; i++) {
                    String title = cursor.getString(TITLE_INDEX);
                    long begin = cursor.getLong(BEGIN_TIME_INDEX);
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
                    nextCalendarAlarm[eventCount][0] = title.toString();

                    // Start building the event details string
                    // Starting with the date
                    Date start = new Date(begin);
                    StringBuilder sb = new StringBuilder();

                    if (allDay) {
                        SimpleDateFormat sdf = new SimpleDateFormat(
                                getString(R.string.abbrev_wday_month_day_no_year));
                        // Calendar stores all-day events in UTC -- setting the time zone ensures
                        // the correct date is shown.
                        sdf.setTimeZone(TimeZone.getTimeZone(Time.TIMEZONE_UTC));
                        sb.append(sdf.format(start));
                    } else {
                        sb.append(DateFormat.format("E", start));
                        sb.append(" ");
                        sb.append(DateFormat.getTimeFormat(this).format(start));
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
                    nextCalendarAlarm[eventCount][1] = sb.toString();
                    cursor.moveToNext();

                    // Increment the event counter
                    eventCount++;
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
