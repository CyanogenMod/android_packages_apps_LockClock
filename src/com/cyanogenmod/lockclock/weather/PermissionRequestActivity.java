/*
 * Copyright (C) 2016 The CyanogenMod Project
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

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.ResultReceiver;

public class PermissionRequestActivity extends Activity {

    private static final String RESULT_RECEIVER_EXTRA = "result_receiver";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    private ResultReceiver mResultReceiver;
    private int mResult = RESULT_CANCELED;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (hasLocationPermission()) {
            finish();
            return;
        }

        mResultReceiver = getIntent().getParcelableExtra(RESULT_RECEIVER_EXTRA);
        if (mResultReceiver == null) {
            finish();
            return;
        }

        String[] permissions = new String[]{Manifest.permission.ACCESS_COARSE_LOCATION};
        requestPermissions(permissions, LOCATION_PERMISSION_REQUEST_CODE);
    }

    public boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mResult = RESULT_OK;
            }
        }
        finish();
    }

    @Override
    public void finish() {
        if (mResultReceiver != null) {
            mResultReceiver.send(mResult, null);
        }
        super.finish();
    }
}
