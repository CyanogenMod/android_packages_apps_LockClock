/******************************************************************************
 * Class       : YahooWeatherHelper.java                                                                  *
 * Parser helper for Yahoo                                                    *
 *                                                                            *
 * Version     : v1.0                                                         *
 * Date        : May 06, 2011                                                 *
 * Copyright (c)-2011 DatNQ some right reserved                               *
 * You can distribute, modify or what ever you want but WITHOUT ANY WARRANTY  *
 * Be honest by keep credit of this file                                      *
 *                                                                            *
 * If you have any concern, feel free to contact with me via email, i will    *
 * check email in free time                                                   * 
 * Email: nguyendatnq@gmail.com                                               *
 * ---------------------------------------------------------------------------*
 * Modification Logs:                                                         *
 *   KEYCHANGE  DATE          AUTHOR   DESCRIPTION                            *
 * ---------------------------------------------------------------------------*
 *    -------   May 06, 2011  DatNQ    Create new                             *
 ******************************************************************************/
/*
 * Modification into Android-internal WeatherXmlParser.java
 * Copyright (C) 2012 The AOKP Project
 */

package com.cyanogenmod.lockclock.weather;

import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import android.content.Context;
import android.util.Log;

public class WeatherXmlParser {

    protected static final String TAG = "WeatherXmlParser";

    /** Yahoo attributes */
    private static final String PARAM_YAHOO_LOCATION = "yweather:location";
    private static final String PARAM_YAHOO_UNIT = "yweather:units";
    private static final String PARAM_YAHOO_ATMOSPHERE = "yweather:atmosphere";
    private static final String PARAM_YAHOO_CONDITION = "yweather:condition";
    private static final String PARAM_YAHOO_WIND = "yweather:wind";
    private static final String PARAM_YAHOO_FORECAST = "yweather:forecast";

    private static final String ATT_YAHOO_CITY = "city";
    private static final String ATT_YAHOO_TEMP = "temp";
    private static final String ATT_YAHOO_CODE = "code";
    private static final String ATT_YAHOO_TEMP_UNIT = "temperature";
    private static final String ATT_YAHOO_HUMIDITY = "humidity";
    private static final String ATT_YAHOO_TEXT = "text";
    private static final String ATT_YAHOO_DATE = "date";
    private static final String ATT_YAHOO_SPEED = "speed";
    private static final String ATT_YAHOO_DIRECTION = "direction";
    private static final String ATT_YAHOO_TODAY_HIGH = "high";
    private static final String ATT_YAHOO_TODAY_LOW = "low";

    private Context mContext;

    public WeatherXmlParser(Context context) {
        mContext = context;
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
        if (value == null || value.equals("")) {
            return Float.NaN;
        }
        return Float.parseFloat(value);
    }

    private int getIntForAttribute(Element root, String tagName, String attributeName)
            throws NumberFormatException {
        String value = getValueForAttribute(root, tagName, attributeName);
        if (value == null || value.equals("")) {
            return -1;
        }
        return Integer.parseInt(value);
    }

    public WeatherInfo parseWeatherResponse(Document docWeather) {
        if (docWeather == null) {
            Log.e(TAG, "Invalid doc weather");
            return null;
        }

        try {
            Element root = docWeather.getDocumentElement();
            root.normalize();

            WeatherInfo w = new WeatherInfo(mContext,
                    /* city */ getValueForAttribute(root, PARAM_YAHOO_LOCATION, ATT_YAHOO_CITY),
                    /* forecastDate */ getValueForAttribute(root, PARAM_YAHOO_CONDITION, ATT_YAHOO_DATE),
                    /* condition */ getValueForAttribute(root, PARAM_YAHOO_CONDITION, ATT_YAHOO_TEXT),
                    /* conditionCode */ getIntForAttribute(root, PARAM_YAHOO_CONDITION, ATT_YAHOO_CODE),
                    /* temperature */ getFloatForAttribute(root, PARAM_YAHOO_CONDITION, ATT_YAHOO_TEMP),
                    /* low */ getFloatForAttribute(root, PARAM_YAHOO_FORECAST, ATT_YAHOO_TODAY_LOW),
                    /* high */ getFloatForAttribute(root, PARAM_YAHOO_FORECAST, ATT_YAHOO_TODAY_HIGH),
                    /* tempUnit */ getValueForAttribute(root, PARAM_YAHOO_UNIT, ATT_YAHOO_TEMP_UNIT),
                    /* humidity */ getFloatForAttribute(root, PARAM_YAHOO_ATMOSPHERE, ATT_YAHOO_HUMIDITY),
                    /* wind */ getFloatForAttribute(root, PARAM_YAHOO_WIND, ATT_YAHOO_SPEED),
                    /* windDir */ getIntForAttribute(root, PARAM_YAHOO_WIND, ATT_YAHOO_DIRECTION),
                    /* speedUnit */ getValueForAttribute(root, PARAM_YAHOO_UNIT, ATT_YAHOO_SPEED),
                    System.currentTimeMillis());

            Log.d(TAG, "Weather updated: " + w);
            return w;
        } catch (Exception e) {
            Log.e(TAG, "Couldn't parse Yahoo weather XML", e);
            return null;
        }
    }

    public String parsePlaceFinderResponse(String response) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new InputSource(new StringReader(response)));

            NodeList resultNodes = doc.getElementsByTagName("Result");

            Node resultNode = resultNodes.item(0);
            NodeList attrsList = resultNode.getChildNodes();

            for (int i = 0; i < attrsList.getLength(); i++) {
                Node node = attrsList.item(i);
                Node firstChild = node.getFirstChild();
                if ("woeid".equalsIgnoreCase(node.getNodeName()) && firstChild != null) {
                    return firstChild.getNodeValue();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Couldn't parse Yahoo place finder XML", e);
        }
        return null;
    }
}
