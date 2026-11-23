package de.audomo;

import android.app.*;
import android.view.*;
import android.widget.*;

import java.util.*;

final class ProfileDialog {
    private final Activity screen;
    private final Audomo app;
    private final int pad;

    ProfileDialog(Activity screen) {
        this.screen = screen;
        app = (Audomo) screen.getApplication();
        pad = (int) (screen.getResources().getDisplayMetrics().density * 16);
    }

    void choose() {
        List<Profile> all = Profile.profiles(screen);
        String[] names =
                all.stream()
                        .map(p -> p.name() + " · " + p.kbps() + " kbit/s")
                        .toArray(String[]::new);
        int[] choice = {all.indexOf(Profile.selected(screen))};
        AlertDialog dialog =
                new AlertDialog.Builder(screen)
                        .setTitle("Aufnahmeprofil")
                        .setSingleChoiceItems(names, choice[0], (d, i) -> choice[0] = i)
                        .setPositiveButton(
                                "Übernehmen",
                                (d, w) -> {
                                    if (app.ready())
                                        Profile.prefs(screen)
                                                .edit()
                                                .putString("selected", all.get(choice[0]).name())
                                                .apply();
                                })
                        .setNeutralButton("Profile verwalten", null)
                        .setNegativeButton("Schließen", null)
                        .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> manage(dialog));
        dialog.getWindow().setGravity(Gravity.BOTTOM);
        dialog.getWindow()
                .setLayout(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.WRAP_CONTENT);
    }

    private void manage(AlertDialog chooser) {
        List<Profile> all = Profile.profiles(screen);
        String[] names =
                all.stream()
                        .map(p -> p.name() + " · " + p.kbps() + " kbit/s")
                        .toArray(String[]::new);
        AlertDialog management = new AlertDialog.Builder(screen)
                .setTitle("Aufnahmeprofile")
                .setItems(names, null)
                .setPositiveButton("Neues Profil", null)
                .setNegativeButton("Schließen", null)
                .show();
        Runnable saved = () -> {
            management.dismiss();
            chooser.dismiss();
            choose();
        };
        management.getListView().setOnItemClickListener((parent, view, i, id) ->
                editor(all.get(i), saved));
        management.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> editor(null, saved));
    }

    private void editor(Profile source, Runnable saved) {
        Profile p = source == null ? Profile.selected(screen) : source;
        boolean create = source == null;
        ScrollView scroll = new ScrollView(screen);
        LinearLayout fields = new LinearLayout(screen);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(pad, 0, pad, pad);
        scroll.addView(fields);
        label(fields, "Name");
        EditText name = new EditText(screen);
        name.setSingleLine();
        name.setText(create ? p.name() + " (Kopie)" : p.name());
        name.setEnabled(
                create || Profile.BUILTINS.stream().noneMatch(b -> b.name().equals(p.name())));
        fields.addView(name);
        label(fields, "Opus / Ogg · Mono");
        Spinner input = select(fields, "Mikrofonwunsch", Profile.INPUTS, p.mic());
        Spinner rate = select(fields, "Abtastrate (Hz)", Profile.RATES, p.rate());
        Spinner bitrate = select(fields, "Bitrate (kbit/s)", Profile.BITRATES, p.kbps());
        label(fields, "Digitale Verstärkung (−20 bis +20 dB)");
        EditText gain = new EditText(screen);
        gain.setSingleLine();
        gain.setInputType(
                android.text.InputType.TYPE_CLASS_NUMBER
                        | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        gain.setText(String.valueOf(p.gain()));
        fields.addView(gain);
        Spinner echo = select(fields, "Echounterdrückung", Profile.EFFECTS, p.echo());
        Spinner noise = select(fields, "Rauschunterdrückung", Profile.EFFECTS, p.noise());
        AlertDialog dialog =
                new AlertDialog.Builder(screen)
                        .setTitle(create ? "Eigenes Profil" : "Profil bearbeiten")
                        .setView(scroll)
                        .setPositiveButton("Speichern", null)
                        .setNegativeButton("Abbrechen", null)
                        .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    if (!app.ready()) {
                        dialog.dismiss();
                        return;
                    }
                    try {
                        Profile edited =
                                new Profile(
                                        name.getText().toString().strip(),
                                        (Integer) rate.getSelectedItem(),
                                        (Integer) bitrate.getSelectedItem(),
                                        Integer.parseInt(gain.getText().toString()),
                                        input.getSelectedItem().toString(),
                                        echo.getSelectedItem().toString(),
                                        noise.getSelectedItem().toString());
                        Profile.save(screen, edited, create ? null : p.name());
                        dialog.dismiss();
                        saved.run();
                    } catch (Exception e) {
                        Toast.makeText(
                                        screen,
                                        "Profil prüfen · " + Audomo.cause(e),
                                        Toast.LENGTH_LONG)
                                .show();
                    }
                });
    }

    private void label(LinearLayout fields, String label) {
        TextView v = new TextView(screen);
        v.setText(label);
        v.setTextColor(MainActivity.ACCENT);
        v.setPadding(0, pad / 2, 0, 0);
        fields.addView(v);
    }

    private <T> Spinner select(LinearLayout fields, String label, List<T> choices, T selected) {
        label(fields, label);
        Spinner s = new Spinner(screen);
        ArrayAdapter<T> a =
                new ArrayAdapter<>(screen, android.R.layout.simple_spinner_dropdown_item, choices);
        s.setAdapter(a);
        s.setSelection(choices.indexOf(selected));
        fields.addView(s);
        return s;
    }
}
