package de.audomo;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ManualUploadDialog extends DialogFragment {
    private static final String FILES = "uploadRecordings", TARGET = "uploadTarget";
    private final Set<String> marked = new LinkedHashSet<>();
    private final Set<String> targetIds = new LinkedHashSet<>();
    private List<CloudConfig> choices = List.of();
    private List<SessionStore.Entry> entries = List.of();
    private ListView list;
    private TextView count;
    private ImageButton toggleAll;

    static void chooseRecordings(MainActivity screen) {
        FragmentManager fm = screen.getFragmentManager();
        if (fm.findFragmentByTag(FILES) == null) new ManualUploadDialog().show(fm, FILES);
    }

    static void chooseTarget(MainActivity screen, List<String> ids) {
        chooseTarget(screen, ids, "");
    }

    static void chooseTarget(MainActivity screen, List<String> ids, String excludedTarget) {
        if (ids.isEmpty()) return;
        FragmentManager fm = screen.getFragmentManager();
        if (fm.findFragmentByTag(TARGET) != null) return;
        ManualUploadDialog dialog = new ManualUploadDialog();
        Bundle args = new Bundle();
        args.putStringArrayList("ids", new ArrayList<>(ids));
        args.putString("excludedTarget", excludedTarget);
        dialog.setArguments(args);
        dialog.show(fm, TARGET);
    }

    @Override
    public Dialog onCreateDialog(Bundle state) {
        MainActivity screen = (MainActivity) getActivity();
        if (state != null && state.containsKey("targets"))
            targetIds.addAll(state.getStringArrayList("targets"));
        if (getArguments() != null)
            return targetDialog(screen, getArguments().getStringArrayList("ids"));

        if (state != null && state.containsKey("marked"))
            marked.addAll(state.getStringArrayList("marked"));
        LinearLayout page = screen.column();
        page.setPadding(screen.dp(16), screen.dp(8), screen.dp(16), 0);
        count = screen.text("Aufnahmen werden geladen …", 14, MainActivity.DIM);
        LinearLayout selection = screen.row();
        page.addView(selection);
        selection.addView(count, screen.weight());
        toggleAll = screen.toolbarIcon("Alle auswählen", R.drawable.ic_select_all);
        selection.addView(toggleAll, screen.lp(48, 48));
        list = new ListView(screen);
        list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        page.addView(list);
        return new AlertDialog.Builder(screen)
                .setTitle("Bestehende Aufnahmen hochladen")
                .setView(page)
                .setPositiveButton("Dienst wählen", null)
                .setNegativeButton("Abbrechen", null)
                .create();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getArguments() != null) {
            if (choices.size() > 1) {
                AlertDialog dialog = (AlertDialog) getDialog();
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!targetIds.isEmpty());
            }
            return;
        }
        MainActivity screen = (MainActivity) getActivity();
        AlertDialog dialog = (AlertDialog) getDialog();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        toggleAll.setEnabled(false);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> chooseTarget(screen, new ArrayList<>(marked)));
        toggleAll.setOnClickListener(v -> {
            boolean select = marked.size() != entries.size();
            marked.clear();
            for (int i = 0; i < entries.size(); i++) {
                list.setItemChecked(i, select);
                if (select) marked.add(entries.get(i).id());
            }
            update(dialog);
        });
        Audomo app = (Audomo) screen.getApplication();
        app.libraryFiles.execute(() -> {
            try {
                List<SessionStore.Entry> available = new ArrayList<>(app.sessions.list());
                available.removeIf(e -> e.trashed() != 0);
                screen.runOnUiThread(() -> {
                    if (!isAdded() || getDialog() != dialog) return;
                    entries = available;
                    Set<String> remaining = new HashSet<>();
                    ArrayList<String> labels = new ArrayList<>();
                    for (SessionStore.Entry e : entries) {
                        remaining.add(e.id());
                        labels.add(e.folder().isEmpty() ? e.name() : e.name() + "\n" + e.folder());
                    }
                    marked.retainAll(remaining);
                    list.setAdapter(
                            new ArrayAdapter<>(
                                    screen,
                                    android.R.layout.simple_list_item_multiple_choice,
                                    labels));
                    for (int i = 0; i < entries.size(); i++)
                        list.setItemChecked(i, marked.contains(entries.get(i).id()));
                    list.setOnItemClickListener((parent, row, index, id) -> {
                        String key = entries.get(index).id();
                        if (list.isItemChecked(index)) marked.add(key);
                        else marked.remove(key);
                        update(dialog);
                    });
                    update(dialog);
                });
            } catch (Exception error) {
                screen.runOnUiThread(() -> {
                    if (isAdded() && getDialog() == dialog) count.setText(Audomo.cause(error));
                });
            }
        });
    }

    private void update(AlertDialog dialog) {
        count.setText(
                entries.isEmpty()
                        ? "Keine gespeicherten Aufnahmen vorhanden"
                        : marked.size() + " von " + entries.size() + " ausgewählt");
        list.setVisibility(entries.isEmpty() ? View.GONE : View.VISIBLE);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!marked.isEmpty());
        boolean all = !entries.isEmpty() && marked.size() == entries.size();
        String label = all ? "Alle abwählen" : "Alle auswählen";
        toggleAll.setEnabled(!entries.isEmpty());
        toggleAll.setAlpha(entries.isEmpty() ? 0.45f : 1f);
        toggleAll.setImageResource(all ? R.drawable.ic_deselect_all : R.drawable.ic_select_all);
        toggleAll.setContentDescription(label);
        toggleAll.setTooltipText(label);
    }

    private Dialog targetDialog(MainActivity screen, List<String> ids) {
        String amount = ids.size() == 1 ? "1 Aufnahme" : ids.size() + " Aufnahmen";
        AlertDialog.Builder builder =
                new AlertDialog.Builder(screen)
                        .setTitle("Uploadziel für " + amount)
                        .setNegativeButton("Abbrechen", null);
        try {
            List<CloudConfig> configs = CloudConfig.all(screen);
            String excluded = getArguments().getString("excludedTarget", "");
            configs.removeIf(
                    config -> !config.hasCredentials(screen) || config.id().equals(excluded));
            if (configs.isEmpty())
                return builder.setMessage(
                                excluded.isEmpty()
                                        ? "Verbinde zuerst einen Dienst für den Cloud Upload."
                                        : "Verbinde ein weiteres Uploadziel für diese Aufnahme.")
                        .setPositiveButton(
                                "Uploadziele einrichten",
                                (dialog, which) -> new CloudScreen(screen).manage(ids))
                        .create();
            builder.setNeutralButton(
                    "Ziel einrichten", (dialog, which) -> new CloudScreen(screen).manage(ids));
            List<String> names = new ArrayList<>();
            for (CloudConfig target : configs) names.add(target.uploadLabel());
            String[] labels = names.toArray(new String[0]);
            String standard = Profile.prefs(screen).getString("defaultTarget", "");
            for (int i = 0; i < labels.length; i++) {
                if (Collections.frequency(names, labels[i]) > 1) labels[i] += " · Ziel " + (i + 1);
                if (configs.get(i).id().equals(standard)) labels[i] += " · Standard";
            }
            choices = configs;
            if (configs.size() == 1)
                return builder.setItems(labels, (dialog, which) -> enqueue(screen, ids, configs))
                        .create();
            targetIds.retainAll(
                    configs.stream()
                            .map(CloudConfig::id)
                            .collect(java.util.stream.Collectors.toSet()));
            boolean[] selected = new boolean[configs.size()];
            for (int i = 0; i < selected.length; i++)
                selected[i] = targetIds.contains(configs.get(i).id());
            return builder.setTitle("Uploadziele für " + amount)
                    .setMultiChoiceItems(
                            labels,
                            selected,
                            (d, which, on) -> {
                                String id = configs.get(which).id();
                                if (on) targetIds.add(id);
                                else targetIds.remove(id);
                                ((AlertDialog) d)
                                        .getButton(AlertDialog.BUTTON_POSITIVE)
                                        .setEnabled(!targetIds.isEmpty());
                            })
                    .setPositiveButton(
                            "Einreihen",
                            (d, which) ->
                                    enqueue(
                                            screen,
                                            ids,
                                            configs.stream()
                                                    .filter(t -> targetIds.contains(t.id()))
                                                    .collect(java.util.stream.Collectors.toList())))
                    .create();
        } catch (Exception error) {
            return builder.setMessage(Audomo.cause(error)).create();
        }
    }

    private void enqueue(MainActivity screen, List<String> ids, List<CloudConfig> targets) {
        screen.runFiles(() -> {
            int added =
                    UploadQueue.get(screen)
                            .enqueueSelected(
                                    ((Audomo) screen.getApplication()).sessions, ids, targets);
            screen.runOnUiThread(() -> {
                if (screen.isDestroyed() || screen.isFinishing()) return;
                DialogFragment picker =
                        (DialogFragment) screen.getFragmentManager().findFragmentByTag(FILES);
                if (picker != null) picker.dismissAllowingStateLoss();
            });
            if (added == 0) return "Für die gewählten Ziele bestehen bereits alle Aufträge.";
            String message = added == 1 ? "1 Auftrag eingereiht" : added + " Aufträge eingereiht";
            int existing = ids.size() * targets.size() - added;
            return existing == 0 ? message : message + " · Bereits beauftragt: " + existing;
        });
    }

    @Override
    public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putStringArrayList("targets", new ArrayList<>(targetIds));
        if (getArguments() == null) out.putStringArrayList("marked", new ArrayList<>(marked));
    }
}
