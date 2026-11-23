package de.audomo;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;

import org.json.*;

// Private one-shot draft shared by the dashboard, the library's back stack and the tile.
final class RecordingDraft {
    record Value(String name, CalendarNames.Event event) {}

    private final SharedPreferences prefs;
    private volatile Value current;

    RecordingDraft(Context context) {
        prefs = context.getSharedPreferences("next-recording", Context.MODE_PRIVATE);
        CalendarNames.Event event = null;
        try {
            if (prefs.contains("event")) {
                JSONObject json = new JSONObject(prefs.getString("event", ""));
                event =
                        new CalendarNames.Event(
                                json.getLong("id"),
                                json.getLong("calendar"),
                                json.optString("title"),
                                json.getLong("begin"),
                                json.getLong("end"),
                                json.optString("calendarName"),
                                json.optBoolean("allDay"));
            }
        } catch (JSONException damaged) {
            prefs.edit().remove("event").apply();
        }
        current = new Value(prefs.getString("name", ""), event);
    }

    Value get() {
        return current;
    }

    synchronized void name(String text) {
        current = new Value(text, current.event());
        prefs.edit().putString("name", text).apply();
    }

    synchronized void select(CalendarNames.Event event) {
        SharedPreferences.Editor editor = prefs.edit().remove("name").remove("event");
        if (event != null) {
            try {
                editor.putString(
                        "event",
                        new JSONObject()
                                .put("id", event.id())
                                .put("calendar", event.calendar())
                                .put("title", event.title())
                                .put("begin", event.begin())
                                .put("end", event.end())
                                .put("calendarName", event.calendarName())
                                .put("allDay", event.allDay())
                                .toString());
            } catch (JSONException error) {
                throw new IllegalArgumentException(error);
            }
        }
        current = new Value("", event);
        editor.apply();
    }

    synchronized void validate(Context context) {
        if (current.event() != null
                && (context.checkSelfPermission(Manifest.permission.READ_CALENDAR)
                                != PackageManager.PERMISSION_GRANTED
                        || !CalendarNames.ids(context).contains(current.event().calendar()))) {
            current = new Value(current.name(), null);
            prefs.edit().remove("event").apply();
        }
    }

    synchronized void consume(Value started) {
        if (current == started) select(null);
    }
}
