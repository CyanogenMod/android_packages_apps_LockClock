
package com.cyanogenmod.lockclock;

import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;

import com.cyanogenmod.lockclock.weather.*;

import org.w3c.dom.Document;

import java.io.IOException;

public class MainActivity extends Activity implements View.OnClickListener {

    private static String TAG = "LockClock";
    private boolean DEBUG = true;

    private RelativeLayout mWeatherPanel, mWeatherTempsPanel;
    private TextView mWeatherCity, mWeatherCondition, mWeatherLowHigh, mWeatherTemp, mWeatherUpdateTime;
    private ImageView mWeatherImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.digital_appwidget);

        // Weather panel
        mWeatherPanel = (RelativeLayout) findViewById(R.id.weather_panel);
        mWeatherCity = (TextView) findViewById(R.id.weather_city);
        mWeatherCondition = (TextView) findViewById(R.id.weather_condition);
        mWeatherImage = (ImageView) findViewById(R.id.weather_image);
        mWeatherTemp = (TextView) findViewById(R.id.weather_temp);
        mWeatherLowHigh = (TextView) findViewById(R.id.weather_low_high);
        mWeatherUpdateTime = (TextView) findViewById(R.id.update_time);
        mWeatherTempsPanel = (RelativeLayout) findViewById(R.id.weather_temps_panel);

        // Hide Weather panel view until we know we need to show it.
        if (mWeatherPanel != null) {
            mWeatherPanel.setVisibility(View.GONE);
            mWeatherPanel.setOnClickListener(this);
        }

        refreshWeather();
    }

    @Override
    public void onResume() {
        super.onResume();
        //refreshWeather();
    }

    public void onClick(View v) {
        if (v == mWeatherPanel) {
            // Indicate we are refreshing
            if (mWeatherCondition != null) {
                mWeatherCondition.setText(R.string.weather_refreshing);
            }

            if (!mWeatherRefreshing) {
                mHandler.sendEmptyMessage(QUERY_WEATHER);
            }
        }
    }

    protected void refresh() {
    }

    /*
     * CyanogenMod Lock screen Weather related functionality
     */
    private static final String URL_YAHOO_API_WEATHER = "http://weather.yahooapis.com/forecastrss?w=%s&u=";
    private static WeatherInfo mWeatherInfo = new WeatherInfo();
    private static final int QUERY_WEATHER = 0;
    private static final int UPDATE_WEATHER = 1;
    private boolean mWeatherRefreshing;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case QUERY_WEATHER:
                Thread queryWeather = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        LocationManager locationManager = (LocationManager) getBaseContext().
                                getSystemService(Context.LOCATION_SERVICE);
                        final ContentResolver resolver = getBaseContext().getContentResolver();
                        boolean useCustomLoc = true; //Settings.System.getInt(resolver,
                                //Settings.System.WEATHER_USE_CUSTOM_LOCATION, 0) == 1;
                        String customLoc = "Toronto, Canada"; //Settings.System.getString(resolver,
                                    //Settings.System.WEATHER_CUSTOM_LOCATION);
                        String woeid = null;

                        // custom location
                        if (customLoc != null && useCustomLoc) {
                            try {
                                woeid = YahooPlaceFinder.GeoCode(getBaseContext().getApplicationContext(), customLoc);
                                if (DEBUG)
                                    Log.d(TAG, "Yahoo location code for " + customLoc + " is " + woeid);
                            } catch (Exception e) {
                                Log.e(TAG, "ERROR: Could not get Location code");
                                e.printStackTrace();
                            }
                        // network location
                        } else {
                            Criteria crit = new Criteria();
                            crit.setAccuracy(Criteria.ACCURACY_COARSE);
                            String bestProvider = locationManager.getBestProvider(crit, true);
                            Location loc = null;
                            if (bestProvider != null) {
                                loc = locationManager.getLastKnownLocation(bestProvider);
                            } else {
                                loc = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
                            }
                            try {
                                woeid = YahooPlaceFinder.reverseGeoCode(getBaseContext(), loc.getLatitude(),
                                        loc.getLongitude());
                                if (DEBUG)
                                    Log.d(TAG, "Yahoo location code for current geolocation is " + woeid);
                            } catch (Exception e) {
                                Log.e(TAG, "ERROR: Could not get Location code");
                                e.printStackTrace();
                            }
                        }
                        if (DEBUG) {
                            Log.d(TAG, "Location code is " + woeid);
                        }
                        WeatherInfo w = null;
                        if (woeid != null) {
                            try {
                                w = parseXml(getDocument(woeid));
                            } catch (Exception e) {
                            }
                        }
                        Message msg = Message.obtain();
                        msg.what = UPDATE_WEATHER;
                        msg.obj = w;
                        mHandler.sendMessage(msg);
                    }
                });
                mWeatherRefreshing = true;
                queryWeather.setPriority(Thread.MIN_PRIORITY);
                queryWeather.start();
                break;
            case UPDATE_WEATHER:
                WeatherInfo w = (WeatherInfo) msg.obj;
                if (w != null) {
                    mWeatherRefreshing = false;
                    setWeatherData(w);
                    mWeatherInfo = w;
                } else {
                    mWeatherRefreshing = false;
                    if (mWeatherInfo.temp.equals(WeatherInfo.NODATA)) {
                        setNoWeatherData();
                    } else {
                        setWeatherData(mWeatherInfo);
                    }
                }
                break;
            }
        }
    };

    /**
     * Reload the weather forecast
     */
    private void refreshWeather() {
        final ContentResolver resolver = getBaseContext().getContentResolver();
        boolean showWeather = true; //Settings.System.getInt(resolver,Settings.System.LOCKSCREEN_WEATHER, 0) == 1;

        if (showWeather) {
            final long interval = 5; //Settings.System.getLong(resolver,
                    //Settings.System.WEATHER_UPDATE_INTERVAL, 60); // Default to hourly
            boolean manualSync = (interval == 0);
            if (!manualSync && (((System.currentTimeMillis() - mWeatherInfo.last_sync) / 60000) >= interval)) {
                if (!mWeatherRefreshing) {
                    mHandler.sendEmptyMessage(QUERY_WEATHER);
                }
            } else if (manualSync && mWeatherInfo.last_sync == 0) {
                setNoWeatherData();
            } else {
                setWeatherData(mWeatherInfo);
            }
        } else {
            // Hide the Weather panel view
            if (mWeatherPanel != null) {
                mWeatherPanel.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Display the weather information
     * @param w
     */
    private void setWeatherData(WeatherInfo w) {
        final ContentResolver resolver = getBaseContext().getContentResolver();
        final Resources res = getBaseContext().getResources();
        boolean showLocation = true; //Settings.System.getInt(resolver,
                //Settings.System.WEATHER_SHOW_LOCATION, 1) == 1;
        boolean showTimestamp = true; //Settings.System.getInt(resolver,
                //Settings.System.WEATHER_SHOW_TIMESTAMP, 1) == 1;
        boolean invertLowhigh = false; //Settings.System.getInt(resolver,
                //Settings.System.WEATHER_INVERT_LOWHIGH, 0) == 1;

        if (mWeatherPanel != null) {
            if (mWeatherImage != null) {
                String conditionCode = w.condition_code;
                String condition_filename = "weather_" + conditionCode;
                int resID = res.getIdentifier(condition_filename, "drawable",
                        getBaseContext().getPackageName());

                if (DEBUG)
                    Log.d("Weather", "Condition:" + conditionCode + " ID:" + resID);

                if (resID != 0) {
                    mWeatherImage.setImageDrawable(res.getDrawable(resID));
                } else {
                    mWeatherImage.setImageResource(R.drawable.weather_na);
                }
            }
            if (mWeatherCity != null) {
                mWeatherCity.setText(w.city);
                mWeatherCity.setVisibility(showLocation ? View.VISIBLE : View.GONE);
            }
            if (mWeatherCondition != null && !mWeatherRefreshing) {
                mWeatherCondition.setText(w.condition);
                mWeatherCondition.setVisibility(View.VISIBLE);
            }
            if (mWeatherUpdateTime != null) {
                long now = System.currentTimeMillis();
                if (now - w.last_sync < 60000) {
                    mWeatherUpdateTime.setText(R.string.weather_last_sync_just_now);
                } else {
                    mWeatherUpdateTime.setText(DateUtils.getRelativeTimeSpanString(
                            w.last_sync, now, DateUtils.MINUTE_IN_MILLIS));
                }
                mWeatherUpdateTime.setVisibility(showTimestamp ? View.VISIBLE : View.GONE);
            }
            if (mWeatherTempsPanel != null && mWeatherTemp != null && mWeatherLowHigh != null) {
                mWeatherTemp.setText(w.temp);
                mWeatherLowHigh.setText(invertLowhigh ? w.high + " | " + w.low : w.low + " | " + w.high);
                mWeatherTempsPanel.setVisibility(View.VISIBLE);
            }

            // Show the Weather panel view
            mWeatherPanel.setVisibility(View.VISIBLE);
        }
    }

    /**
     * There is no data to display, display 'empty' fields and the
     * 'Tap to reload' message
     */
    private void setNoWeatherData() {

        if (mWeatherPanel != null) {
            if (mWeatherImage != null) {
                mWeatherImage.setImageResource(R.drawable.weather_na);
            }
            if (mWeatherCity != null) {
                mWeatherCity.setText(R.string.weather_no_data);
                mWeatherCity.setVisibility(View.VISIBLE);
            }
            if (mWeatherCondition != null && !mWeatherRefreshing) {
                mWeatherCondition.setText(R.string.weather_tap_to_refresh);
            }
            if (mWeatherUpdateTime != null) {
                mWeatherUpdateTime.setVisibility(View.GONE);
            }
            if (mWeatherTempsPanel != null ) {
                mWeatherTempsPanel.setVisibility(View.GONE);
            }

            // Show the Weather panel view
            mWeatherPanel.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Get the weather forecast XML document for a specific location
     * @param woeid
     * @return
     */
    private Document getDocument(String woeid) {
        try {
            boolean celcius = true; //Settings.System.getInt(getBaseContext().getContentResolver(),
                    //Settings.System.WEATHER_USE_METRIC, 1) == 1;
            String urlWithDegreeUnit;

            if (celcius) {
                urlWithDegreeUnit = URL_YAHOO_API_WEATHER + "c";
            } else {
                urlWithDegreeUnit = URL_YAHOO_API_WEATHER + "f";
            }

            return new HttpRetriever().getDocumentFromURL(String.format(urlWithDegreeUnit, woeid));
        } catch (IOException e) {
            Log.e(TAG, "Error querying Yahoo weather");
        }

        return null;
    }

    /**
     * Parse the weather XML document
     * @param wDoc
     * @return
     */
    private WeatherInfo parseXml(Document wDoc) {
        try {
            return new WeatherXmlParser(getBaseContext()).parseWeatherResponse(wDoc);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing Yahoo weather XML document");
            e.printStackTrace();
        }
        return null;
    }
}
