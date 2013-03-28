/*
 * Copyright (C) 2012 The AOKP Project
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

package com.cyanogenmod.lockclock.weather;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;

import com.cyanogenmod.lockclock.R;
import com.cyanogenmod.lockclock.misc.WidgetUtils;

import java.text.DecimalFormat;
import java.util.Date;

public class WeatherInfo {
    private static final DecimalFormat sNoDigitsFormat = new DecimalFormat("0");

    private Context mContext;

    private String city;
    private String forecastDate;
    private String condition;
    private int conditionCode;
    private float temperature;
    private float lowTemperature;
    private float highTemperature;
    private String tempUnit;
    private float humidity;
    private float wind;
    private int windDirection;
    private String speedUnit;
    private long timestamp;

    public WeatherInfo(Context context,
            String city, String fdate, String condition, int conditionCode,
            float temp, float low, float high, String tempUnit, float humidity,
            float wind, int windDir, String speedUnit, long timestamp) {
        this.mContext = context;
        this.city = city;
        this.forecastDate = fdate;
        this.condition = condition;
        this.conditionCode = conditionCode;
        this.humidity = humidity;
        this.wind = wind;
        this.windDirection = windDir;
        this.speedUnit = speedUnit;
        this.timestamp = timestamp;
        this.temperature = temp;
        this.lowTemperature = low;
        this.highTemperature = high;
        this.tempUnit = tempUnit;
    }

    public int getConditionResource() {
        final Resources res = mContext.getResources();
        final int resId = res.getIdentifier("weather2_" + conditionCode, "drawable", mContext.getPackageName());
        if (resId != 0) {
            return resId;
        }
        return R.drawable.weather2_na;
    }

    public Bitmap getConditionBitmap(int color) {
        final Resources res = mContext.getResources();
        int resId = res.getIdentifier("weather_" + conditionCode, "drawable", mContext.getPackageName());
        if (resId == 0) {
            resId = R.drawable.weather_na;
        }
        return WidgetUtils.getOverlaidBitmap(mContext, resId, color);
    }

    public String getCity() {
        return city;
    }

    public String getCondition() {
        final Resources res = mContext.getResources();
        final int resId = res.getIdentifier("weather_" + conditionCode, "string", mContext.getPackageName());
        if (resId != 0) {
            return res.getString(resId);
        }
        return condition;
    }

    public Date getTimestamp() {
        return new Date(timestamp);
    }

    private String getFormattedValue(float value, String unit) {
        if (Float.isNaN(highTemperature)) {
            return "-";
        }
        return sNoDigitsFormat.format(value) + unit;
    }

    public String getFormattedTemperature() {
        return getFormattedValue(temperature, "°" + tempUnit);
    }

    public String getFormattedLow() {
        return getFormattedValue(lowTemperature, "°");
    }

    public String getFormattedHigh() {
        return getFormattedValue(highTemperature, "°");
    }

    public String getFormattedHumidity() {
        return getFormattedValue(humidity, "%");
    }

    public String getFormattedWindSpeed() {
        return getFormattedValue(wind, speedUnit);
    }

    public String getWindDirection() {
        int resId;

        if (windDirection < 0) {
            return "";
        }

        if (windDirection < 23) resId = R.string.weather_N;
        else if (windDirection < 68) resId = R.string.weather_NE;
        else if (windDirection < 113) resId = R.string.weather_E;
        else if (windDirection < 158) resId = R.string.weather_SE;
        else if (windDirection < 203) resId = R.string.weather_S;
        else if (windDirection < 248) resId = R.string.weather_SW;
        else if (windDirection < 293) resId = R.string.weather_W;
        else if (windDirection < 338) resId = R.string.weather_NW;
        else resId = R.string.weather_N;

        return mContext.getString(resId);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("WeatherInfo for ");
        builder.append(city);
        builder.append("@ ");
        builder.append(getTimestamp());
        builder.append(": ");
        builder.append(getCondition());
        builder.append("(");
        builder.append(conditionCode);
        builder.append("), temperature ");
        builder.append(getFormattedTemperature());
        builder.append(", low ");
        builder.append(getFormattedLow());
        builder.append(", high ");
        builder.append(getFormattedHigh());
        builder.append(", humidity ");
        builder.append(getFormattedHumidity());
        builder.append(", wind ");
        builder.append(getFormattedWindSpeed());
        builder.append(" at ");
        builder.append(getWindDirection());
        return builder.toString();
    }

    public String toSerializedString() {
        StringBuilder builder = new StringBuilder();
        builder.append(city).append('|');
        builder.append(forecastDate).append('|');
        builder.append(condition).append('|');
        builder.append(conditionCode).append('|');
        builder.append(temperature).append('|');
        builder.append(lowTemperature).append('|');
        builder.append(highTemperature).append('|');
        builder.append(tempUnit).append('|');
        builder.append(humidity).append('|');
        builder.append(wind).append('|');
        builder.append(windDirection).append('|');
        builder.append(speedUnit).append('|');
        builder.append(timestamp);
        return builder.toString();
    }

    public static WeatherInfo fromSerializedString(Context context, String input) {
        if (input == null) {
            return null;
        }

        String[] parts = input.split("\\|");
        if (parts == null || parts.length != 13) {
            return null;
        }

        int conditionCode, windDirection;
        long timestamp;
        float temperature, low, high, humidity, wind;

        try {
            conditionCode = Integer.parseInt(parts[3]);
            temperature = Float.parseFloat(parts[4]);
            low = Float.parseFloat(parts[5]);
            high = Float.parseFloat(parts[6]);
            humidity = Float.parseFloat(parts[8]);
            wind = Float.parseFloat(parts[9]);
            windDirection = Integer.parseInt(parts[10]);
            timestamp = Long.parseLong(parts[12]);
        } catch (NumberFormatException e) {
            return null;
        }

        return new WeatherInfo(context,
                /* city */ parts[0], /* date */ parts[1], /* condition */ parts[2],
                conditionCode, temperature, low, high, /* tempUnit */ parts[7],
                humidity, wind, windDirection, /* speedUnit */ parts[11], timestamp);
    }
}
