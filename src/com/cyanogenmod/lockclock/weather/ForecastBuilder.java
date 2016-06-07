/*
 * Copyright (C) 2013 David van Tonder
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use context file except in compliance with the License.
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

package com.cyanogenmod.lockclock.weather;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.cyanogenmod.lockclock.R;
import com.cyanogenmod.lockclock.misc.IconUtils;
import com.cyanogenmod.lockclock.misc.Preferences;
import static cyanogenmod.providers.WeatherContract.WeatherColumns.WindSpeedUnit.MPH;
import static cyanogenmod.providers.WeatherContract.WeatherColumns.WindSpeedUnit.KPH;
import static cyanogenmod.providers.WeatherContract.WeatherColumns.TempUnit.FAHRENHEIT;
import static cyanogenmod.providers.WeatherContract.WeatherColumns.TempUnit.CELSIUS;
import cyanogenmod.weather.CMWeatherManager;
import cyanogenmod.weather.WeatherInfo;
import cyanogenmod.weather.WeatherInfo.DayForecast;
import cyanogenmod.weather.util.WeatherUtils;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class ForecastBuilder {
    private static final String TAG = "ForecastBuilder";

    /**
     * This method is used to build the full current conditions and horizontal forecasts
     * panels
     *
     * @param context
     * @param w = the Weather info object that contains the forecast data
     * @return = a built view that can be displayed
     */
    @SuppressLint("SetJavaScriptEnabled")
    public static View buildFullPanel(Context context, int resourceId, WeatherInfo w) {

        // Load some basic settings
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE); 
        int color = Preferences.weatherFontColor(context);
        boolean invertLowHigh = Preferences.invertLowHighTemperature(context);
        final boolean useMetric = Preferences.useMetricUnits(context);

        //Make any conversion needed in case the data was not provided in the desired unit
        double temp = w.getTemperature();
        double todaysLow = w.getTodaysLow();
        double todaysHigh = w.getTodaysHigh();
        int tempUnit = w.getTemperatureUnit();
        if (tempUnit == FAHRENHEIT && useMetric) {
            temp = WeatherUtils.fahrenheitToCelsius(temp);
            todaysLow = WeatherUtils.fahrenheitToCelsius(todaysLow);
            todaysHigh = WeatherUtils.fahrenheitToCelsius(todaysHigh);
            tempUnit = CELSIUS;
        } else if (tempUnit == CELSIUS && !useMetric) {
            temp = WeatherUtils.celsiusToFahrenheit(temp);
            todaysLow = WeatherUtils.celsiusToFahrenheit(todaysLow);
            todaysHigh = WeatherUtils.celsiusToFahrenheit(todaysHigh);
            tempUnit = FAHRENHEIT;
        }

        double windSpeed = w.getWindSpeed();
        int windSpeedUnit = w.getWindSpeedUnit();
        if (windSpeedUnit == MPH && useMetric) {
            windSpeedUnit = KPH;
            windSpeed = Utils.milesToKilometers(windSpeed);
        } else if (windSpeedUnit == KPH && !useMetric) {
            windSpeedUnit = MPH;
            windSpeed = Utils.kilometersToMiles(windSpeed);
        }

        View view = inflater.inflate(resourceId, null);

        // Set the weather source
        TextView weatherSource = (TextView) view.findViewById(R.id.weather_source);
        final CMWeatherManager cmWeatherManager = CMWeatherManager.getInstance(context);
        String activeWeatherLabel = cmWeatherManager.getActiveWeatherServiceProviderLabel();
        weatherSource.setText(activeWeatherLabel != null ? activeWeatherLabel : "");

        // Set the current conditions
        // Weather Image
        ImageView weatherImage = (ImageView) view.findViewById(R.id.weather_image);
        String iconsSet = Preferences.getWeatherIconSet(context);
        weatherImage.setImageBitmap(IconUtils.getWeatherIconBitmap(context, iconsSet, color,
                w.getConditionCode(), IconUtils.getNextHigherDensity(context)));

        // Weather Condition
        TextView weatherCondition = (TextView) view.findViewById(R.id.weather_condition);
        weatherCondition.setText(Utils.resolveWeatherCondition(context, w.getConditionCode()));

        // Weather Temps
        TextView weatherTemp = (TextView) view.findViewById(R.id.weather_temp);
        weatherTemp.setText(WeatherUtils.formatTemperature(temp, tempUnit));

        // Humidity and Wind
        TextView weatherHumWind = (TextView) view.findViewById(R.id.weather_hum_wind);
        weatherHumWind.setText(Utils.formatHumidity(w.getHumidity()) + ", "
                + Utils.formatWindSpeed(context, windSpeed, windSpeedUnit) + " "
                + Utils.resolveWindDirection(context, w.getWindDirection()));

        // City
        TextView city = (TextView) view.findViewById(R.id.weather_city);
        city.setText(w.getCity());

        // Weather Update Time
        Date lastUpdate = new Date(w.getTimestamp());
        StringBuilder sb = new StringBuilder();
        sb.append(DateFormat.format("E", lastUpdate));
        sb.append(" ");
        sb.append(DateFormat.getTimeFormat(context).format(lastUpdate));
        TextView updateTime = (TextView) view.findViewById(R.id.update_time);
        updateTime.setText(sb.toString());
        updateTime.setVisibility(
                Preferences.showWeatherTimestamp(context) ? View.VISIBLE : View.GONE);

        // Weather Temps Panel additional items
        final String low = WeatherUtils.formatTemperature(todaysLow, tempUnit);
        final String high = WeatherUtils.formatTemperature(todaysHigh, tempUnit);
        TextView weatherLowHigh = (TextView) view.findViewById(R.id.weather_low_high);
        weatherLowHigh.setText(invertLowHigh ? high + " | " + low : low + " | " + high);

        // Get things ready
        LinearLayout forecastView = (LinearLayout) view.findViewById(R.id.forecast_view);
        final View progressIndicator = view.findViewById(R.id.progress_indicator);

        // Build the forecast panel
        if (buildSmallPanel(context, forecastView, w)) {
            // Success, hide the progress container
            progressIndicator.setVisibility(View.GONE);
        } else {
            // TODO: Display a text notifying the user that the forecast data is not available
            // rather than keeping the indicator spinning forever
        }

        return view;
    }

    /**
     * This method is used to build the small, horizontal forecasts panel
     * @param context
     * @param smallPanel = a horizontal linearlayout that will contain the forecasts
     * @param w = the Weather info object that contains the forecast data
     */
    public static boolean buildSmallPanel(Context context, LinearLayout smallPanel, WeatherInfo w) {
        if (smallPanel == null) {
          Log.d(TAG, "Invalid view passed");
          return false;
        }

        // Get things ready
        LayoutInflater inflater
              = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        int color = Preferences.weatherFontColor(context);
        boolean invertLowHigh = Preferences.invertLowHighTemperature(context);
        final boolean useMetric = Preferences.useMetricUnits(context);

        List<DayForecast> forecasts = w.getForecasts();
        if (forecasts == null || forecasts.size() <= 1) {
          smallPanel.setVisibility(View.GONE);
          return false;
        }

        TimeZone MyTimezone = TimeZone.getDefault();
        Calendar calendar = new GregorianCalendar(MyTimezone);
        int weatherTempUnit = w.getTemperatureUnit();
        int numForecasts = forecasts.size();
        int itemSidePadding = context.getResources().getDimensionPixelSize(
                R.dimen.forecast_item_padding_side);

        // Iterate through the Forecasts
        for (int count = 0; count < numForecasts; count ++) {
            DayForecast d = forecasts.get(count);

            // Load the views
            View forecastItem = inflater.inflate(R.layout.forecast_item, null);

            // The day of the week
            TextView day = (TextView) forecastItem.findViewById(R.id.forecast_day);
            day.setText(calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT,
                  Locale.getDefault()));
            calendar.roll(Calendar.DAY_OF_WEEK, true);

            // Weather Image
            ImageView image = (ImageView) forecastItem.findViewById(R.id.weather_image);
            String iconsSet = Preferences.getWeatherIconSet(context);
            final int resId = IconUtils.getWeatherIconResource(context, iconsSet,
                  d.getConditionCode());
            if (resId != 0) {
              image.setImageResource(resId);
            } else {
              image.setImageBitmap(IconUtils.getWeatherIconBitmap(context, iconsSet,
                      color, d.getConditionCode()));
            }

            // Temperatures
            double lowTemp = d.getLow();
            double highTemp = d.getHigh();
            int tempUnit = weatherTempUnit;
            if (weatherTempUnit == FAHRENHEIT && useMetric) {
                lowTemp = WeatherUtils.fahrenheitToCelsius(lowTemp);
                highTemp = WeatherUtils.fahrenheitToCelsius(highTemp);
                tempUnit = CELSIUS;
            } else if (weatherTempUnit == CELSIUS && !useMetric) {
                lowTemp = WeatherUtils.celsiusToFahrenheit(lowTemp);
                highTemp = WeatherUtils.celsiusToFahrenheit(highTemp);
                tempUnit = FAHRENHEIT;
            }
            String dayLow = WeatherUtils.formatTemperature(lowTemp, tempUnit);
            String dayHigh = WeatherUtils.formatTemperature(highTemp, tempUnit);
            TextView temps = (TextView) forecastItem.findViewById(R.id.weather_temps);
            temps.setText(invertLowHigh ? dayHigh + " " + dayLow : dayLow + " " + dayHigh);

            // Add the view
            smallPanel.addView(forecastItem,
                  new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            // Add a divider to the right for all but the last view
            if (count < numForecasts - 1) {
                View divider = new View(context);
                smallPanel.addView(divider, new LinearLayout.LayoutParams(
                        itemSidePadding, LinearLayout.LayoutParams.MATCH_PARENT));
            }
        }
        return true;
    }
}
