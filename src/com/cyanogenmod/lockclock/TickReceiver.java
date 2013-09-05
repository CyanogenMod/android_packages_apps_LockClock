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

package com.cyanogenmod.lockclock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class TickReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		// Forces a refresh of the widget when the time has ticked
		String action = intent.getAction();
		if(Intent.ACTION_TIME_TICK.equals(action)){
			Intent refreshIntent = new Intent(context, ClockWidgetProvider.class);
	        refreshIntent.setAction(ClockWidgetService.ACTION_REFRESH);
	        context.sendBroadcast(refreshIntent);
		}
	}

}
