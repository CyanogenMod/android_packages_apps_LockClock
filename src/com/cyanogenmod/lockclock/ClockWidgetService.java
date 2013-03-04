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
import android.app.IntentService;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.Time;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import com.cyanogenmod.lockclock.misc.CalendarInfo;
import com.cyanogenmod.lockclock.misc.CalendarInfo.EventInfo;
import com.cyanogenmod.lockclock.misc.Constants;
import com.cyanogenmod.lockclock.misc.Preferences;
import com.cyanogenmod.lockclock.misc.WidgetUtils;
import com.cyanogenmod.lockclock.weather.WeatherInfo;
import com.cyanogenmod.lockclock.weather.WeatherUpdateService;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;

public class ClockWidgetService extends IntentService {
    private static final String TAG = "ClockWidgetService";
    private static final boolean D = Constants.DEBUG;

    public static final String ACTION_REFRESH = "com.cyanogenmod.lockclock.action.REFRESH_WIDGET";
    public static final String ACTION_REFRESH_CALENDAR = "com.cyanogenmod.lockclock.action.REFRESH_CALENDAR";

    private static final long DAY_IN_MILLIS = 24L * 60L * 60L * 1000L;

    private int[] mWidgetIds;
    private AppWidgetManager mAppWidgetManager;
    private boolean mRefreshCalendar;

    public ClockWidgetService() {
        super("ClockWidgetService");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        ComponentName thisWidget = new ComponentName(this, ClockWidgetProvider.class);
        mAppWidgetManager = AppWidgetManager.getInstance(this);
        mWidgetIds = mAppWidgetManager.getAppWidgetIds(thisWidget);

        mRefreshCalendar = false;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (D) Log.d(TAG, "Got intent " + intent);
        if (intent != null && ACTION_REFRESH_CALENDAR.equals(intent.getAction())) {
            if (D) Log.v(TAG, "Forcing a calendar refresh");
            mRefreshCalendar = true;
        }

        if (mWidgetIds != null && mWidgetIds.length != 0) {
            refreshWidget();
        }
    }

    /**
     * Reload the widget including the Weather forecast, Alarm, Clock font and Calendar
     */
    private void refreshWidget() {
        // Get things ready
        RemoteViews remoteViews;
        boolean digitalClock = Preferences.showDigitalClock(this);
        boolean showWeather = Preferences.showWeather(this);
        boolean showCalendar = false;

        // Update the widgets
        for (int id : mWidgetIds) {

            // Determine which layout to use
            boolean smallWidget = showWeather && WidgetUtils.showSmallWidget(this, id, digitalClock);
            if (smallWidget) {
                // The small widget is only shown if weather needs to be shown
                // and there is not enough space for the full weather widget
                remoteViews = new RemoteViews(getPackageName(), R.layout.appwidget_small);
                showCalendar = false;
            } else {
                remoteViews = new RemoteViews(getPackageName(), R.layout.appwidget);
                showCalendar = Preferences.showCalendar(this);
            }

            // Always Refresh the Clock widget
            refreshClock(remoteViews, smallWidget, digitalClock);
            refreshAlarmStatus(remoteViews, smallWidget);

            // Don't bother with Calendar if its not enabled
            if (showCalendar) {
                showCalendar &= refreshCalendar(remoteViews);
            }

            // Hide the Loading indicator
            remoteViews.setViewVisibility(R.id.loading_indicator, View.GONE);

            // Now, if we need to show the actual weather, do so
            if (showWeather) {
                WeatherInfo weatherInfo = Preferences.getCachedWeatherInfo(this);

                if (weatherInfo != null) {
                    setWeatherData(remoteViews, smallWidget, weatherInfo);
                } else {
                    setNoWeatherData(remoteViews, smallWidget);
                }
            }

            // Resize the clock font if needed
            if (digitalClock && !smallWidget) {
                float ratio = WidgetUtils.getScaleRatio(this, id);
                setClockSize(remoteViews, ratio);
            }

            if (showWeather) {
                boolean canFitWeather = smallWidget || WidgetUtils.canFitWeather(this, id, digitalClock);
                remoteViews.setViewVisibility(R.id.weather_panel, canFitWeather ? View.VISIBLE : View.GONE);
            }

            // Hide the calendar panel if there is no space for it
            if (showCalendar) {
                boolean canFitCalendar = WidgetUtils.canFitCalendar(this, id, digitalClock);
                remoteViews.setViewVisibility(R.id.calendar_panel, canFitCalendar ? View.VISIBLE : View.GONE);
            }

            // Do the update
            mAppWidgetManager.updateAppWidget(id, remoteViews);
        }

        if (showCalendar) {
            scheduleCalendarUpdate();
        }
    }

