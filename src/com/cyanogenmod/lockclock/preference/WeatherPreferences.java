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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.AsyncTask;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.cyanogenmod.lockclock.ClockWidgetProvider;
import com.cyanogenmod.lockclock.R;
import com.cyanogenmod.lockclock.misc.Constants;
import com.cyanogenmod.lockclock.weather.YahooPlaceFinder;

public class WeatherPreferences extends PreferenceFragment implements
    OnPreferenceClickListener, OnSharedPreferenceChangeListener {
    private static final String TAG = "Weather Preferences";

    private CheckBoxPreference mUseCustomLoc;
    private CheckBoxPreference mUseMetric;
    private CheckBoxPreference mShowLocation;
    private CheckBoxPreference mShowTimestamp;
    private CheckBoxPreference mUseAlternateIcons;
    private EditTextPreference mCustomWeatherLoc;

    private Context mContext;
    private ContentResolver mResolver;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getPreferenceManager().setSharedPreferencesName(PREF_NAME);
        addPreferencesFromResource(R.xml.preferences_weather);
        mContext = getActivity();
        mResolver = mContext.getContentResolver();

        // Load the required settings from preferences
        SharedPreferences prefs = mContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        // Some preferences need to be set to a default value in code since we cannot do them in XML
        mUseMetric = (CheckBoxPreference) findPreference(Constants.WEATHER_USE_METRIC);
        mUseMetric.setChecked(prefs.getBoolean(Constants.WEATHER_USE_METRIC, true));
        mShowLocation = (CheckBoxPreference) findPreference(Constants.WEATHER_SHOW_LOCATION);
        mShowLocation.setChecked(prefs.getBoolean(Constants.WEATHER_SHOW_LOCATION, true));
        mShowTimestamp = (CheckBoxPreference) findPreference(Constants.WEATHER_SHOW_TIMESTAMP);
        mShowTimestamp.setChecked(prefs.getBoolean(Constants.WEATHER_SHOW_TIMESTAMP, true));
        mUseAlternateIcons = (CheckBoxPreference) findPreference(Constants.WEATHER_USE_ALTERNATE_ICONS);
        mUseAlternateIcons.setChecked(prefs.getBoolean(Constants.WEATHER_USE_ALTERNATE_ICONS, false));

        // Load items that need custom summaries etc.
        mUseCustomLoc = (CheckBoxPreference) findPreference(Constants.WEATHER_USE_CUSTOM_LOCATION);
        mUseCustomLoc.setChecked(prefs.getBoolean(Constants.WEATHER_USE_CUSTOM_LOCATION, false));
        mUseCustomLoc.setOnPreferenceClickListener(this);
        mCustomWeatherLoc = (EditTextPreference) findPreference(Constants.WEATHER_CUSTOM_LOCATION_STRING);
        mCustomWeatherLoc.setOnPreferenceClickListener(this);
        updateLocationSummary();

        // Show a warning if location manager is disabled and there is no custom location set
        if (!Settings.Secure.isLocationProviderEnabled(mResolver,
                LocationManager.NETWORK_PROVIDER)
                && !mUseCustomLoc.isChecked()) {
            showDialog();
        }

        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Preference pref = findPreference(key);
        if (pref instanceof ListPreference) {
            ListPreference listPref = (ListPreference) pref;
            pref.setSummary(listPref.getEntry());
        }
        Intent updateIntent = new Intent(mContext, ClockWidgetProvider.class);
        updateIntent.putExtra(Constants.FORCE_REFRESH, true);
        mContext.sendBroadcast(updateIntent);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mUseCustomLoc) {
            SharedPreferences prefs = mContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(Constants.WEATHER_USE_CUSTOM_LOCATION, mUseCustomLoc.isChecked()).apply();
            updateLocationSummary();
            return true;
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference == mCustomWeatherLoc) {
            SharedPreferences prefs = mContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String location = prefs.getString(Constants.WEATHER_CUSTOM_LOCATION_STRING, null);
            if (location != null) {
                mCustomWeatherLoc.getEditText().setText(location);
                mCustomWeatherLoc.getEditText().setSelection(location.length());
            }

            mCustomWeatherLoc.getDialog().findViewById(android.R.id.button1)
            .setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final ProgressDialog d = new ProgressDialog(mContext);
                    d.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                    d.setMessage(mContext.getString(R.string.weather_progress_title));
                    d.show();

                    final String location = mCustomWeatherLoc.getEditText().getText().toString();
                    final WeatherLocationTask task = new WeatherLocationTask() {
                        @Override
                        protected void onPostExecute(String woeid) {
                            if (woeid == null) {
                                Toast.makeText(mContext,
                                        mContext.getString(R.string.weather_retrieve_location_dialog_title),
                                        Toast.LENGTH_SHORT)
                                    .show();
                            } else {
                                mCustomWeatherLoc.setText(location);
                                mCustomWeatherLoc.setSummary(location);
                                mCustomWeatherLoc.getDialog().dismiss();

                                SharedPreferences prefs = mContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                                prefs.edit().putString(Constants.WEATHER_CUSTOM_LOCATION_STRING, location).apply();
                            }
                            d.dismiss();
                        }
                    };
                    task.execute(location);
                }
            });
            return true;
        }
        return false;
    }

    //===============================================================================================
    // Utility classes and supporting methods
    //===============================================================================================

    private class WeatherLocationTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... input) {
            String woeid = null;

            try {
                woeid = YahooPlaceFinder.GeoCode(mContext, input[0]);
            } catch (Exception e) {
                Log.e(TAG, "Could not resolve location", e);
            }

            return woeid;
        }
    }

    private void updateLocationSummary() {
        if (mUseCustomLoc.isChecked()) {
            SharedPreferences prefs = mContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String location = prefs.getString(Constants.WEATHER_CUSTOM_LOCATION_STRING,
                    getResources().getString(R.string.unknown));
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
                        Settings.Secure.setLocationProviderEnabled(mResolver,
                                LocationManager.NETWORK_PROVIDER, true);
                    }
                });
        builder.setNegativeButton(R.string.cancel, null);
        dialog = builder.create();
        dialog.show();
    }
}
