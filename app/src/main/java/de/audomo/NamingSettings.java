package de.audomo;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Set;

public final class NamingSettings extends ProtectedActivity {
    private static final String[] PRESETS = {
        "Datum, Uhrzeit und Termintitel", "Kalendername", "Termintitel", "Datum und Kalendername"
    };
    private static final String[] FORMATS = {
        CalendarNames.DEFAULT, "{kalender}", "{termin}", "{datum}_{kalender}"
    };
    private static final String[] TOKENS = {"{datum}", "{uhrzeit}", "{termin}", "{kalender}"};
    private static final String[] TOKEN_LABELS = {
        "Datum", "Uhrzeit", "Termintitel", "Kalendername"
    };

    private final OffsetDateTime exampleTime = OffsetDateTime.now();
    private final CalendarNames.Event example =
            new CalendarNames.Event(0, 0, "Abendessen", 0, 0, "Privat");
    private CalendarNames.FolderMode mode;
    private String base;
    private LinearLayout body;
    private ScrollView scroll;
    private EditText template;
    private TextView matched, fallback, folderHint;
    private Button baseButton, calendars;

    @Override
    protected void openContent(Bundle state) {
        mode =
                state == null
                        ? CalendarNames.folderMode(this)
                        : CalendarNames.FolderMode.valueOf(state.getString("mode"));
        base =
                state == null
                        ? Profile.prefs(this).getString("recordingBase", "")
                        : state.getString("base");
        LinearLayout page = column();
        page.setPadding(dp(16), 0, dp(16), dp(8));
        page.setFocusableInTouchMode(true);
        setContentView(page);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(header);
        ImageButton back = new ImageButton(this, null, android.R.attr.borderlessButtonStyle);
        back.setImageResource(R.drawable.ic_arrow_back);
        back.setContentDescription("Zurück");
        back.setOnClickListener(v -> finish());
        header.addView(back, dp(48), dp(48));
        header.addView(text("Dateiname und Ordner", 22, MainActivity.INK));

        scroll = new ScrollView(this);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        body = column();
        scroll.addView(body);
        TextView nameLabel = section("Dateiname");
        template = new EditText(this);
        template.setId(View.generateViewId());
        nameLabel.setLabelFor(template.getId());
        template.setSingleLine();
        template.setHint("Vorlage für neue Aufnahmen");
        template.setText(
                state == null ? CalendarNames.template(this) : state.getString("template"));
        template.setSelection(template.length());
        body.addView(template);
        LinearLayout tools = new LinearLayout(this);
        body.addView(tools);
        LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(0, -2, 1);
        first.setMarginEnd(dp(8));
        tools.addView(button("Vorlagen", this::presets), first);
        tools.addView(
                button("Baustein einfügen", this::insertToken),
                new LinearLayout.LayoutParams(0, -2, 1));

        section("Aufnahmeordner");
        RadioGroup modes = new RadioGroup(this);
        body.addView(modes);
        for (CalendarNames.FolderMode option : CalendarNames.FolderMode.values()) {
            RadioButton radio = new RadioButton(this);
            radio.setId(View.generateViewId());
            radio.setText(option.label);
            radio.setMinHeight(dp(48));
            modes.addView(radio);
            radio.setChecked(option == mode);
            radio.setOnCheckedChangeListener((v, selected) -> {
                if (selected) {
                    mode = option;
                    preview();
                }
            });
        }
        baseButton = button("", this::chooseBase);
        body.addView(baseButton);
        folderHint =
                text(
                        "Audomo legt fehlende Kalenderordner bei der ersten Aufnahme an.",
                        14,
                        MainActivity.DIM);
        body.addView(folderHint);

        section("Vorschau");
        LinearLayout examples = column();
        examples.setPadding(dp(12), dp(10), dp(12), dp(10));
        examples.setBackground(surface(MainActivity.PANEL));
        body.addView(examples);
        examples.addView(text("Termin „Abendessen“ im Kalender „Privat“", 14, MainActivity.DIM));
        matched = text("", 16, MainActivity.INK);
        matched.setPadding(0, dp(3), 0, dp(10));
        examples.addView(matched);
        examples.addView(text("Ohne passenden Termin", 14, MainActivity.DIM));
        fallback = text("", 16, MainActivity.INK);
        fallback.setPadding(0, dp(3), 0, 0);
        examples.addView(fallback);
        calendars =
                button(
                        "Kalender auswählen",
                        () ->
                                startActivity(
                                        new android.content.Intent(this, CalendarSettings.class)));
        LinearLayout.LayoutParams calendarRow = new LinearLayout.LayoutParams(-1, -2);
        calendarRow.topMargin = dp(8);
        body.addView(calendars, calendarRow);
        template.addTextChangedListener(
                new TextWatcher() {
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        preview();
                    }

                    public void afterTextChanged(Editable s) {}
                });
        Button save = button("Speichern", this::save);
        save.setTextColor(MainActivity.BG);
        styleButton(save, MainActivity.ACCENT);
        LinearLayout.LayoutParams footer = new LinearLayout.LayoutParams(-1, -2);
        footer.topMargin = dp(8);
        page.addView(save, footer);
        if (state != null) {
            int cursor = Math.min(state.getInt("cursor"), template.length());
            template.setSelection(Math.max(0, cursor));
            scroll.post(() -> scroll.scrollTo(0, state.getInt("scroll")));
        }
        preview();
    }

    private void presets() {
        new AlertDialog.Builder(this)
                .setTitle("Dateinamenvorlage")
                .setItems(
                        PRESETS,
                        (d, i) -> {
                            template.setText(FORMATS[i]);
                            template.setSelection(template.length());
                        })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    private void insertToken() {
        new AlertDialog.Builder(this)
                .setTitle("Baustein einfügen")
                .setItems(
                        TOKEN_LABELS,
                        (d, i) -> {
                            int a = Math.max(0, template.getSelectionStart());
                            int b = Math.max(0, template.getSelectionEnd());
                            template.getText().replace(Math.min(a, b), Math.max(a, b), TOKENS[i]);
                            template.setSelection(Math.min(a, b) + TOKENS[i].length());
                        })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    private void chooseBase() {
        Set<String> folders = new LibraryFiles(((Audomo) getApplication()).sessions).folders();
        folders.add(base);
        ArrayList<String> paths = new ArrayList<>(folders);
        String[] labels = paths.stream().map(NamingSettings::displayFolder).toArray(String[]::new);
        new AlertDialog.Builder(this)
                .setTitle("Basisordner wählen")
                .setItems(
                        labels,
                        (d, i) -> {
                            base = paths.get(i);
                            preview();
                        })
                .setNeutralButton("Neuer Unterordner", (d, w) -> newFolder())
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    private void newFolder() {
        EditText input = new EditText(this);
        input.setSingleLine();
        input.setHint("Ordnername");
        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle("Unterordner in " + displayFolder(base))
                        .setView(input)
                        .setPositiveButton("Übernehmen", null)
                        .setNegativeButton("Abbrechen", null)
                        .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String name = SessionStore.cleanComponent(input.getText().toString());
                    if (name.isBlank()) {
                        input.setError("Ordnernamen eingeben");
                        return;
                    }
                    base = base.isEmpty() ? name : base + "/" + name;
                    preview();
                    dialog.dismiss();
                });
    }

    private static String displayFolder(String path) {
        return path.isEmpty() ? "Aufnahmen" : "Aufnahmen/" + path;
    }

    private void preview() {
        if (matched == null) return;
        String format = template.getText().toString();
        String parent =
                mode == CalendarNames.FolderMode.CURRENT
                        ? CalendarNames.baseFolder(this, mode)
                        : base;
        baseButton.setVisibility(
                mode == CalendarNames.FolderMode.CURRENT ? View.GONE : View.VISIBLE);
        baseButton.setText("Basisordner · " + displayFolder(base));
        folderHint.setVisibility(
                mode == CalendarNames.FolderMode.CALENDAR ? View.VISIBLE : View.GONE);
        matched.setText(
                displayFolder(CalendarNames.folder(parent, mode, example))
                        + "/"
                        + CalendarNames.name(format, exampleTime, example));
        fallback.setText(
                displayFolder(parent) + "/" + CalendarNames.name(format, exampleTime, null));
        boolean usesCalendar =
                mode == CalendarNames.FolderMode.CALENDAR
                        || format.contains("{kalender}")
                        || format.contains("{termin}");
        calendars.setVisibility(usesCalendar ? View.VISIBLE : View.GONE);
    }

    private void save() {
        if (template.getText().toString().isBlank()) {
            template.setError("Dateinamenvorlage eingeben");
            template.requestFocus();
            return;
        }
        Profile.prefs(this)
                .edit()
                .putString("template", template.getText().toString())
                .putString("recordingFolderMode", mode.name())
                .putString("recordingBase", base)
                .apply();
        finish();
    }

    @Override
    protected void saveContent(Bundle out) {
        out.putString("mode", mode.name());
        out.putString("base", base);
        out.putString("template", template.getText().toString());
        out.putInt("cursor", template.getSelectionStart());
        out.putInt("scroll", scroll.getScrollY());
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private TextView section(String label) {
        TextView heading = text(label, 17, MainActivity.ACCENT);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setAccessibilityHeading(true);
        heading.setPadding(0, dp(14), 0, dp(4));
        body.addView(heading);
        return heading;
    }

    private Button button(String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setMinHeight(dp(48));
        b.setTextColor(MainActivity.INK);
        styleButton(b, MainActivity.PANEL);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private GradientDrawable surface(int color) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(12));
        background.setStroke(dp(1), MainActivity.LINE);
        return background;
    }

    private void styleButton(Button b, int color) {
        b.setBackground(
                new RippleDrawable(ColorStateList.valueOf(0x448e83bb), surface(color), null));
    }
}
