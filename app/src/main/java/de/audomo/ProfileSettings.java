package de.audomo;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.util.List;

public final class ProfileSettings extends ProtectedActivity {
    private enum Mode { OVERVIEW, CREATE, EDIT, RENAME }

    private Mode mode;
    private Profile source;
    private LinearLayout body;
    private EditText name, gain;
    private TextView nameError, gainError;
    private Spinner input, rate, bitrate;
    private RadioGroup echo, noise;

    @Override protected void openContent(Bundle state) {
        String action = getIntent().getStringExtra("mode");
        mode = action == null ? Mode.OVERVIEW : Mode.valueOf(action);
        LinearLayout page = column();
        page.setPadding(dp(16), 0, dp(16), 0);
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
        header.addView(text(switch (mode) {
            case OVERVIEW -> "Aufnahmeprofile";
            case CREATE -> "Neues Profil";
            case EDIT -> "Profil bearbeiten";
            case RENAME -> "Profil umbenennen";
        }, 22, MainActivity.INK));
        ScrollView scroll = new ScrollView(this);
        scroll.setId(R.id.profile_scroll);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        body = column();
        body.setPadding(0, dp(8), 0, dp(16));
        scroll.addView(body);
        if (mode != Mode.OVERVIEW) {
            String key = getIntent().getStringExtra("source");
            source = Profile.profiles(this).stream().filter(p -> p.name().equals(key)).findFirst().orElse(null);
            if (source == null) {
                toast("Profil nicht mehr vorhanden");
                finish();
            } else editor();
        }
    }

    @Override protected void resumeContent() {
        if (mode == Mode.OVERVIEW) overview();
    }

