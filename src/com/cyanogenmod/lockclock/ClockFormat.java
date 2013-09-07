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

import android.content.Context;
import android.text.format.DateFormat;

public class ClockFormat {
	
	private String hoursFormat;
	private String minutesFormat;
	private String separatorFormat;
	
	public ClockFormat(Context context){
		String time;
		//Get time string
		if(!DateFormat.is24HourFormat(context)){
			time = context.getString(R.string.twelve_hour_time_format);
		}else {
			time = context.getString(R.string.twenty_four_hour_time_format);
		}
		splitStrings(time);
	}
	
	private void splitStrings(String time){	
		String[] split;

		if(time.contains("a")){
			// Remove AM/PM if present
			time = time.replaceAll("a ", "");
			time = time.replace("a", "");
			time = time.replaceAll(" a", "");
		} 
		if(time.contains(":")){
			split = time.split(":");
			separatorFormat = ":";
		}
		else{
			split = time.split("\\.");
			separatorFormat = ".";

		}    
		hoursFormat = split[0].trim();
		minutesFormat = split[1].trim();		
	}
	
	public String getSeparatorFormat(){
		return separatorFormat;
	}

	public String getHoursFormat(){		
		return hoursFormat;
	}
	
	public String getMinutesFormat(){
		return minutesFormat;
	}
}
