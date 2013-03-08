/*
 * Copyright (C) 2013 The CyanogenMod Project (DvTonder)
 * Portions Copyright (C) 2012 The Android Open Source Project
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

package com.cyanogenmod.lockclock.misc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CalendarInfo {
    private List<EventInfo> mEventsList;
    private long mFollowingEventStart;

    public CalendarInfo() {
        mEventsList = new ArrayList<EventInfo>(Constants.MAX_CALENDAR_ITEMS);
        mFollowingEventStart = 0;
    }

    public List<EventInfo> getEvents() {
        return mEventsList;
    }

    public boolean hasEvents() {
        return !mEventsList.isEmpty();
    }

    public void clearEvents() {
        mEventsList.clear();
        mFollowingEventStart = 0;
    }

    public void addEvent(EventInfo event) {
        mEventsList.add(event);
        Collections.sort(mEventsList);
    }

    public void setFollowingEventStart(long start) {
        mFollowingEventStart = start;
    }

    public long getFollowingEventStart() {
        return mFollowingEventStart;
    }

    //===============================================================================================
    // Calendar event information class
    //===============================================================================================
    /**
     * EventInfo is a class that represents an event in the widget
     */
    public static class EventInfo implements Comparable<EventInfo> {
        public String description;
        public String title;

        public long id;
        public long start;
        public long end;
        public boolean allDay;

        public EventInfo() {
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("EventInfo [Title=");
            builder.append(title);
            builder.append(", id=");
            builder.append(id);
            builder.append(", description=");
            builder.append(description);
            builder.append("]");
            return builder.toString();
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (allDay ? 1231 : 1237);
            result = prime * result + (int) (id ^ (id >>> 32));
            result = prime * result + (int) (end ^ (end >>> 32));
            result = prime * result + (int) (start ^ (start >>> 32));
            result = prime * result + ((title == null) ? 0 : title.hashCode());
            result = prime * result + ((description == null) ? 0 : description.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            EventInfo other = (EventInfo) obj;
            if (id != other.id)
                return false;
            if (allDay != other.allDay)
                return false;
            if (end != other.end)
                return false;
            if (start != other.start)
                return false;
            if (title == null) {
                if (other.title != null)
                    return false;
            } else if (!title.equals(other.title))
                return false;
            if (description == null) {
                if (other.description != null)
                    return false;
            } else if (!description.equals(other.description)) {
                return false;
            }
            return true;
        }

        @Override
        public int compareTo(EventInfo other) {
            if (start < other.start) {
                return -1;
            } else if (start > other.start) {
                return 1;
            }

            if (allDay && !other.allDay) {
                return -1;
            } else if (!allDay && other.allDay) {
                return 1;
            }

            return 0;
        }
    }
}
