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

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.preference.PreferenceActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import com.cyanogenmod.lockclock.R;

import java.util.List;

public class Preferences extends PreferenceActivity {

    // only used when adding a new widget
    private int mNewWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.preferences_headers, target);

        // Check if triggered from adding a new widget
        Intent intent = getIntent();
        if (intent != null
                && AppWidgetManager.ACTION_APPWIDGET_CONFIGURE.equals(intent.getAction())) {
            mNewWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mNewWidgetId);
            // See http://code.google.com/p/android/issues/detail?id=2539
            myResult(RESULT_CANCELED);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.menu_done:
                myResult(RESULT_OK);
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onBackPressed() {
        // If launched from the configure intent, signal RESULT_OK
        myResult(RESULT_OK);
        super.onBackPressed();
    }

    private void myResult(int result) {
        if (mNewWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(result, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mNewWidgetId));
        }
    }
}
