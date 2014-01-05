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
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.cyanogenmod.lockclock.R;
import com.cyanogenmod.lockclock.misc.Preferences;
import com.cyanogenmod.lockclock.weather.WeatherProvider.LocationResult;

import java.util.HashSet;
import java.util.List;

public class CustomLocationPreference extends EditTextPreference {
    public CustomLocationPreference(Context context) {
        super(context);
    }
    public CustomLocationPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    public CustomLocationPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);

        final AlertDialog d = (AlertDialog) getDialog();
        Button okButton = d.getButton(DialogInterface.BUTTON_POSITIVE);

        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CustomLocationPreference.this.onClick(d, DialogInterface.BUTTON_POSITIVE);
                new WeatherLocationTask(d, getEditText().getText().toString()).execute();
            }
        });
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        String location = Preferences.customWeatherLocationCity(getContext());
        if (location != null) {
            getEditText().setText(location);
            getEditText().setSelection(location.length());
        }
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        // we handle persisting the selected location below, so pretend cancel
        super.onDialogClosed(false);
    }

    private class WeatherLocationTask extends AsyncTask<Void, Void, List<LocationResult>> {
        private Dialog mDialog;
        private ProgressDialog mProgressDialog;
        private String mLocation;

        public WeatherLocationTask(Dialog dialog, String location) {
            mDialog = dialog;
            mLocation = location;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            final Context context = getContext();

            mProgressDialog = new ProgressDialog(context);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mProgressDialog.setMessage(context.getString(R.string.weather_progress_title));
            mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    cancel(true);
                }
            });
            mProgressDialog.show();
        }

        @Override
        protected List<LocationResult> doInBackground(Void... input) {
            return Preferences.weatherProvider(getContext()).getLocations(mLocation);
        }

        @Override
        protected void onPostExecute(List<LocationResult> results) {
            super.onPostExecute(results);

            final Context context = getContext();

            if (results == null || results.isEmpty()) {
                Toast.makeText(context,
                        context.getString(R.string.weather_retrieve_location_dialog_title),
                        Toast.LENGTH_SHORT)
                        .show();
            } else if (results.size() > 1) {
                handleResultDisambiguation(results);
            } else {
                applyLocation(results.get(0));
            }
            mProgressDialog.dismiss();
        }

        private void handleResultDisambiguation(final List<LocationResult> results) {
            CharSequence[] items = buildItemList(results);
            new AlertDialog.Builder(getContext())
                    .setSingleChoiceItems(items, -1, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            applyLocation(results.get(which));
                            dialog.dismiss();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .setTitle(R.string.weather_select_location)
                    .show();
        }

        private CharSequence[] buildItemList(List<LocationResult> results) {
            boolean needCountry = false, needPostal = false;
            String countryId = results.get(0).countryId;
            HashSet<String> postalIds = new HashSet<String>();

            for (LocationResult result : results) {
                if (!TextUtils.equals(result.countryId, countryId)) {
                    needCountry = true;
                }
                String postalId = result.countryId + "##" + result.city;
                if (postalIds.contains(postalId)) {
                    needPostal = true;
                }
                postalIds.add(postalId);
                if (needPostal && needCountry) {
                    break;
                }
            }

            int count = results.size();
            CharSequence[] items = new CharSequence[count];
            for (int i = 0; i < count; i++) {
                LocationResult result = results.get(i);
                StringBuilder builder = new StringBuilder();
                if (needPostal && result.postal != null) {
                    builder.append(result.postal).append(" ");
                }
                builder.append(result.city);
                if (needCountry) {
                    String country = result.country != null
                            ? result.country : result.countryId;
                    builder.append(" (").append(country).append(")");
                }
                items[i] = builder.toString();
            }
            return items;
        }

        private void applyLocation(final LocationResult result) {
            Preferences.setCustomWeatherLocationId(getContext(), result.id);
            setText(result.city);
            mDialog.dismiss();
        }
    }
}
