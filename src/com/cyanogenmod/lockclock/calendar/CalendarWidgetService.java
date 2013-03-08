/*
 * Copyright (C) 2013 The CyanogenMod Project (DvTonder)
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

package com.cyanogenmod.lockclock.calendar;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import android.widget.RemoteViewsService.RemoteViewsFactory;

import com.cyanogenmod.lockclock.ClockWidgetService;
import com.cyanogenmod.lockclock.R;
import com.cyanogenmod.lockclock.misc.CalendarInfo;
import com.cyanogenmod.lockclock.misc.Constants;
import com.cyanogenmod.lockclock.misc.Preferences;
import com.cyanogenmod.lockclock.misc.CalendarInfo.EventInfo;

import java.util.Date;
import java.util.Set;

public class CalendarWidgetService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new CalendarRemoteViewsFactory(this.getApplicationContext(), intent);
    }

}

class CalendarRemoteViewsFactory implements RemoteViewsFactory {
    private static final String TAG = "CalendarRemoteViewsFactory";
    private static boolean D = Constants.DEBUG;

    private static final long DAY_IN_MILLIS = 24L * 60L * 60L * 1000L;

    private Context mContext;
    private int mAppWidgetId;
    private static CalendarInfo mCalendarInfo = new CalendarInfo();
    private static int mWidgetCount = 0;
    private static Object mUpdateLock = new Object();
    private static boolean mUpdating;

    public CalendarRemoteViewsFactory(Context applicationContext, Intent intent) {
        mContext = applicationContext;
        mAppWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
    }

    @Override
    public int getCount() {
        return mCalendarInfo.getEvents().size();
    }

    @Override
    public long getItemId(int position) {
        return mCalendarInfo.getEvents().get(position).id;
    }

    @Override
    public RemoteViews getLoadingView() {
        return null;
    }

    @Override
    public RemoteViews getViewAt(int position) {
        if (0 > position || mCalendarInfo.getEvents().size() < position) {
            return null;
        }
        final RemoteViews itemViews = new RemoteViews(mContext.getPackageName(),
                R.layout.calendar_item);
        final EventInfo event = mCalendarInfo.getEvents().get(position);

        // Add the event text fields
        itemViews.setTextViewText(R.id.calendar_event_title, event.title);
        itemViews.setTextViewText(R.id.calendar_event_details, event.description);
        if (D) Log.v(TAG, "Showing event: " + event.title);

        // Register an onClickListener on entries, starting the Calendar app
        Bundle extras = new Bundle();
        extras.putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
        Intent fillInIntent = new Intent();
        fillInIntent.putExtras(extras);
        itemViews.setOnClickFillInIntent(R.id.calendar_item, fillInIntent);

        return itemViews;
    }

    @Override
    public int getViewTypeCount() {
        // There's only one view type for the events
        return 1;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public void onCreate() {
        // populate calendar info only on first created widget
        if (0 == mWidgetCount) {
            updateCalendarInfo(mContext, false);
        }
        mWidgetCount++;
    }

    @Override
    public void onDataSetChanged() {
        if (D) Log.v(TAG, "onDataSetChanged()");
        updateCalendarInfo(mContext, true);
    }

    private static void updateCalendarInfo(Context context, boolean force) {
        // Load the settings
        Set<String> calendarList = Preferences.calendarsToDisplay(context);
        boolean remindersOnly = Preferences.showEventsWithRemindersOnly(context);
        boolean hideAllDay = !Preferences.showAllDayEvents(context);
        long lookAhead = Preferences.lookAheadTimeInMs(context);

        // If we don't have any events yet or forcing a refresh, get the next
        // batch of events
        if (force || !mCalendarInfo.hasEvents()) {
            if (D) Log.d(TAG, "Checking for calendar events..." + (force ? " (forced)" : ""));
            getCalendarEvents(context, lookAhead, calendarList, remindersOnly, hideAllDay);
        }
        scheduleCalendarUpdate(context);
    }

    /**
     * Get the next set of calendar events (up to MAX_CALENDAR_ITEMS) within a
     * certain look-ahead time. Result is stored in the CalendarInfo object
     */
    private static void getCalendarEvents(Context context, long lookahead, Set<String> calendars,
            boolean remindersOnly, boolean hideAllDay) {
        // do not execute in parallel
        if (D) Log.v(TAG, "getCalendarEvents() mUpdating = " + mUpdating);
        synchronized(mUpdateLock) {
            if (mUpdating) {
                return;
            }
            mUpdating = true;
        }
        try
        {
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

            // all day events are stored in UTC, that is why we need to fetch events
            // after 'later'
            Uri uri = Uri.withAppendedPath(CalendarContract.Instances.CONTENT_URI,
                    String.format("%d/%d", now - DAY_IN_MILLIS, later + DAY_IN_MILLIS));
            Cursor cursor = null;
            mCalendarInfo.clearEvents();

            try {
                cursor = context.getContentResolver().query(uri, projection,
                        where.toString(), null, CalendarContract.Instances.BEGIN + " ASC");

                if (cursor != null) {
                    final int showLocation = Preferences.calendarLocationMode(context);
                    final int showDescription = Preferences.calendarDescriptionMode(context);
                    final Time time = new Time();
                    int eventCount = 0;

                    cursor.moveToPosition(-1);
                    // Iterate through returned rows to a maximum number of calendar
                    // events
                    while (cursor.moveToNext() && eventCount < Constants.MAX_CALENDAR_ITEMS) {
                        long eventId = cursor.getLong(EVENT_ID_INDEX);
                        String title = cursor.getString(TITLE_INDEX);
                        long begin = cursor.getLong(BEGIN_TIME_INDEX);
                        long end = cursor.getLong(END_TIME_INDEX);
                        String description = cursor.getString(DESCRIPTION_INDEX);
                        String location = cursor.getString(LOCATION_INDEX);
                        boolean allDay = cursor.getInt(ALL_DAY_INDEX) != 0;
                        int format = 0;

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
                        StringBuilder sb = new StringBuilder();

                        if (allDay) {
                            format = Constants.CALENDAR_FORMAT_ALLDAY;
                        } else if (DateUtils.isToday(begin)) {
                            format = Constants.CALENDAR_FORMAT_TODAY;
                        } else {
                            format = Constants.CALENDAR_FORMAT_FUTURE;
                        }
                        if (allDay || begin == end) {
                            sb.append(DateUtils.formatDateTime(context, begin, format));
                        } else {
                            sb.append(DateUtils.formatDateRange(context, begin, end, format));
                        }

                        // Add the event location if it should be shown
                        if (showLocation != Preferences.SHOW_NEVER && !TextUtils.isEmpty(location)) {
                            switch (showLocation) {
                                case Preferences.SHOW_FIRST_LINE:
                                    int stringEnd = location.indexOf('\n');
                                    if (stringEnd == -1) {
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
                        if (showDescription != Preferences.SHOW_NEVER
                                && !TextUtils.isEmpty(description)) {
                            // Show the appropriate separator
                            if (showLocation == Preferences.SHOW_NEVER) {
                                sb.append(": ");
                            } else {
                                sb.append(" - ");
                            }

                            switch (showDescription) {
                                case Preferences.SHOW_FIRST_LINE:
                                    int stringEnd = description.indexOf('\n');
                                    if (stringEnd == -1) {
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

                        // Add the event details to the CalendarInfo object and move
                        // to next record
                        mCalendarInfo.addEvent(populateEventInfo(eventId, title, sb.toString(), begin,
                                end, allDay));
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
                projection = new String[] {
                        CalendarContract.Instances.BEGIN
                };
                cursor = context.getContentResolver().query(uri, projection, where.toString(), null,
                        CalendarContract.Instances.BEGIN + " ASC limit 1");

                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        mCalendarInfo.setFollowingEventStart(cursor.getLong(0));
                    }
                    cursor.close();
                }
            }
        } finally {
            synchronized(mUpdateLock) {
                mUpdating = false;
            }
        }
    }

    private static long convertUtcToLocal(Time time, long utcTime) {
        time.timezone = Time.TIMEZONE_UTC;
        time.set(utcTime);
        time.timezone = Time.getCurrentTimezone();
        return time.normalize(true);
    }

    /**
     * Construct the EventInfo object
     */
    private static EventInfo populateEventInfo(long eventId, String title, String description,
            long begin, long end, boolean allDay) {
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

    private static long getMinUpdateFromNow(long now) {
        /* we update at least once a day */
        return now + DAY_IN_MILLIS;
    }

    // ===============================================================================================
    // Update timer related functionality
    // ===============================================================================================
    /**
     * Calculates and returns the next time we should push widget updates.
     */
    private static long calculateUpdateTime(Context context) {
        final long now = System.currentTimeMillis();
        long lookAhead = Preferences.lookAheadTimeInMs(context);
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
            // Make sure to update when the next event gets into the lookahead
            // window
            minUpdateTime = Math.min(minUpdateTime, mCalendarInfo.getFollowingEventStart()
                    - lookAhead);
        }

        // Construct a log entry in human readable form
        if (D) {
            Date date1 = new Date(now);
            Date date2 = new Date(minUpdateTime);
            Log.i(TAG, "Chronus: It is now " + DateFormat.getTimeFormat(context).format(date1)
                    + ", next widget update at " + DateFormat.getTimeFormat(context).format(date2));
        }

        // Return the next update time
        return minUpdateTime;
    }

    /**
     * Schedule an alarm to trigger an update at the next weather refresh or at
     * the next event time boundary (start/end).
     */
    private static void scheduleCalendarUpdate(Context context) {
        PendingIntent pi = ClockWidgetService.getRefreshIntent(context);
        long updateTime = calculateUpdateTime(context);

        // Clear any old alarms and schedule the new alarm
        // Since the updates are now only done very infrequently, it can wake
        // the device to ensure the
        // latest date is available when the user turns the screen on after a
        // few hours sleep
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pi);
        if (updateTime > 0) {
            am.set(AlarmManager.RTC_WAKEUP, updateTime, pi);
        }
    }

    @Override
    public void onDestroy() {
        mWidgetCount--;
        // cleanup calendar info on destruction of last widget
        if (0 == mWidgetCount) {
            mCalendarInfo.clearEvents();
        }
    }
}
