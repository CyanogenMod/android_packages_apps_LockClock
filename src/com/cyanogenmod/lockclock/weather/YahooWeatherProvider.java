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

import android.content.Context;
import android.location.Location;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.cyanogenmod.lockclock.misc.Preferences;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class YahooWeatherProvider implements WeatherProvider {
    private static final String TAG = "YahooWeatherProvider";

    private static final String URL_YAHOO_API_WEATHER =
            "http://weather.yahooapis.com/forecastrss?w=%s&u=%s";
    private static final String YAHOO_API_BASE_URL =
            "http://query.yahooapis.com/v1/public/yql?q=" +
            Uri.encode("select woeid, postal, admin1, admin2, admin3, " +
                    "locality1, locality2, country from geo.places where text =");

    private Context mContext;

    public YahooWeatherProvider(Context context) {
        mContext = context;
    }

    @Override
    public List<LocationResult> getLocations(String input) {
        String locale = mContext.getResources().getConfiguration().locale.getCountry();
        String params = "\"" + input + "\" and lang = \"" + locale + "\"";
        String url = YAHOO_API_BASE_URL + Uri.encode(params);
        NodeList nodes = prepareNodeList(url);

        if (nodes == null) {
            return null;
        }

        ArrayList<LocationResult> results = new ArrayList<LocationResult>();

        for (int i = 0; i < nodes.getLength(); i++) {
            LocationResult result = parsePlace(nodes.item(i).getChildNodes(), true);
            if (result != null) {
                results.add(result);
            }
        }

        return results;
    }

    public WeatherInfo getWeatherInfo(String id, String localizedCityName) {
        String unit = Preferences.useMetricUnits(mContext) ? "c" : "f";
        Document doc = prepareDocument(String.format(URL_YAHOO_API_WEATHER, id, unit));

        if (doc == null) {
            return null;
        }

        Element root = doc.getDocumentElement();
        root.normalize();

        String city = localizedCityName != null
                ? localizedCityName : getValueForAttribute(root, "yweather:location", "city");

        try {
            WeatherInfo w = new WeatherInfo(mContext, id, city,
                    /* forecastDate */ getValueForAttribute(root, "yweather:condition", "date"),
                    /* condition */ getValueForAttribute(root, "yweather:condition", "text"),
                    /* conditionCode */ getIntForAttribute(root, "yweather:condition", "code"),
                    /* temperature */ getFloatForAttribute(root, "yweather:condition", "temp"),
                    /* low */ getFloatForAttribute(root, "yweather:forecast", "low"),
                    /* high */ getFloatForAttribute(root, "yweather:forecast", "high"),
                    /* tempUnit */ getValueForAttribute(root, "yweather:units", "temperature"),
                    /* humidity */ getFloatForAttribute(root, "yweather:atmosphere", "humidity"),
                    /* wind */ getFloatForAttribute(root, "yweather:wind", "speed"),
                    /* windDir */ getIntForAttribute(root, "yweather:wind", "direction"),
                    /* speedUnit */ getValueForAttribute(root, "yweather:units", "speed"),
                    System.currentTimeMillis());

            Log.d(TAG, "Weather updated: " + w);
            return w;
        } catch (NumberFormatException e) {
            Log.w(TAG, "Weather condition data is invalid.", e);
        }

        return null;
    }

    public WeatherInfo getWeatherInfo(Location location) {
        String formattedCoordinates = String.format(Locale.US, "\"%f %f\"",
                location.getLatitude(), location.getLongitude());
        String url = YAHOO_API_BASE_URL + Uri.encode(formattedCoordinates);
        NodeList nodes = prepareNodeList(url);
        if (nodes == null) {
            return null;
        }

        for (int i = 0; i < nodes.getLength(); i++) {
            LocationResult result = parsePlace(nodes.item(i).getChildNodes(), false);
            if (result != null) {
                Log.d(TAG, "Looking up weather for " + result.city + " (" + result.id + ")");
                WeatherInfo info = getWeatherInfo(result.id, result.city);
                if (info != null) {
                    // cache the result for potential reuse
                    // (the placefinder service API is rate limited)
                    Preferences.setCachedLocationId(mContext, result.id);
                    return info;
                }
            }
        }

        return null;
    }

    private LocationResult parsePlace(NodeList place, boolean requireFullDataSet) {
        LocationResult result = new LocationResult();
        int localityScore = -1;

        for (int j = 0; j < place.getLength(); j++) {
            Node node = place.item(j);
            Node firstChild = node.getFirstChild();
            String name = node.getNodeName();
            String text = firstChild != null ? firstChild.getNodeValue() : null;

            if (text == null) {
                continue;
            }

            if (name.equals("woeid")) {
                result.id = text;
            } else if (name.equals("country")) {
                result.country = text;
                result.countryId = node.getAttributes().getNamedItem("code").getNodeValue();
            } else if (name.equals("postal")) {
                result.postal = text;
            } else if (name.equals("locality2") && localityScore < 5) {
                result.city = text;
                localityScore = 5;
            } else if (name.equals("locality1") && localityScore < 4) {
                result.city = text;
                localityScore = 4;
            } else if (name.equals("admin3") && localityScore < 3) {
                result.city = text;
                localityScore = 3;
            } else if (name.equals("admin2") && localityScore < 2) {
                result.city = text;
                localityScore = 2;
            } else if (name.equals("admin1") && localityScore < 1) {
                result.city = text;
                localityScore = 1;
            }
        }

        if (result.id == null || result.city == null) {
            return null;
        }
        if (requireFullDataSet && (result.countryId == null || result.postal == null)) {
            return null;
        }

        return result;
    }

    private String getValueForAttribute(Element root, String tagName, String attributeName) {
        NamedNodeMap node = root.getElementsByTagName(tagName).item(0).getAttributes();
        if (node == null) {
            return null;
        }
        return node.getNamedItem(attributeName).getNodeValue();
    }

    private float getFloatForAttribute(Element root, String tagName, String attributeName)
            throws NumberFormatException {
        String value = getValueForAttribute(root, tagName, attributeName);
        return TextUtils.isEmpty(value) ? Float.NaN : Float.parseFloat(value);
    }

    private int getIntForAttribute(Element root, String tagName, String attributeName)
            throws NumberFormatException {
        String value = getValueForAttribute(root, tagName, attributeName);
        return TextUtils.isEmpty(value) ? -1 : Integer.parseInt(value);
    }

    private Document prepareDocument(String url) {
        try {
            String response = HttpRetriever.retrieve(url);
            if (response != null) {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();
                return db.parse(new InputSource(new StringReader(response)));
            }
        } catch (ParserConfigurationException e) {
            Log.d(TAG, "Couldn't set up XML parser", e);
        } catch (SAXException e) {
            Log.d(TAG, "Couldn't parse Yahoo response XML", e);
        } catch (IOException e) {
            Log.d(TAG, "Couldn't parse Yahoo response XML", e);
        }

        return null;
    }

    private NodeList prepareNodeList(String url) {
        Document doc = prepareDocument(url);
        if (doc != null) {
            return doc.getElementsByTagName("place");
        }
        return null;
    }
};
