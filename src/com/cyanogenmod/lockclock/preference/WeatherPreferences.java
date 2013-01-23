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
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.cyanogenmod.lockclock.ClockWidgetProvider;
import com.cyanogenmod.lockclock.R;
import com.cyanogenmod.lockclock.misc.Constants;
import com.cyanogenmod.lockclock.misc.Preferences;
import com.cyanogenmod.lockclock.weather.WeatherUpdateService;
import com.cyanogenmod.lockclock.weather.YahooPlaceFinder;

public class WeatherPreferences extends PreferenceFragment implements
        OnPreferenceClickListener, OnSharedPreferenceChangeListener {
    private static final String TAG = "WeatherPreferences";

    private static final String[] LOCATION_PREF_KEYS = new String[] {
        Constants.WEATHER_USE_CUSTOM_LOCATION,
        Constants.WEATHER_CUSTOM_LOCATION_STRING
    };
    private static final String[] WEATHER_REFRESH_KEYS = new String[] {
        Constants.SHOW_WEATHER,
        Constants.WEATHER_REFRESH_INTERVAL
    };

    private CheckBoxPreference mUseCustomLoc;
    private EditTextPreference mCustomWeatherLoc;

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
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
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

        if (pref == mUseCustomLoc) {
            updateLocationSummary();
        }

        boolean needWeatherUpdate = false;
        boolean forceWeatherUpdate = false;

        for (String k : LOCATION_PREF_KEYS) {
            if (TextUtils.equals(key, k)) {
                // location pref has changed -> clear out woeid cache
                Preferences.setCachedWoeid(mContext, null);
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

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference == mCustomWeatherLoc) {
            String location = com.cyanogenmod.lockclock.misc.Preferences.customWeatherLocation(mContext);
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
                woeid = YahooPlaceFinder.geoCode(mContext, input[0]);
            } catch (Exception e) {
                Log.e(TAG, "Could not resolve location", e);
            }

            return woeid;
        }
    }

    private void updateLocationSummary() {
        if (mUseCustomLoc.isChecked()) {
            String location = com.cyanogenmod.lockclock.misc.Preferences.customWeatherLocation(mContext);
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
                        Settings.Secure.setLocationProviderEnabled(mResolver,
                                LocationManager.NETWORK_PROVIDER, true);
                    }
                });
        builder.setNegativeButton(R.string.cancel, null);
        dialog = builder.create();
        dialog.show();
    }
}
