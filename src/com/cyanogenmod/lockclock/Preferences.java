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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.database.Cursor;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import com.cyanogenmod.lockclock.misc.Constants;
import com.cyanogenmod.lockclock.weather.*;

import java.util.ArrayList;
import java.util.List;

public class Preferences extends PreferenceActivity implements
    OnPreferenceChangeListener, OnPreferenceClickListener, OnSharedPreferenceChangeListener {
    private static final String TAG = "Preferences";

    protected static final String PREF_NAME = "LockClock";

    private static final int LOC_WARNING = 101;
    private static final int WEATHER_CHECK = 0;

    private CheckBoxPreference mUseCustomLoc;
    private ListPreference mWeatherSyncInterval;
    private EditTextPreference mCustomWeatherLoc;
    private MultiSelectListPreference mCalendarList;
    private ListPreference mCalendarLookahead;
    private ListPreference mCalendarShowLocation;
    private ListPreference mCalendarShowDescription;

    private Context mContext;
    private ContentResolver mResolver;
    private ProgressDialog mProgressDialog;

    @SuppressWarnings("deprecation")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getPreferenceManager().setSharedPreferencesName(PREF_NAME);
        addPreferencesFromResource(R.xml.widget_prefs);
        mContext = this;
        mResolver = getContentResolver();

        // Load the required settings from preferences
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_MULTI_PROCESS);

        mUseCustomLoc = (CheckBoxPreference) findPreference(Constants.WEATHER_USE_CUSTOM_LOCATION);
        //mUseCustomLoc.setChecked(prefs.getInt(Constants.WEATHER_USE_CUSTOM_LOCATION, 0) == 1);
        mCustomWeatherLoc = (EditTextPreference) findPreference(Constants.WEATHER_CUSTOM_LOCATION_STRING);
        updateLocationSummary();
        mCustomWeatherLoc.setOnPreferenceClickListener(this);

        mWeatherSyncInterval = (ListPreference) findPreference(Constants.WEATHER_REFRESH_INTERVAL);
        int weatherInterval = prefs.getInt(Constants.WEATHER_REFRESH_INTERVAL, 60);
        mWeatherSyncInterval.setValue(String.valueOf(weatherInterval));
        mWeatherSyncInterval.setSummary(mapUpdateValue(weatherInterval));
        mWeatherSyncInterval.setOnPreferenceChangeListener(this);

        if (!Settings.Secure.isLocationProviderEnabled(mResolver,
                LocationManager.NETWORK_PROVIDER)
                && !mUseCustomLoc.isChecked()) {
            showDialog(LOC_WARNING);
        }

        // Calendar items
        mCalendarList = (MultiSelectListPreference) findPreference(Constants.CALENDAR_LIST);
        mCalendarList.setDefaultValue(prefs.getString(Constants.CALENDAR_LIST, null));
        mCalendarList.setOnPreferenceChangeListener(this);
        CalendarEntries calEntries = CalendarEntries.findCalendars(this);
        mCalendarList.setEntries(calEntries.getEntries());
        mCalendarList.setEntryValues(calEntries.getEntryValues());

        mCalendarLookahead = (ListPreference) findPreference(Constants.CALENDAR_LOOKAHEAD);
        long calendarLookahead = prefs.getLong(Constants.CALENDAR_LOOKAHEAD, 10800000);
        mCalendarLookahead.setValue(String.valueOf(calendarLookahead));
        mCalendarLookahead.setSummary(mapLookaheadValue(calendarLookahead));
        mCalendarLookahead.setOnPreferenceChangeListener(this);

        mCalendarShowLocation = (ListPreference) findPreference(Constants.CALENDAR_SHOW_LOCATION);
        int calendarShowLocation = prefs.getInt(Constants.CALENDAR_SHOW_LOCATION, 0);
        mCalendarShowLocation.setValue(String.valueOf(calendarShowLocation));
        mCalendarShowLocation.setSummary(mapMetadataValue(calendarShowLocation));
        mCalendarShowLocation.setOnPreferenceChangeListener(this);

        mCalendarShowDescription = (ListPreference) findPreference(Constants.CALENDAR_SHOW_DESCRIPTION);
        int calendarShowDescription = prefs.getInt(Constants.CALENDAR_SHOW_DESCRIPTION, 0);
        mCalendarShowDescription.setValue(String.valueOf(calendarShowDescription));
        mCalendarShowDescription.setSummary(mapMetadataValue(calendarShowDescription));
        mCalendarShowDescription.setOnPreferenceChangeListener(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    private void updateLocationSummary() {
        if (mUseCustomLoc.isChecked()) {
            SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_MULTI_PROCESS);
            String location = prefs.getString(Constants.WEATHER_CUSTOM_LOCATION_STRING, 
                    getResources().getString(R.string.unknown));
            mCustomWeatherLoc.setSummary(location);
        } else {
            mCustomWeatherLoc.setSummary(R.string.weather_geolocated);
        }
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
            return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_MULTI_PROCESS);

        if (preference == mWeatherSyncInterval) {
            int newVal = Integer.parseInt((String) newValue);
            prefs.edit().putInt(Constants.WEATHER_REFRESH_INTERVAL, newVal).apply();
            mWeatherSyncInterval.setValue((String) newValue);
            mWeatherSyncInterval.setSummary(mapUpdateValue(newVal));
            preference.setSummary(mapUpdateValue(newVal));

        } else if (preference == mCalendarShowLocation) {
            int calendarShowLocation = Integer.valueOf((String) newValue);
            prefs.edit().putInt(Constants.CALENDAR_SHOW_LOCATION, calendarShowLocation).apply();
            mCalendarShowLocation.setSummary(mapMetadataValue(calendarShowLocation));
            return true;

        } else if (preference == mCalendarShowDescription) {
            int calendarShowDescription = Integer.valueOf((String) newValue);
            prefs.edit().putInt(Constants.CALENDAR_SHOW_DESCRIPTION, calendarShowDescription).apply();
            mCalendarShowDescription.setSummary(mapMetadataValue(calendarShowDescription));
            return true;

        } else if (preference == mCalendarLookahead) {
            long calendarLookahead = Long.valueOf((String) newValue);
            prefs.edit().putLong(Constants.CALENDAR_LOOKAHEAD, calendarLookahead).apply();
            mCalendarLookahead.setSummary(mapLookaheadValue(calendarLookahead));
            return true;

        } else if (preference == mCalendarList) {
            String calendars = newValue.toString();
            prefs.edit().putString(Constants.CALENDAR_LIST, calendars).apply();
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {

        if (preference == mCustomWeatherLoc) {
            SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_MULTI_PROCESS);
            String location = prefs.getString(Constants.WEATHER_CUSTOM_LOCATION_STRING, "");
            mCustomWeatherLoc.getEditText().setText(location);
            mCustomWeatherLoc.getEditText().setSelection(location.length());

            mCustomWeatherLoc.getDialog().findViewById(android.R.id.button1)
            .setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mProgressDialog = new ProgressDialog(mContext);
                    mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                    mProgressDialog.setMessage(mContext.getString(R.string.weather_progress_title));
                    mProgressDialog.show();
                    new Thread(new Runnable(){
                        @Override
                        public void run() {
                            String woeid = null;
                            try {
                                woeid = YahooPlaceFinder.GeoCode(mContext,
                                        mCustomWeatherLoc.getEditText().getText().toString());
                            } catch (Exception e) {
                            }
                            Message msg = Message.obtain();
                            msg.what = WEATHER_CHECK;
                            msg.obj = woeid;
                            mHandler.sendMessage(msg);
                        }
                    }).start();
                }
            });
            return true;
        }
        return false;
    }

    /**
     * Utility classes and supporting methods
     */

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case WEATHER_CHECK:
                if (msg.obj == null) {
                    Toast.makeText(mContext, mContext.getString(R.string.weather_retrieve_location_dialog_title),
                            Toast.LENGTH_SHORT).show();
                } else {
                    String cLoc = mCustomWeatherLoc.getEditText().getText().toString();
                    mCustomWeatherLoc.setText(cLoc);
                    SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_MULTI_PROCESS);
                    prefs.edit().putString(Constants.WEATHER_CUSTOM_LOCATION_STRING, cLoc).apply();
                    mCustomWeatherLoc.setSummary(cLoc);
                    mCustomWeatherLoc.getDialog().dismiss();
                }
                mProgressDialog.dismiss();
                break;
            }
        }
    };

    private String mapUpdateValue(Integer time) {
        Resources resources = mContext.getResources();

        String[] timeNames = resources.getStringArray(R.array.weather_interval_entries);
        String[] timeValues = resources.getStringArray(R.array.weather_interval_values);

        for (int i = 0; i < timeValues.length; i++) {
            if (Integer.decode(timeValues[i]).equals(time)) {
                return timeNames[i];
            }
        }
        return mContext.getString(R.string.unknown);
    }

    @Override
    public Dialog onCreateDialog(int dialogId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        final Dialog dialog;

        switch (dialogId) {
            case LOC_WARNING:
                builder.setTitle(R.string.weather_retrieve_location_dialog_title);
                builder.setMessage(R.string.weather_retrieve_location_dialog_message);
                builder.setCancelable(false);
                builder.setPositiveButton(R.string.weather_retrieve_location_dialog_enable_button,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                Settings.Secure.setLocationProviderEnabled(mResolver,
                                        LocationManager.NETWORK_PROVIDER, true);
                            }
                        });
                builder.setNegativeButton(R.string.cancel, null);
                dialog = builder.create();
                break;
            default:
                dialog = null;
        }
        return dialog;
    }

    private String mapMetadataValue(Integer value) {
        Resources resources = mContext.getResources();

        String[] names = resources.getStringArray(R.array.calendar_show_event_metadata_entries);
        String[] values = resources.getStringArray(R.array.calendar_show_event_metadata_values);

        for (int i = 0; i < values.length; i++) {
            if (Integer.decode(values[i]).equals(value)) {
                return names[i];
            }
        }
        return mContext.getString(R.string.unknown);
    }

    private String mapLookaheadValue(Long value) {
        Resources resources = mContext.getResources();

        String[] names = resources.getStringArray(R.array.calendar_lookahead_entries);
        String[] values = resources.getStringArray(R.array.calendar_lookahead_values);

        for (int i = 0; i < values.length; i++) {
            if (Long.decode(values[i]).equals(value)) {
                return names[i];
            }
        }

        return mContext.getString(R.string.unknown);
    }

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

        static CalendarEntries findCalendars(Activity activity) {
            List<CharSequence> entries = new ArrayList<CharSequence>();
            List<CharSequence> entryValues = new ArrayList<CharSequence>();

            Cursor calendarCursor = activity.managedQuery(uri, projection, null, null, null);
            if (calendarCursor.moveToFirst()) {
                do {
                    entryValues.add(calendarCursor.getString(CALENDAR_ID_INDEX));
                    entries.add(calendarCursor.getString(DISPLAY_NAME_INDEX));
                } while (calendarCursor.moveToNext());
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

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {
        Intent updateIntent = new Intent(mContext, ClockWidgetProvider.class);
        sendBroadcast(updateIntent);
    }
}
