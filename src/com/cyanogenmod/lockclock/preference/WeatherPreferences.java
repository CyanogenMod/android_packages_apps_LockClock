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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.cyanogenmod.lockclock.ClockWidgetProvider;
import com.cyanogenmod.lockclock.R;
import com.cyanogenmod.lockclock.misc.Constants;
import com.cyanogenmod.lockclock.misc.Preferences;
import com.cyanogenmod.lockclock.weather.WeatherUpdateService;

public class WeatherPreferences extends PreferenceFragment implements
        SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "WeatherPreferences";

    private static final String[] LOCATION_PREF_KEYS = new String[] {
        Constants.WEATHER_USE_CUSTOM_LOCATION,
        Constants.WEATHER_CUSTOM_LOCATION_CITY
    };
    private static final String[] WEATHER_REFRESH_KEYS = new String[] {
        Constants.SHOW_WEATHER,
        Constants.WEATHER_REFRESH_INTERVAL
    };

    private CheckBoxPreference mUseCustomLoc;
    private EditTextPreference mCustomWeatherLoc;
    private ListPreference mFontColor;
    private ListPreference mTimestampFontColor;
    private CheckBoxPreference mUseMetric;

    private Context mContext;
    private ContentResolver mResolver;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getPreferenceManager().setSharedPreferencesName(Constants.PREF_NAME);
        addPreferencesFromResource(R.xml.preferences_weather);
        mContext = getActivity();
        mResolver = mContext.getContentResolver();

        // Load items that need custom summaries etc.
        mUseCustomLoc = (CheckBoxPreference) findPreference(Constants.WEATHER_USE_CUSTOM_LOCATION);
        mCustomWeatherLoc = (EditTextPreference) findPreference(Constants.WEATHER_CUSTOM_LOCATION_CITY);

        mFontColor = (ListPreference) findPreference(Constants.WEATHER_FONT_COLOR);
        mTimestampFontColor = (ListPreference) findPreference(Constants.WEATHER_TIMESTAMP_FONT_COLOR);

        mUseMetric = (CheckBoxPreference) findPreference(Constants.WEATHER_USE_METRIC);

        // Show a warning if location manager is disabled and there is no custom location set
        if (!Settings.Secure.isLocationProviderEnabled(mResolver,
                LocationManager.NETWORK_PROVIDER)
                && !mUseCustomLoc.isChecked()) {
            showDialog();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        updateLocationSummary();
        updateFontColorsSummary();
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Preference pref = findPreference(key);
        if (pref instanceof ListPreference) {
            ListPreference listPref = (ListPreference) pref;
            pref.setSummary(listPref.getEntry());
        }

        if (pref == mUseCustomLoc || pref == mCustomWeatherLoc) {
            updateLocationSummary();
        }

        boolean needWeatherUpdate = false;
        boolean forceWeatherUpdate = false;

        if (pref == mUseMetric) {
            // The display format of the temperatures have changed
            // Force a weather update to refresh the display
            forceWeatherUpdate = true;
        }

        for (String k : LOCATION_PREF_KEYS) {
            if (TextUtils.equals(key, k)) {
                // location pref has changed -> clear out location id cache
                Preferences.setCachedLocationId(mContext, null);
                forceWeatherUpdate = true;
                break;
            }
        }

        for (String k : WEATHER_REFRESH_KEYS) {
            if (TextUtils.equals(key, k)) {
                needWeatherUpdate = true;
                break;
            }
        }

        if (Constants.DEBUG) {
            Log.v(TAG, "Preference " + key + " changed, need update " +
                    needWeatherUpdate + " force update "  + forceWeatherUpdate);
        }

        if (Preferences.showWeather(mContext) && (needWeatherUpdate || forceWeatherUpdate)) {
            Intent updateIntent = new Intent(mContext, WeatherUpdateService.class);
            if (forceWeatherUpdate) {
                updateIntent.setAction(WeatherUpdateService.ACTION_FORCE_UPDATE);
            }
            mContext.startService(updateIntent);
        }

        Intent updateIntent = new Intent(mContext, ClockWidgetProvider.class);
        mContext.sendBroadcast(updateIntent);
    }

    //===============================================================================================
    // Utility classes and supporting methods
    //===============================================================================================

    private void updateLocationSummary() {
        if (mUseCustomLoc.isChecked()) {
            String location = Preferences.customWeatherLocationCity(mContext);
            if (location == null) {
                location = getResources().getString(R.string.unknown);
            }
            mCustomWeatherLoc.setSummary(location);
        } else {
            mCustomWeatherLoc.setSummary(R.string.weather_geolocated);
        }
    }

    private void showDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        final Dialog dialog;

        // Build and show the dialog
        builder.setTitle(R.string.weather_retrieve_location_dialog_title);
        builder.setMessage(R.string.weather_retrieve_location_dialog_message);
        builder.setCancelable(false);
        builder.setPositiveButton(R.string.weather_retrieve_location_dialog_enable_button,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        mContext.startActivity(intent);
                    }
                });
        builder.setNegativeButton(R.string.cancel, null);
        dialog = builder.create();
        dialog.show();
    }

    private void updateFontColorsSummary() {
        if (mFontColor != null) {
            mFontColor.setSummary(mFontColor.getEntry());
        }
        if (mTimestampFontColor != null) {
            mTimestampFontColor.setSummary(mTimestampFontColor.getEntry());
        }
    }
}
