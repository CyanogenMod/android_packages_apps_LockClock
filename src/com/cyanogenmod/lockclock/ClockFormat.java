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
		//a h:mm
		if(time.startsWith("a ")){
			time = time.replace("a ", "");
			String[] split = time.split(":");        
			hoursFormat = split[0];
			minutesFormat = split[1].replace("mm a", "mm");
			separatorFormat = ":";
		}
		//h:mm a or h:mma
		else if(time.matches("[h|H]{1,2}[:][m|M]{1,2}\\s[a]?") 
				|| time.matches("[h|H]{1,2}[:][m|M]{1,2}[a]?")){
			String[] split = time.split(":");        
			hoursFormat = split[0];
			minutesFormat = split[1].replace("mma", "mm");
			minutesFormat = split[1].replace("mm a", "mm");
			separatorFormat = ":";
		}
		//h.mm a
		else if(time.matches("[h|H]{1,2}[\\.][m|M]{1,2}\\s[a]?")){
			String[] split = time.split("\\.");        
			hoursFormat = split[0];
			minutesFormat = split[1].replace("mm a", "mm");
			separatorFormat = ".";
		}
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
