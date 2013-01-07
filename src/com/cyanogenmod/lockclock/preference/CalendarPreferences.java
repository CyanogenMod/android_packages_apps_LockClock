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

package com.cyanogenmod.lockclock.preference;

import static com.cyanogenmod.lockclock.misc.Constants.PREF_NAME;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.provider.CalendarContract;

import com.cyanogenmod.lockclock.ClockWidgetProvider;
import com.cyanogenmod.lockclock.R;
import com.cyanogenmod.lockclock.misc.Constants;

import java.util.ArrayList;
import java.util.List;

public class CalendarPreferences extends PreferenceFragment implements
    OnSharedPreferenceChangeListener {
    private static final String TAG = "Calendar Preferences";

    private MultiSelectListPreference mCalendarList;
    private Context mContext;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getPreferenceManager().setSharedPreferencesName(PREF_NAME);
        addPreferencesFromResource(R.xml.preferences_calendar);
        mContext = getActivity();

        // Load the required settings from preferences
        SharedPreferences prefs = mContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        // The calendar list entries and values are determined at run time, not in XML
        mCalendarList = (MultiSelectListPreference) findPreference(Constants.CALENDAR_LIST);
        CalendarEntries calEntries = CalendarEntries.findCalendars(getActivity());
        mCalendarList.setEntries(calEntries.getEntries());
        mCalendarList.setEntryValues(calEntries.getEntryValues());

        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {
        Preference pref = findPreference(key);
        if (pref instanceof ListPreference) {
            ListPreference listPref = (ListPreference) pref;
            pref.setSummary(listPref.getEntry());
        }
        Intent updateIntent = new Intent(mContext, ClockWidgetProvider.class);
        updateIntent.putExtra(Constants.FORCE_REFRESH, true);
        mContext.sendBroadcast(updateIntent);
    }

    //===============================================================================================
    // Utility classes and supporting methods
    //===============================================================================================

    private static class CalendarEntries {
        private final CharSequence[] mEntries;
        private final CharSequence[] mEntryValues;
        private static Uri uri = CalendarContract.Calendars.CONTENT_URI;

        // Calendar projection array
        private static String[] projection = new String[] {
               CalendarContract.Calendars._ID,
               CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
        };

        // The indices for the projection array
        private static final int CALENDAR_ID_INDEX = 0;
        private static final int DISPLAY_NAME_INDEX = 1;

        static CalendarEntries findCalendars(Context context) {
            List<CharSequence> entries = new ArrayList<CharSequence>();
            List<CharSequence> entryValues = new ArrayList<CharSequence>();
            ContentResolver cr = context.getContentResolver();

            Cursor calendarCursor = cr.query(uri, projection, null, null, null);
            if (calendarCursor != null) {
                calendarCursor.moveToFirst();
                while (!calendarCursor.isAfterLast()) {
                    entryValues.add(calendarCursor.getString(CALENDAR_ID_INDEX));
                    entries.add(calendarCursor.getString(DISPLAY_NAME_INDEX));
                    calendarCursor.moveToNext();
                }
                calendarCursor.close();
            }

            return new CalendarEntries(entries, entryValues);
        }

        private CalendarEntries(List<CharSequence> mEntries, List<CharSequence> mEntryValues) {
            this.mEntries = mEntries.toArray(new CharSequence[mEntries.size()]);
            this.mEntryValues = mEntryValues.toArray(new CharSequence[mEntryValues.size()]);
        }

        CharSequence[] getEntries() {
            return mEntries;
        }

        CharSequence[] getEntryValues() {
            return mEntryValues;
        }
    }
}
