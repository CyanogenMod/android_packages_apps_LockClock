package com.cyanogenmod.lockclock.weather;

import android.content.Context;
import android.content.res.Resources;
import com.cyanogenmod.lockclock.R;

public final class Utils {

    public static String resolveWindDirection(Context context, float windDirection) {
        int resId;

        if (windDirection < 0) resId = R.string.unknown;
        else if (windDirection < 23) resId = R.string.weather_N;
        else if (windDirection < 68) resId = R.string.weather_NE;
        else if (windDirection < 113) resId = R.string.weather_E;
        else if (windDirection < 158) resId = R.string.weather_SE;
        else if (windDirection < 203) resId = R.string.weather_S;
        else if (windDirection < 248) resId = R.string.weather_SW;
        else if (windDirection < 293) resId = R.string.weather_W;
        else if (windDirection < 338) resId = R.string.weather_NW;
        else resId = R.string.weather_N;

        return context.getString(resId);
    }

    public static String resolveWeatherCondition(Context context, int conditionCode) {
        final Resources res = context.getResources();
        final int resId = res.getIdentifier("weather_" + conditionCode, "string",
                context.getPackageName());
        if (resId != 0) {
            return res.getString(resId);
        }
        return "";
    }

}