    private void overview() {
        body.removeAllViews();
        List<Profile> profiles = Profile.profiles(this);
        Profile active = Profile.selected(this);
        LinearLayout picker = card("Aktives Profil");
        Spinner dropdown = spinner(picker, "Aktives Profil",
                profiles.stream().map(Profile::name).toList(), active.name(), View.NO_ID,
                i -> profiles.get(i).rate() / 1000 + " kHz · " + profiles.get(i).kbps() + " kbit/s");
        LinearLayout details = card("Aufnahmeeinstellungen");
        LinearLayout actions = column();
        body.addView(actions);
        showProfile(details, actions, active);
        dropdown.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(AdapterView<?> parent) {}
            @Override public void onItemSelected(AdapterView<?> parent, View row, int i, long id) {
                if (!dropdown.isAttachedToWindow()) return;
                Profile chosen = profiles.get(i);
                Profile current = Profile.selected(ProfileSettings.this);
                if (chosen.name().equals(current.name())) return;
                if (!ready()) {
                    dropdown.setSelection(profiles.indexOf(current));
                    return;
                }
                Profile.prefs(ProfileSettings.this).edit().putString("selected", chosen.name()).apply();
                showProfile(details, actions, chosen);
            }
        });
    }

    private void showProfile(LinearLayout details, LinearLayout actions, Profile p) {
        details.removeAllViews();
        detail(details, "Format", "Opus / Ogg · Mono");
        detail(details, "Abtastrate", p.rate() / 1000 + " kHz");
        detail(details, "Bitrate", p.kbps() + " kbit/s");
        detail(details, "Verstärkung", p.gain() + " dB");
        detail(details, "Mikrofonwunsch", p.mic());
        detail(details, "Echounterdrückung", p.echo());
        detail(details, "Rauschunterdrückung", p.noise());
        actions.removeAllViews();
        LinearLayout first = actionRow(actions);
        button(first, "Bearbeiten", () -> openEditor(Mode.EDIT, p));
        button(first, "Neues Profil", () -> openEditor(Mode.CREATE, p));
        if (!Profile.builtIn(p.name())) {
            LinearLayout second = actionRow(actions);
            button(second, "Umbenennen", () -> openEditor(Mode.RENAME, p));
            button(second, "Löschen", () -> confirmDelete(p));
        }
    }

    private void openEditor(Mode next, Profile profile) {
        if (ready()) startActivity(new Intent(this, ProfileSettings.class)
                .putExtra("mode", next.name()).putExtra("source", profile.name()));
    }

    private void editor() {
        boolean editableName = mode == Mode.CREATE || !Profile.builtIn(source.name());
        LinearLayout identity = card("Profilname");
        if (editableName) {
            name = new EditText(this);
            name.setId(R.id.profile_name);
            name.setContentDescription("Profilname");
            name.setSingleLine();
            name.setHint("Name des Profils");
            if (mode != Mode.CREATE) name.setText(source.name());
            name.setSelectAllOnFocus(true);
            identity.addView(name);
            nameError = validationMessage(identity, name);
        } else identity.addView(text(source.name(), 17, MainActivity.INK));

        if (mode != Mode.RENAME) {
            LinearLayout audio = card("Aufnahmequalität");
            rate = field(audio, "Abtastrate (Hz)", Profile.RATES, source.rate(), R.id.profile_rate);
            bitrate = field(audio, "Bitrate (kbit/s)", Profile.BITRATES, source.kbps(), R.id.profile_bitrate);
            LinearLayout mic = card("Mikrofon und Klang");
            input = field(mic, "Mikrofonwunsch", Profile.INPUTS, source.mic(), R.id.profile_input);
            gain = new EditText(this);
            gain.setId(R.id.profile_gain);
            caption(mic, "Digitale Verstärkung (−20 bis +20 dB)", gain.getId());
            gain.setSingleLine();
            gain.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
            gain.setText(Integer.toString(source.gain()));
            mic.addView(gain);
            gainError = validationMessage(mic, gain);
            echo = effect(mic, "Echounterdrückung", source.echo(), R.id.profile_echo,
                    R.id.echo_default, R.id.echo_on, R.id.echo_off);
            noise = effect(mic, "Rauschunterdrückung", source.noise(), R.id.profile_noise,
                    R.id.noise_default, R.id.noise_on, R.id.noise_off);
        }
        LinearLayout actions = actionRow(body);
        button(actions, "Abbrechen", this::finish);
        button(actions, "Speichern", this::save);
    }

    private void save() {
        if (!ready()) return;
        String label = name == null ? source.name() : name.getText().toString().strip();
        if (label.isEmpty() || label.length() > 64) {
            showError(name, nameError, "Bitte einen Namen mit 1 bis 64 Zeichen eingeben");
            return;
        }
        int db = source.gain();
        if (gain != null) {
            try {
                db = Integer.parseInt(gain.getText().toString().trim());
                if (db < -20 || db > 20) throw new NumberFormatException();
            } catch (NumberFormatException exception) {
                showError(gain, gainError, "Bitte eine ganze Zahl von −20 bis +20 eingeben");
                return;
            }
        }
        Profile value = mode == Mode.RENAME
                ? new Profile(label, source.rate(), source.kbps(), db, source.mic(), source.echo(), source.noise())
                : new Profile(label, (int) rate.getSelectedItem(), (int) bitrate.getSelectedItem(), db,
                        (String) input.getSelectedItem(), effectValue(echo), effectValue(noise));
        try {
            Profile.save(this, value, mode == Mode.CREATE ? null : source.name());
            finish();
        } catch (IllegalArgumentException e) {
            if (name == null) toast(e.getMessage());
            else showError(name, nameError, e.getMessage());
        } catch (org.json.JSONException e) {
            toast("Profil konnte nicht gespeichert werden");
        }
    }

    private TextView validationMessage(LinearLayout parent, EditText field) {
        TextView message = text("", 14, getColor(R.color.permission_missing));
        message.setVisibility(View.GONE);
        message.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        parent.addView(message);
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                message.setVisibility(View.GONE);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        return message;
    }

    private void showError(EditText field, TextView message, String explanation) {
        message.setText(explanation);
        message.setVisibility(View.VISIBLE);
        field.requestFocus();
    }

    private void confirmDelete(Profile p) {
        if (!ready()) return;
        new AlertDialog.Builder(this).setTitle("Profil löschen?")
                .setMessage("„" + p.name() + "“ löschen und „" + Profile.BUILTINS.get(0).name() + "“ aktivieren?")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Löschen", (dialog, which) -> {
                    if (!ready()) return;
                    try {
                        Profile.delete(this, p.name());
                        overview();
                    } catch (org.json.JSONException e) {
                        toast("Profil konnte nicht gelöscht werden");
                    }
                }).show();
    }

    private boolean ready() {
        if (((Audomo) getApplication()).ready()) return true;
        toast("Profiländerungen sind erst möglich, wenn die Aufnahme bereit ist.");
        return false;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private LinearLayout card(String title) {
        TextView heading = text(title, 15, MainActivity.ACCENT);
        heading.setPadding(0, dp(10), 0, dp(6));
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setAccessibilityHeading(true);
        body.addView(heading);
        LinearLayout card = column();
        card.setBackground(surface());
        card.setPadding(dp(14), dp(6), dp(14), dp(6));
        body.addView(card);
        return card;
    }

    private GradientDrawable surface() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(MainActivity.PANEL);
        background.setCornerRadius(dp(12));
        background.setStroke(dp(1), MainActivity.LINE);
        return background;
    }

    private void detail(LinearLayout parent, String label, String value) {
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(0, dp(5), 0, dp(5));
        line.addView(text(label, 15, MainActivity.DIM), new LinearLayout.LayoutParams(0, -2, 1));
        TextView setting = text(value, 15, MainActivity.INK);
        setting.setGravity(Gravity.END);
        line.addView(setting, new LinearLayout.LayoutParams(0, -2, 1));
        parent.addView(line);
    }

    private void caption(LinearLayout box, String label, int id) {
        TextView caption = text(label, 14, MainActivity.DIM);
        caption.setPadding(0, dp(6), 0, 0);
        caption.setLabelFor(id);
        box.addView(caption);
    }

    private <T> Spinner field(LinearLayout box, String label, List<T> values, T current, int id) {
        caption(box, label, id);
        return spinner(box, label, values, current, id, null);
    }

    private <T> Spinner spinner(LinearLayout box, String label, List<T> values, T current, int id,
            java.util.function.IntFunction<String> subtitle) {
        Spinner view = new Spinner(new android.view.ContextThemeWrapper(this, R.style.ProfileFieldTheme),
                Spinner.MODE_DROPDOWN);
        view.setId(id);
        view.setContentDescription(label);
        view.setAdapter(new ProfileDropdown<>(view, values, subtitle));
        view.setSelection(values.indexOf(current));
        view.setMinimumHeight(dp(48));
        box.addView(view);
        return view;
    }

    private RadioGroup effect(LinearLayout box, String label, String current, int groupId, int... optionIds) {
        caption(box, label, groupId);
        RadioGroup group = new RadioGroup(this);
        group.setId(groupId);
        group.setOrientation(LinearLayout.HORIZONTAL);
        group.setBackground(surface());
        group.setPadding(dp(3), dp(3), dp(3), dp(3));
        box.addView(group);
        int[] checked = {android.R.attr.state_checked};
        for (int i = 0; i < Profile.EFFECTS.size(); i++) {
            String value = Profile.EFFECTS.get(i);
            RadioButton choice = new RadioButton(this);
            choice.setId(optionIds[i]);
            choice.setTag(value);
            choice.setText(value.equals(Profile.SYSTEM) ? "Standard" : value);
            choice.setContentDescription(label + ", " + value);
            choice.setButtonDrawable(null);
            choice.setGravity(Gravity.CENTER);
            choice.setMinHeight(dp(48));
            choice.setPadding(dp(3), 0, dp(3), 0);
            GradientDrawable active = new GradientDrawable();
            active.setColor(0xffd1bcf5);
            active.setCornerRadius(dp(9));
            StateListDrawable states = new StateListDrawable();
            states.addState(checked, active);
            states.addState(new int[0], new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            choice.setBackground(new RippleDrawable(ColorStateList.valueOf(0x337e61aa), states, null));
            choice.setTextColor(new ColorStateList(new int[][] {checked, new int[0]},
                    new int[] {0xff241935, MainActivity.INK}));
            group.addView(choice, new RadioGroup.LayoutParams(0, -2, 1));
            if (value.equals(current)) group.check(choice.getId());
        }
        return group;
    }

    private String effectValue(RadioGroup group) {
        return (String) group.findViewById(group.getCheckedRadioButtonId()).getTag();
    }

    private LinearLayout actionRow(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(8), 0, 0);
        parent.addView(row);
        return row;
    }

    private void button(LinearLayout row, String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(MainActivity.INK);
        b.setAllCaps(false);
        b.setMinimumHeight(dp(48));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x448e83bb), surface(), null));
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        if (row.getChildCount() > 0) params.setMarginStart(dp(6));
        row.addView(b, params);
    }
}
