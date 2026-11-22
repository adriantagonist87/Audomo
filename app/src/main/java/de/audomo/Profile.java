package de.audomo;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;

record Profile(String name, int rate, int kbps, int gain, String mic, String echo, String noise) {
    static final String SYSTEM = "Gerätestandard";
    static final List<String> INPUTS = List.of("Hinten", "Vorne", SYSTEM);
    static final List<String> EFFECTS = List.of(SYSTEM, "Ein", "Aus");
    static final List<Integer> RATES = List.of(16000, 24000, 48000);
    static final List<Integer> BITRATES = List.of(16, 24, 32, 48, 64, 80, 96, 128);
    static final List<Profile> BUILTINS =
            List.of(
                    standard("Besprechung", 48),
                    standard("Hohe Qualität", 80),
                    standard("Kompakt", 32));

    static Profile standard(String name, int kbps) {
        return new Profile(name, 48000, kbps, 5, "Hinten", "Ein", "Aus");
    }

    Profile {
        if (name.isBlank()
                || name.length() > 64
                || !RATES.contains(rate)
                || !BITRATES.contains(kbps)
                || gain < -20
                || gain > 20
                || !INPUTS.contains(mic)
                || !EFFECTS.contains(echo)
                || !EFFECTS.contains(noise))
            throw new IllegalArgumentException("Ungültige Profileinstellung");
    }

    JSONObject json() throws JSONException {
        return new JSONObject()
                .put("name", name)
                .put("rate", rate)
                .put("kbps", kbps)
                .put("gain", gain)
                .put("mic", mic)
                .put("echo", echo)
                .put("noise", noise);
    }

    static Profile parse(JSONObject value) throws JSONException {
        return new Profile(
                value.getString("name"),
                value.getInt("rate"),
                value.getInt("kbps"),
                value.getInt("gain"),
                value.getString("mic"),
                value.getString("echo"),
                value.getString("noise"));
    }

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("options", Context.MODE_PRIVATE);
    }

    static List<Profile> profiles(Context context) {
        List<Profile> all = new ArrayList<>(BUILTINS);
        try {
            JSONArray own = new JSONArray(prefs(context).getString("profiles", "[]"));
            for (int i = 0; i < own.length(); i++) all.add(parse(own.getJSONObject(i)));
        } catch (JSONException e) {
            throw new IllegalStateException("Gespeicherte Profile nicht lesbar", e);
        }
        return all;
    }

    static Profile selected(Context context) {
        SharedPreferences prefs = prefs(context);
        int old = prefs.getInt("kbps", 48);
        String selected =
                prefs.getString(
                        "selected",
                        old == 80 ? "Hohe Qualität" : old == 32 ? "Kompakt" : "Besprechung");
        for (Profile p : profiles(context)) if (p.name.equals(selected)) return p;
        return BUILTINS.get(0);
    }

    static void save(Context context, Profile value, String former) throws JSONException {
        JSONArray own = new JSONArray();
        for (Profile p : profiles(context)) {
            if (p.name.equals(value.name) && !p.name.equals(former))
                throw new IllegalArgumentException("Profilname schon belegt");
            if (!BUILTINS.contains(p) && !p.name.equals(former)) own.put(p.json());
        }
        own.put(value.json());
        prefs(context)
                .edit()
                .putString("profiles", own.toString())
                .putString("selected", value.name)
                .apply();
    }
}