    //===============================================================================================
    // Clock related functionality
    //===============================================================================================
    private void refreshClock(RemoteViews clockViews, boolean smallWidget, boolean digitalClock) {
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

        // Date/Alarm is common to both clocks, set it's size
        refreshDateAlarmFont(clockViews, smallWidget);

        // Register an onClickListener on Clock, starting DeskClock
        ComponentName clk = new ComponentName("com.android.deskclock", "com.android.deskclock.DeskClock");
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(clk);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
        clockViews.setOnClickPendingIntent(R.id.clock_panel, pi);
    }

    private void refreshClockFont(RemoteViews clockViews) {
        // Hours
        if (Preferences.useBoldFontForHours(this)) {
            clockViews.setViewVisibility(R.id.clock1_bold, View.VISIBLE);
            clockViews.setViewVisibility(R.id.clock1_regular, View.GONE);
        } else {
            clockViews.setViewVisibility(R.id.clock1_regular, View.VISIBLE);
            clockViews.setViewVisibility(R.id.clock1_bold, View.GONE);
        }

        // Minutes
        if (Preferences.useBoldFontForMinutes(this)) {
            clockViews.setViewVisibility(R.id.clock2_bold, View.VISIBLE);
            clockViews.setViewVisibility(R.id.clock2_regular, View.GONE);
        } else {
            clockViews.setViewVisibility(R.id.clock2_regular, View.VISIBLE);
            clockViews.setViewVisibility(R.id.clock2_bold, View.GONE);
        }
    }

