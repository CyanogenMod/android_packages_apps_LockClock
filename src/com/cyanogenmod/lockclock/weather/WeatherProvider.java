/*
 * Copyright (C) 2013 The CyanogenMod Project
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

import android.location.Location;

import java.util.List;

public interface WeatherProvider {
    public class LocationResult {
        public String id;
        public String city;
        public String postal;
        public String countryId;
        public String country;
    }

    List<LocationResult> getLocations(String input);

    WeatherInfo getWeatherInfo(String id, String localizedCityName, boolean metricUnits);

    WeatherInfo getWeatherInfo(Location location, boolean metricUnits);

    int getNameResourceId();
}
