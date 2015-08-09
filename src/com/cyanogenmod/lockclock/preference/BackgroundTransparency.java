/*
 * Copyright (C) 2015 The CyanogenMod Project
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
import android.content.Context;
import android.content.SharedPreferences;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.UserHandle;
import android.preference.DialogPreference;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import com.cyanogenmod.lockclock.ClockWidgetProvider;
import com.cyanogenmod.lockclock.R;
import com.cyanogenmod.lockclock.misc.Constants;

/**
 * Preference for selection of background transparency for the clock widget
 */
public class BackgroundTransparency extends DialogPreference {
    private static final String TAG = "BackgroundTransparency";

    private final Context mContext;

    private TransparencySeekBar mBackgroundTransparency;

    private int mOriginalBackgroundTransparency;

    private final int mDefaultBackgroundTransparency;

    public BackgroundTransparency(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;

        mDefaultBackgroundTransparency = Constants.DEFAULT_BACKGROUND_TRANSPARENCY;
        setDialogLayoutResource(R.layout.background_transparency);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        SharedPreferences getPrefs = mContext.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
        mOriginalBackgroundTransparency = getPrefs.getInt(Constants.CLOCK_BACKGROUND_TRANSPARENCY,
                Constants.DEFAULT_BACKGROUND_TRANSPARENCY);

        SeekBar trans = (SeekBar) view.findViewById(R.id.background_transparency_seekbar);
        mBackgroundTransparency = new TransparencySeekBar(trans);

        mBackgroundTransparency.setProgress(mOriginalBackgroundTransparency);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        updateTransparency(positiveResult);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (getDialog() == null || !getDialog().isShowing()) {
            return superState;
        }

        // Save the dialog state
        final SavedState myState = new SavedState(superState);
        myState.originalBackgroundTransparency = mOriginalBackgroundTransparency;
        myState.currentBackgroundTransparency = mBackgroundTransparency.getProgress();

        // Restore the old state when the activity or dialog is being paused
        updateTransparency(false);

        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state == null || !state.getClass().equals(SavedState.class)) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());

        mOriginalBackgroundTransparency = myState.originalBackgroundTransparency;
        mBackgroundTransparency.setProgress(myState.currentBackgroundTransparency);

        updateTransparency(true);
    }

    private static class SavedState extends BaseSavedState {
        int originalBackgroundTransparency;
        int currentBackgroundTransparency;

        public SavedState(Parcelable superState) {
            super(superState);
        }

        public SavedState(Parcel source) {
            super(source);
            originalBackgroundTransparency = source.readInt();
            currentBackgroundTransparency = source.readInt();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(originalBackgroundTransparency);
            dest.writeInt(currentBackgroundTransparency);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {

            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    private void updateTransparency(boolean accept) {
        int trans = accept ? mBackgroundTransparency.getProgress() : mOriginalBackgroundTransparency;
        callChangeListener(trans);

        SharedPreferences getPrefs = mContext.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = getPrefs.edit();
        edit.putInt(Constants.CLOCK_BACKGROUND_TRANSPARENCY, trans);
        edit.apply();
    }

    private class TransparencySeekBar implements SeekBar.OnSeekBarChangeListener {
        private final SeekBar mSeekBar;

        private static final int MIN = 0;
        private static final int MAX = 255;
        private static final int STEP = 5;

        public TransparencySeekBar(SeekBar seekBar) {
            mSeekBar = seekBar;

            mSeekBar.setMax((MAX - MIN) / STEP);
            mSeekBar.setOnSeekBarChangeListener(this);
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser) {
                updateTransparency(true);
            }
        }

        public void setProgress(int progress) {
            int p = Math.max(progress, MIN) - MIN;
            mSeekBar.setProgress(Math.round((float) p / STEP));
        }

        public int getProgress() {
            return mSeekBar.getProgress() * STEP + MIN;
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            // Do nothing here
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            // Do nothing here
        }
    }
}