    private void refreshDateAlarmFont(RemoteViews clockViews, boolean smallWidget) {
        // Date and Alarm font
        if (!smallWidget) {
            if (Preferences.useBoldFontForDateAndAlarms(this)) {
                clockViews.setViewVisibility(R.id.date_bold, View.VISIBLE);
                clockViews.setViewVisibility(R.id.date_regular, View.GONE);
            } else {
                clockViews.setViewVisibility(R.id.date_regular, View.VISIBLE);
                clockViews.setViewVisibility(R.id.date_bold, View.GONE);
            }
        } else {
            clockViews.setViewVisibility(R.id.date, View.VISIBLE);
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
    private void refreshAlarmStatus(RemoteViews alarmViews, boolean smallWidget) {
        if (Preferences.showAlarm(this)) {
            String nextAlarm = getNextAlarm();
            if (!TextUtils.isEmpty(nextAlarm)) {
                // An alarm is set, deal with displaying it
                if (!smallWidget) {
                    boolean isBold = Preferences.useBoldFontForDateAndAlarms(this);
                    alarmViews.setTextViewText(isBold ? R.id.nextAlarm_bold : R.id.nextAlarm_regular,
                            nextAlarm.toString().toUpperCase());
                    alarmViews.setViewVisibility(R.id.nextAlarm_bold, isBold ? View.VISIBLE : View.GONE);
                    alarmViews.setViewVisibility(R.id.nextAlarm_regular, isBold ? View.GONE : View.VISIBLE);
                } else {
                    alarmViews.setTextViewText(R.id.nextAlarm, nextAlarm.toString().toUpperCase());
                    alarmViews.setViewVisibility(R.id.nextAlarm, View.VISIBLE);
                }
                return;
            }
        }

        // No alarm set or Alarm display is hidden, hide the views
        if (!smallWidget) {
            alarmViews.setViewVisibility(R.id.nextAlarm_bold, View.GONE);
            alarmViews.setViewVisibility(R.id.nextAlarm_regular, View.GONE);
        } else {
            alarmViews.setViewVisibility(R.id.nextAlarm, View.GONE);
        }
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
    /**
     * Display the weather information
     */
    private void setWeatherData(RemoteViews weatherViews, boolean smallWidget, WeatherInfo w) {

        // Weather Image
        weatherViews.setImageViewResource(R.id.weather_image, w.getConditionResource());

        // Weather Condition
        weatherViews.setTextViewText(R.id.weather_condition, w.getCondition());
        weatherViews.setViewVisibility(R.id.weather_condition, View.VISIBLE);

        // Weather Temps Panel
        weatherViews.setTextViewText(R.id.weather_temp, w.getFormattedTemperature());
        weatherViews.setViewVisibility(R.id.weather_temps_panel, View.VISIBLE);

        if (!smallWidget) {
            // Display the full weather information panel items
            // Load the preferences
            boolean showLocation = Preferences.showWeatherLocation(this);
            boolean showTimestamp = Preferences.showWeatherTimestamp(this);

            // City
            weatherViews.setTextViewText(R.id.weather_city, w.getCity());
            weatherViews.setViewVisibility(R.id.weather_city, showLocation ? View.VISIBLE : View.GONE);

            // Weather Update Time
            if (showTimestamp) {
                Date updateTime = w.getTimestamp();
                StringBuilder sb = new StringBuilder();
                sb.append(DateFormat.format("E", updateTime));
                sb.append(" ");
                sb.append(DateFormat.getTimeFormat(this).format(updateTime));
                weatherViews.setTextViewText(R.id.update_time, sb.toString());
                weatherViews.setViewVisibility(R.id.update_time, View.VISIBLE);
            } else {
                weatherViews.setViewVisibility(R.id.update_time, View.GONE);
            }

            // Weather Temps Panel additional items
            boolean invertLowhigh = Preferences.invertLowHighTemperature(this);
            final String low = w.getFormattedLow();
            final String high = w.getFormattedHigh();
            weatherViews.setTextViewText(R.id.weather_low_high, invertLowhigh ? high + " | " + low : low + " | " + high);
        }

        // Register an onClickListener on Weather
        setWeatherClickListener(weatherViews);
    }

    /**
     * There is no data to display, display 'empty' fields and the 'Tap to reload' message
     */
    private void setNoWeatherData(RemoteViews weatherViews, boolean smallWidget) {
        boolean defaultIcons = !Preferences.useAlternateWeatherIcons(this);
        final Resources res = getBaseContext().getResources();

        // Weather Image - Either the default or alternate set
        weatherViews.setImageViewResource(R.id.weather_image,
                defaultIcons ? R.drawable.weather_na : R.drawable.weather2_na);

        if (!smallWidget) {
            weatherViews.setTextViewText(R.id.weather_city, res.getString(R.string.weather_no_data));
            weatherViews.setViewVisibility(R.id.weather_city, View.VISIBLE);
            weatherViews.setViewVisibility(R.id.update_time, View.GONE);
        }

        weatherViews.setViewVisibility(R.id.weather_temps_panel, View.GONE);
        weatherViews.setTextViewText(R.id.weather_condition, res.getString(R.string.weather_tap_to_refresh));

        // Register an onClickListener on Weather
        setWeatherClickListener(weatherViews);
    }

    private void setWeatherClickListener(RemoteViews weatherViews) {
        weatherViews.setOnClickPendingIntent(R.id.weather_panel,
                WeatherUpdateService.getUpdateIntent(this, true));
    }

    //===============================================================================================
    // Calendar related functionality
    //===============================================================================================
    private static CalendarInfo mCalendarInfo = new CalendarInfo();

    private boolean refreshCalendar(RemoteViews calendarViews) {
        // Load the settings
        Set<String> calendarList = Preferences.calendarsToDisplay(this);
        boolean remindersOnly = Preferences.showEventsWithRemindersOnly(this);
        boolean hideAllDay = !Preferences.showAllDayEvents(this);
        long lookAhead = Preferences.lookAheadTimeInMs(this);
        boolean hasEvents = false;

        // Remove all the views to start
        calendarViews.removeAllViews(R.id.calendar_panel);

        // If we don't have any events yet or forcing a refresh, get the next batch of events
        if (!mCalendarInfo.hasEvents() || mRefreshCalendar) {
            if (D) Log.d(TAG, "Checking for calendar events...");
            getCalendarEvents(lookAhead, calendarList, remindersOnly, hideAllDay);
            mRefreshCalendar = false;
        }

        // Iterate through the events and add them to the views
        for (EventInfo event : mCalendarInfo.getEvents()) {
            final RemoteViews itemViews = new RemoteViews(getPackageName(), R.layout.calendar_item);

            // Only set the icon on the first event
            if (!hasEvents) {
                itemViews.setImageViewResource(R.id.calendar_icon, R.drawable.ic_lock_idle_calendar);
            }

            // Add the event text fields
            itemViews.setTextViewText(R.id.calendar_event_title, event.title);
            itemViews.setTextViewText(R.id.calendar_event_details, event.description);

            if (D) Log.v(TAG, "Showing event: " + event.title);

            // Add the view to the panel
            calendarViews.addView(R.id.calendar_panel, itemViews);
            hasEvents = true;
        }

        // Register an onClickListener on Calendar if it contains any events, starting the Calendar app
        if (hasEvents) {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR);
            PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
            calendarViews.setOnClickPendingIntent(R.id.calendar_panel, pi);
        }
        return hasEvents;
    }

    /**
     * Get the next set of calendar events (up to MAX_CALENDAR_ITEMS) within a certain look-ahead time.
     * Result is stored in the CalendarInfo object
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
        if (hideAllDay) {
            if (remindersOnly) {
                where.append(" AND ");
            }
            where.append(CalendarContract.Events.ALL_DAY + "!=1");
        }
        if (calendars != null && calendars.size() > 0) {
            if (remindersOnly || hideAllDay) {
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
        };

        // The indices for the projection array
        int EVENT_ID_INDEX = 0;
        int TITLE_INDEX = 1;
        int BEGIN_TIME_INDEX = 2;
        int END_TIME_INDEX = 3;
        int DESCRIPTION_INDEX = 4;
        int LOCATION_INDEX = 5;
        int ALL_DAY_INDEX = 6;

        // all day events are stored in UTC, that is why we need to fetch events after 'later'
        Uri uri = Uri.withAppendedPath(CalendarContract.Instances.CONTENT_URI,
                String.format("%d/%d", now - DAY_IN_MILLIS, later + DAY_IN_MILLIS));
        Cursor cursor = null;
        mCalendarInfo.clearEvents();

        try {
            cursor = getContentResolver().query(uri, projection,
                    where.toString(), null, CalendarContract.Instances.BEGIN + " ASC");

            if (cursor != null) {
                final SimpleDateFormat allDayFormat = new SimpleDateFormat(
                        getString(R.string.abbrev_wday_month_day_no_year));
                final java.text.DateFormat eventFormat = DateFormat.getTimeFormat(this);
                final int showLocation = Preferences.calendarLocationMode(this);
                final int showDescription = Preferences.calendarDescriptionMode(this);
                final Time time = new Time();
                int eventCount = 0;

                cursor.moveToPosition(-1);
                // Iterate through returned rows to a maximum number of calendar events
                while (cursor.moveToNext() && eventCount < Constants.MAX_CALENDAR_ITEMS) {
                    long eventId = cursor.getLong(EVENT_ID_INDEX);
                    String title = cursor.getString(TITLE_INDEX);
                    long begin = cursor.getLong(BEGIN_TIME_INDEX);
                    long end = cursor.getLong(END_TIME_INDEX);
                    String description = cursor.getString(DESCRIPTION_INDEX);
                    String location = cursor.getString(LOCATION_INDEX);
                    boolean allDay = cursor.getInt(ALL_DAY_INDEX) != 0;

                    if (allDay) {
                        begin = convertUtcToLocal(time, begin);
                        end = convertUtcToLocal(time, end);
                    }

                    if (end < now || begin > later) {
                        continue;
                    }

                    if (D) Log.v(TAG, "Adding event: " + title + " with id: " + eventId);

                    // Start building the event details string
                    // Starting with the date
                    Date startDate = new Date(begin);
                    Date endDate = new Date(end);
                    StringBuilder sb = new StringBuilder();

                    if (allDay) {
                        sb.append(allDayFormat.format(startDate));
                    } else {
                        sb.append(DateFormat.format("E", startDate));
                        sb.append(" ");
                        sb.append(eventFormat.format(startDate));
                        sb.append(" - ");
                        sb.append(eventFormat.format(endDate));
                    }

                    // Add the event location if it should be shown
                    if (showLocation != Preferences.SHOW_NEVER && !TextUtils.isEmpty(location)) {
                        switch (showLocation) {
                            case Preferences.SHOW_FIRST_LINE:
                                int stringEnd = location.indexOf('\n');
                                if(stringEnd == -1) {
                                    sb.append(": " + location);
                                } else {
                                    sb.append(": " + location.substring(0, stringEnd));
                                }
                                break;
                            case Preferences.SHOW_ALWAYS:
                                sb.append(": " + location);
                                break;
                        }
                    }

                    // Add the event description if it should be shown
                    if (showDescription != Preferences.SHOW_NEVER && !TextUtils.isEmpty(description)) {
                        // Show the appropriate separator
                        if (showLocation == Preferences.SHOW_NEVER) {
                            sb.append(": ");
                        } else {
                            sb.append(" - ");
                        }

                        switch (showDescription) {
                            case Preferences.SHOW_FIRST_LINE:
                                int stringEnd = description.indexOf('\n');
                                if(stringEnd == -1) {
                                    sb.append(description);
                                } else {
                                    sb.append(description.substring(0, stringEnd));
                                }
                                break;
                            case Preferences.SHOW_ALWAYS:
                                sb.append(description);
                                break;
                        }
                    }

                    // Add the event details to the CalendarInfo object and move to next record
                    mCalendarInfo.addEvent(populateEventInfo(eventId, title, sb.toString(), begin, end, allDay));
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

        // check for first event outside of lookahead window
        long endOfLookahead = now + lookahead;
        long minUpdateTime = getMinUpdateFromNow(endOfLookahead);

        // don't bother with querying if the end result is later than the
        // minimum update time anyway
        if (endOfLookahead < minUpdateTime) {
            if (where.length() > 0) {
                where.append(" AND ");
            }
            where.append(CalendarContract.Instances.BEGIN);
            where.append(" > ");
            where.append(endOfLookahead);

            uri = Uri.withAppendedPath(CalendarContract.Instances.CONTENT_URI,
                    String.format("%d/%d", endOfLookahead, minUpdateTime));
            projection = new String[] { CalendarContract.Instances.BEGIN };
            cursor = getContentResolver().query(uri, projection, where.toString(), null,
                    CalendarContract.Instances.BEGIN + " ASC limit 1");

            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    mCalendarInfo.setFollowingEventStart(cursor.getLong(0));
                }
                cursor.close();
            }
        }
    }

    private long convertUtcToLocal(Time time, long utcTime) {
        time.timezone = Time.TIMEZONE_UTC;
        time.set(utcTime);
        time.timezone = Time.getCurrentTimezone();
        return time.normalize(true);
    }

    /**
     * Construct the EventInfo object
     */
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
        final long now = System.currentTimeMillis();
        long lookAhead = Preferences.lookAheadTimeInMs(this);
        long minUpdateTime = getMinUpdateFromNow(now);

        // Check if there is a calendar event earlier
        for (EventInfo event : mCalendarInfo.getEvents()) {
            final long end = event.end;
            final long start = event.start;
            if (now < start) {
                minUpdateTime = Math.min(minUpdateTime, start);
            }
            if (now < end) {
                minUpdateTime = Math.min(minUpdateTime, end);
            }
        }

        if (mCalendarInfo.getFollowingEventStart() > 0) {
            // Make sure to update when the next event gets into the lookahead window
            minUpdateTime = Math.min(minUpdateTime, mCalendarInfo.getFollowingEventStart() - lookAhead);
        }

        // Construct a log entry in human readable form
        if (D) {
            Date date1 = new Date(now);
            Date date2 = new Date(minUpdateTime);
            Log.i(TAG, "Chronus: It is now " + DateFormat.getTimeFormat(this).format(date1)
                    + ", next widget update at " + DateFormat.getTimeFormat(this).format(date2));
        }

        // Return the next update time
        return minUpdateTime;
    }

    private long getMinUpdateFromNow(long now) {
        /* we update at least once a day */
        return now + DAY_IN_MILLIS;
    }

    private static PendingIntent getRefreshIntent(Context context) {
        Intent i = new Intent(context, ClockWidgetService.class);
        i.setAction(ACTION_REFRESH_CALENDAR);
        return PendingIntent.getService(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Schedule an alarm to trigger an update at the next weather refresh or at the next event
     * time boundary (start/end).
     */
    private void scheduleCalendarUpdate() {
        PendingIntent pi = getRefreshIntent(this);
        long updateTime = calculateUpdateTime();

        // Clear any old alarms and schedule the new alarm
        // Since the updates are now only done very infrequently, it can wake the device to ensure the
        // latest date is available when the user turns the screen on after a few hours sleep
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        am.cancel(pi);
        if (updateTime > 0) {
            am.set(AlarmManager.RTC_WAKEUP, updateTime, pi);
        }
    }

    public static void cancelUpdates(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(getRefreshIntent(context));
    }
}
