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

package com.cyanogenmod.chronus.preference;

import static com.cyanogenmod.chronus.misc.Constants.PREF_NAME;

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
import android.os.Handler;
import android.os.Message;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import com.cyanogenmod.chronus.ClockWidgetProvider;
import com.cyanogenmod.chronus.misc.Constants;
import com.cyanogenmod.chronus.weather.YahooPlaceFinder;
import com.cyanogenmod.chronus.R;

public class WeatherPreferences extends PreferenceFragment implements
    OnPreferenceClickListener, OnSharedPreferenceChangeListener {
    private static final String TAG = "Weather Preferences";

    private static final int WEATHER_CHECK = 0;

    private CheckBoxPreference mUseCustomLoc;
    private CheckBoxPreference mUseMetric;
    private CheckBoxPreference mShowLocation;
    private CheckBoxPreference mShowTimestamp;
    private EditTextPreference mCustomWeatherLoc;

    private Context mContext;
    private ContentResolver mResolver;
    private ProgressDialog mProgressDialog;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getPreferenceManager().setSharedPreferencesName(PREF_NAME);
        addPreferencesFromResource(R.xml.preferences_weather);
        mContext = getActivity();
        mResolver = mContext.getContentResolver();

        // Load the required settings from preferences
        SharedPreferences prefs = mContext.getSharedPreferences(PREF_NAME, Context.MODE_MULTI_PROCESS);

        // Some preferences need to be set to a default value in code since we cannot do them in XML
        mUseMetric = (CheckBoxPreference) findPreference(Constants.WEATHER_USE_METRIC);
        mUseMetric.setChecked(prefs.getBoolean(Constants.WEATHER_USE_METRIC, true));
        mShowLocation = (CheckBoxPreference) findPreference(Constants.WEATHER_SHOW_LOCATION);
        mShowLocation.setChecked(prefs.getBoolean(Constants.WEATHER_SHOW_LOCATION, true));
        mShowTimestamp = (CheckBoxPreference) findPreference(Constants.WEATHER_SHOW_TIMESTAMP);
        mShowTimestamp.setChecked(prefs.getBoolean(Constants.WEATHER_SHOW_TIMESTAMP, true));

        // Load items that need custom summaries etc.
        mUseCustomLoc = (CheckBoxPreference) findPreference(Constants.WEATHER_USE_CUSTOM_LOCATION);
        mCustomWeatherLoc = (EditTextPreference) findPreference(Constants.WEATHER_CUSTOM_LOCATION_STRING);
        updateLocationSummary();
        mCustomWeatherLoc.setOnPreferenceClickListener(this);

        // Show a warning if location manager is disabled and there is no custom location set
        if (!Settings.Secure.isLocationProviderEnabled(mResolver,
                LocationManager.NETWORK_PROVIDER)
                && !mUseCustomLoc.isChecked()) {
            showDialog();
        }

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

    @Override
    public boolean onPreferenceClick(Preference preference) {

        if (preference == mCustomWeatherLoc) {
            SharedPreferences prefs = mContext.getSharedPreferences(PREF_NAME, Context.MODE_MULTI_PROCESS);
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

    //===============================================================================================
    // Utility classes and supporting methods
    //===============================================================================================

    private void updateLocationSummary() {
        if (mUseCustomLoc.isChecked()) {
            SharedPreferences prefs = mContext.getSharedPreferences(PREF_NAME, Context.MODE_MULTI_PROCESS);
            String location = prefs.getString(Constants.WEATHER_CUSTOM_LOCATION_STRING, 
                    getResources().getString(R.string.unknown));
            mCustomWeatherLoc.setSummary(location);
        } else {
            mCustomWeatherLoc.setSummary(R.string.weather_geolocated);
        }
    }

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
                    SharedPreferences prefs = mContext.getSharedPreferences(PREF_NAME, Context.MODE_MULTI_PROCESS);
                    prefs.edit().putString(Constants.WEATHER_CUSTOM_LOCATION_STRING, cLoc).apply();
                    mCustomWeatherLoc.setSummary(cLoc);
                    mCustomWeatherLoc.getDialog().dismiss();
                }
                mProgressDialog.dismiss();
                break;
            }
        }
    };

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
