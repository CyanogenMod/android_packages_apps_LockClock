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
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Events;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import android.widget.RemoteViewsService.RemoteViewsFactory;

import com.cyanogenmod.lockclock.ClockWidgetProvider;
import com.cyanogenmod.lockclock.ClockWidgetService;
import com.cyanogenmod.lockclock.R;
import com.cyanogenmod.lockclock.misc.CalendarInfo;
import com.cyanogenmod.lockclock.misc.CalendarInfo.EventInfo;
import com.cyanogenmod.lockclock.misc.Constants;
import com.cyanogenmod.lockclock.misc.Preferences;

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
    private CalendarInfo mCalendarInfo = new CalendarInfo();

    public CalendarRemoteViewsFactory(Context applicationContext, Intent intent) {
        mContext = applicationContext;
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

        int color = Preferences.calendarFontColor(mContext);
        int detailsColor = Preferences.calendarDetailsFontColor(mContext);
        final RemoteViews itemViews = new RemoteViews(mContext.getPackageName(),
                R.layout.calendar_item);
        final EventInfo event = mCalendarInfo.getEvents().get(position);

        // Add the event text fields
        itemViews.setTextViewText(R.id.calendar_event_title, event.title);
        itemViews.setTextViewText(R.id.calendar_event_details, event.description);
        itemViews.setTextColor(R.id.calendar_event_title, color);
        itemViews.setTextColor(R.id.calendar_event_details, detailsColor);
        if (D) Log.v(TAG, "Showing at position " + position + " event: " + event.title);

        final Intent fillInIntent = new Intent();
        fillInIntent.setData(ContentUris.withAppendedId(Events.CONTENT_URI, event.id));
        // work around stock calendar not displaying the correct date with only uri
        fillInIntent.putExtra("beginTime", event.start);
        fillInIntent.putExtra("endTime", event.end);
        fillInIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
              | Intent.FLAG_ACTIVITY_SINGLE_TOP
              | Intent.FLAG_ACTIVITY_CLEAR_TOP
              | Intent.FLAG_ACTIVITY_NO_HISTORY
              | Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
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
        updateCalendarInfo(mContext, false);
    }

    @Override
    public void onDataSetChanged() {
        if (D) Log.v(TAG, "onDataSetChanged()");
        updateCalendarInfo(mContext, true);
        updatePanelVisibility();
    }

    private void updateCalendarInfo(Context context, boolean force) {
        // Load the settings
        Set<String> calendarList = Preferences.calendarsToDisplay(context);
        boolean remindersOnly = Preferences.showEventsWithRemindersOnly(context);
        boolean hideAllDay = !Preferences.showAllDayEvents(context);
        long lookAhead = Preferences.lookAheadTimeInMs(context);

        // If we don't have any events yet or forcing a refresh, get the next batch of events
        if (force || !mCalendarInfo.hasEvents()) {
            if (D) Log.d(TAG, "Checking for calendar events..." + (force ? " (forced)" : ""));
            getCalendarEvents(context, lookAhead, calendarList, remindersOnly, hideAllDay);
        }
        scheduleCalendarUpdate(context);
    }

    /**
     * Trigger the hiding of the Calendar panel if there are no events to display
     */
    private void updatePanelVisibility() {
        if (!mCalendarInfo.hasEvents()) {
            if (D) Log.v(TAG, "No events - Hide calendar panel");
            Intent updateIntent = new Intent(mContext, ClockWidgetProvider.class);
            updateIntent.setAction(ClockWidgetService.ACTION_HIDE_CALENDAR);
            mContext.sendBroadcast(updateIntent);
        }
    }

    /**
     * Get the next set of calendar events (up to MAX_CALENDAR_ITEMS) within a
     * certain look-ahead time. Result is stored in the CalendarInfo object
     */
    private void getCalendarEvents(Context context, long lookahead, Set<String> calendars,
            boolean remindersOnly, boolean hideAllDay) {
        long now = System.currentTimeMillis();
        long later = now + lookahead;
        CalendarInfo newCalendarInfo = new CalendarInfo();

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
                CalendarContract.Instances.EVENT_ID,
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

        try {
            cursor = context.getContentResolver().query(uri, projection,
                    where.toString(), null, CalendarContract.Instances.BEGIN + " ASC");

            if (cursor != null) {
                final int showLocation = Preferences.calendarLocationMode(context);
                final int showDescription = Preferences.calendarDescriptionMode(context);
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

                    // Add the event details to the CalendarInfo object and move to next record
                    newCalendarInfo.addEvent(populateEventInfo(eventId, title, sb.toString(), begin,
                            end, allDay));
                    eventCount++;
                }
            }
        } catch (Exception e) {
            // Do nothing
        } finally {
            mCalendarInfo = newCalendarInfo;
            if (cursor != null) {
                cursor.close();
            }
        }

        // check for first event outside of lookahead window
        long endOfLookahead = now + lookahead;
        long minUpdateTime = getMinUpdateFromNow(endOfLookahead);

        // don't bother with querying if the end result is later than the minimum update time anyway
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

        // Populate the fields
        eventInfo.id = eventId;
        eventInfo.title = title;
        eventInfo.description = description;
        eventInfo.start = begin;
        eventInfo.end = end;
        eventInfo.allDay = allDay;

        return eventInfo;
    }

    private static long getMinUpdateFromNow(long now) {
        // we update at least once a day
        return now + DAY_IN_MILLIS;
    }

    // ===============================================================================================
    // Update timer related functionality
    // ===============================================================================================
    /**
     * Calculates and returns the next time we should push widget updates.
     */
    private long calculateUpdateTime(Context context) {
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
            // Make sure to update when the next event gets into the lookahead window
            minUpdateTime = Math.min(minUpdateTime, mCalendarInfo.getFollowingEventStart()
                    - lookAhead);
        }

        // Construct a log entry in human readable form
        if (D) {
            Date date1 = new Date(now);
            Date date2 = new Date(minUpdateTime);
            Log.i(TAG, "cLock: It is now " + DateFormat.getTimeFormat(context).format(date1)
                    + ", next widget update at " + DateFormat.getDateFormat(context).format(date2)
                    + " at " + DateFormat.getTimeFormat(context).format(date2));
        }

        // Return the next update time
        return minUpdateTime;
    }

    /**
     * Schedule an alarm to trigger an update at the next weather refresh or at
     * the next event time boundary (start/end).
     */
    private void scheduleCalendarUpdate(Context context) {
        PendingIntent pi = ClockWidgetService.getRefreshIntent(context);
        long updateTime = calculateUpdateTime(context);

        // Clear any old alarms and schedule the new alarm
        // Since the updates are now only done very infrequently, it can wake the device to ensure
        // the latest date is available when the user turns the screen on after a few hours sleep
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pi);
        if (updateTime > 0) {
            am.set(AlarmManager.RTC_WAKEUP, updateTime, pi);
        }
    }

    @Override
    public void onDestroy() {
        mCalendarInfo.clearEvents();
    }
}
