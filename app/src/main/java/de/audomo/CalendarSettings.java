package de.audomo;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.CalendarContract.Calendars;
import android.text.*;
import android.view.*;
import android.widget.*;

import java.util.*;
import java.util.concurrent.*;

public final class CalendarSettings extends ProtectedActivity {
    private LinearLayout page, rows;
    private TextView notice;
    private EditText before, after;
    private final List<Long> order = new ArrayList<>();
    private final Map<Long, String> available = new LinkedHashMap<>();
    private final ExecutorService queries = Executors.newSingleThreadExecutor();
    private int pad;

    @Override
    protected void openContent(Bundle saved) {
        pad = (int) (getResources().getDisplayMetrics().density * 16);
        ScrollView scroll = new ScrollView(this);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(pad, pad, pad, pad);
        scroll.addView(page);
        setContentView(scroll);
        title("Kalender", 24);
        notice = title("", 14);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        page.addView(rows);
        order.addAll(CalendarNames.ids(this));
        Button access =
                button(
                        "Kalenderzugriff erlauben",
                        v ->
                                requestPermissions(
                                        new String[] {Manifest.permission.READ_CALENDAR}, 4));
        access.setVisibility(granted() ? View.GONE : View.VISIBLE);
        page.addView(access);
        title(
                "Laufende Termine haben Vorrang. Bei Überschneidung entscheidet die Reihenfolge der"
                        + " Kalender.",
                14);
        LinearLayout times = new LinearLayout(this);
        page.addView(times);
        before = minutes(times, "Vorlauf (Min.)", "before");
        after = minutes(times, "Nachlauf (Min.)", "after");
        page.addView(button("Speichern", v -> save()));
        load();
    }

    private boolean granted() {
        return checkSelfPermission(Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
    }

    private TextView title(String value, int size) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setPadding(0, pad / 2, 0, pad / 2);
        page.addView(t);
        return t;
    }

    private Button button(String text, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(click);
        return b;
    }

    private EditText minutes(LinearLayout times, String label, String key) {
        LinearLayout field = new LinearLayout(this);
        field.setOrientation(LinearLayout.VERTICAL);
        times.addView(field, new LinearLayout.LayoutParams(0, -2, 1));
        TextView title = new TextView(this);
        title.setText(label);
        field.addView(title);
        EditText n = new EditText(this);
        n.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        n.setText(String.valueOf(Profile.prefs(this).getInt(key, 0)));
        field.addView(n);
        return n;
    }

    private void load() {
        if (!granted()) {
            notice.setText("Ohne Zugriff verwendet Audomo den Ersatznamen.");
            return;
        }
        queries.execute(() -> {
            Map<Long, String> names = new LinkedHashMap<>();
            try (Cursor c =
                    getContentResolver()
                            .query(
                                    Calendars.CONTENT_URI,
                                    new String[] {Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME},
                                    null,
                                    null,
                                    Calendars.CALENDAR_DISPLAY_NAME + " ASC")) {
                if (c != null) while (c.moveToNext()) names.put(c.getLong(0), c.getString(1));
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    available.clear();
                    available.putAll(names);
                    order.retainAll(names.keySet());
                    notice.setText(
                            names.isEmpty()
                                    ? "Keine Kalender auf diesem Gerät"
                                    : "Aktive Kalender nach Priorität");
                    render();
                });
            } catch (RuntimeException e) {
                runOnUiThread(() -> notice.setText("Kalender nicht lesbar"));
            }
        });
    }

    private void render() {
        rows.removeAllViews();
        List<Long> sorted = new ArrayList<>(order);
        for (long id : available.keySet()) if (!sorted.contains(id)) sorted.add(id);
        for (long id : sorted) {
            LinearLayout row = new LinearLayout(this);
            rows.addView(row);
            CheckBox box = new CheckBox(this);
            box.setText(available.get(id));
            box.setChecked(order.contains(id));
            row.addView(box, new LinearLayout.LayoutParams(0, -2, 1));
            box.setOnCheckedChangeListener((b, on) -> {
                if (on) order.add(id);
                else order.remove(Long.valueOf(id));
                render();
            });
            if (!order.contains(id)) continue;
            Button move =
                    button(
                            String.valueOf(order.indexOf(id) + 1),
                            v -> {
                                String[] slots = new String[order.size()];
                                for (int i = 0; i < slots.length; i++)
                                    slots[i] = "Position " + (i + 1);
                                new AlertDialog.Builder(this)
                                        .setTitle("Priorität")
                                        .setItems(slots, (d, i) -> move(id, i))
                                        .show();
                            });
            move.setContentDescription("Priorität ändern");
            row.addView(move);
            move.setOnLongClickListener(v ->
                    v.startDragAndDrop(
                            ClipData.newPlainText("Kalender", ""),
                            new View.DragShadowBuilder(v),
                            id,
                            0));
            row.setOnDragListener((v, event) -> {
                if (!(event.getLocalState() instanceof Long)) return false;
                if (event.getAction() == DragEvent.ACTION_DROP)
                    move((Long) event.getLocalState(), order.indexOf(id));
                return true;
            });
        }
    }

    private void move(long id, int destination) {
        order.remove(Long.valueOf(id));
        order.add(destination, id);
        render();
    }

    private void save() {
        try {
            int a = Integer.parseInt(before.getText().toString()),
                    b = Integer.parseInt(after.getText().toString());
            if (a < 0 || b < 0) throw new IllegalArgumentException();
            Profile.prefs(this)
                    .edit()
                    .putString("calendars", android.text.TextUtils.join(",", order))
                    .putInt("before", a)
                    .putInt("after", b)
                    .apply();
            finish();
        } catch (IllegalArgumentException invalid) {
            notice.setText("Für Vorlauf und Nachlauf ganze Minuten ab null eingeben");
        }
    }

    @Override
    protected void permissionResult(int code, String[] keys, int[] results) {
        if (granted()) recreate();
        else load();
    }

    @Override
    protected void onDestroy() {
        queries.shutdown();
        super.onDestroy();
    }
}
