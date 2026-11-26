package de.audomo;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

import java.util.*;

final class QueueDashboard {
    private static final String[] FILTERS = {"Alle", "Alle (außer Fertiggestellte)", "Fehlerhafte Uploads", "In Warteschlange"};
    private final MainActivity screen;
    private final Audomo app;
    private final TextView summary, empty;
    private final Button filterButton, targets, retryErrors;
    private String waitingFor = "";
    private final TextView totals;
    private final QueueSwipeList list;
    private final LinearLayout normalActions, selectionActions;
    private final Button cancelSelection, promoteSelected, deleteSelected, select;
    private final ImageButton toggleAll, manage;
    private final Set<Long> selected = new HashSet<>();
    private boolean selecting;
    private final Rows adapter = new Rows();
    private List<UploadQueue.Job> jobs = List.of(), visible = List.of();
    private Map<String, String> labels = Map.of();
    private int filter, restoreIndex, restoreOffset;
    private boolean loading;

    QueueDashboard(MainActivity screen, LinearLayout body, Bundle state) {
        this.screen = screen;
        app = (Audomo) screen.getApplication();
        filter = savedFilter();
        if (state != null) {
            selecting = state.getBoolean("queueSelecting");
            long[] saved = state.getLongArray("queueSelected");
            if (saved != null) for (long id : saved) selected.add(id);
            restoreIndex = state.getInt("queueIndex");
            restoreOffset = state.getInt("queueOffset");
        }
        list = new QueueSwipeList(screen, id -> remove(List.of(id)), id -> prioritize(List.of(id)));
        LinearLayout heading = screen.row();
        heading.setPadding(0, screen.dp(12), 0, screen.dp(4));
        body.addView(heading);
        summary = screen.text("Aufträge werden geladen …", 21, MainActivity.INK);
        heading.addView(summary, screen.weight());
        filterButton =
                screen.button(
                        FILTERS[filter],
                        v ->
                                new AlertDialog.Builder(screen)
                                        .setTitle("Aufträge anzeigen")
                                        .setSingleChoiceItems(
                                                FILTERS,
                                                filter,
                                                (dialog, i) -> {
                                                    filter = i;
                                                    Profile.prefs(screen).edit().putInt("queueFilter", i).apply();
                                                    applyFilter();
                                                    if (!visible.isEmpty()) list.setSelection(0);
                                                    dialog.dismiss();
                                                })
                                        .setNegativeButton("Abbrechen", null)
                                        .show());
        body.addView(filterButton);
        cancelSelection = screen.button("Abbrechen", v -> exitSelection());
        heading.addView(cancelSelection);
        totals = screen.text("", 14, MainActivity.DIM);
        totals.setPadding(0, 0, 0, screen.dp(8));
        body.addView(totals);
        targets = screen.button("Uploadziele", v -> new CloudScreen(screen).manage(List.of()));
        body.addView(targets);
        normalActions = screen.row();
        normalActions.setPadding(0, screen.dp(4), 0, screen.dp(6));
        normalActions.addView(
                screen.button(
                        "Upload hinzufügen", v -> ManualUploadDialog.chooseRecordings(screen)),
                screen.weight());
        retryErrors =
                screen.button(
                        "Fehler wiederholen",
                        v ->
                                screen.runFiles(() ->
                                        UploadQueue.get(screen).retryErrors()
                                                + " Aufträge freigegeben"));
        retryErrors.setVisibility(View.GONE);
        select = screen.button("Auswählen", v -> enterSelection());
        normalActions.addView(select, screen.weight());
        manage = screen.toolbarIcon("Warteschlange verwalten", R.drawable.ic_more);
        manage.setOnClickListener(v -> manage());
        normalActions.addView(manage, screen.lp(48, 48));
        body.addView(normalActions);
        body.addView(retryErrors);
        selectionActions = screen.row();
        body.addView(selectionActions);
        toggleAll = screen.toolbarIcon("Alle auswählen", R.drawable.ic_select_all);
        toggleAll.setOnClickListener(v -> {
            boolean all = !visible.isEmpty() && selected.size() == visible.size();
            selected.clear();
            if (!all) for (UploadQueue.Job job : visible) selected.add(job.id());
            updateSelection();
        });
        selectionActions.addView(toggleAll, screen.lp(48, 48));
        promoteSelected = screen.button("An die Spitze", v -> prioritize(selectedIds()));
        deleteSelected = screen.button("Entfernen", v -> confirmRemove(selectedIds(), false));
        selectionActions.addView(promoteSelected, screen.weight());
        selectionActions.addView(deleteSelected, screen.weight());
        empty = screen.text("", 16, MainActivity.DIM);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, screen.dp(24), 0, 0);
        body.addView(empty);

        list.setDivider(new android.graphics.drawable.ColorDrawable(MainActivity.BG));
        list.setDividerHeight(screen.dp(6));
        list.setAdapter(adapter);
        list.setOnItemClickListener((a, v, index, id) -> {
            if (selecting) toggle(id);
            else UploadDetailsDialog.open(screen, id);
        });
        list.setOnItemLongClickListener((a, v, index, id) -> {
            selecting = true;
            toggle(id);
            return true;
        });
        body.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        updateControls();
        refresh();
    }

    void save(Bundle state) {
        state.putBoolean("queueSelecting", selecting);
        state.putLongArray("queueSelected", selected.stream().mapToLong(Long::longValue).toArray());
        state.putInt("queueIndex", list.getFirstVisiblePosition());
        View first = list.getChildAt(0);
        state.putInt("queueOffset", first == null ? 0 : first.getTop());
    }

    private int savedFilter() {
        int value = Profile.prefs(screen).getInt("queueFilter", 0);
        return value >= 0 && value < FILTERS.length ? value : 0;
    }

    void refresh() {
        int current = savedFilter();
        if (filter != current) {
            filter = current;
            applyFilter();
            if (!visible.isEmpty()) list.setSelection(0);
        }
        if (loading) return;
        loading = true;
        app.uploadControls.execute(() -> {
            try {
                List<UploadQueue.Job> next = UploadQueue.get(screen).listedJobs();
                Map<String, String> names = new HashMap<>();
                List<CloudConfig> configs = CloudConfig.all(screen);
                Map<String, Integer> counts = new HashMap<>();
                for (CloudConfig t : configs) counts.merge(t.label(), 1, Integer::sum);
                for (CloudConfig t : configs)
                    names.put(t.id(), counts.get(t.label()) > 1 ? t.uploadLabel() : t.label());
                var prefs = Profile.prefs(screen);
                android.net.ConnectivityManager cm =
                        screen.getSystemService(android.net.ConnectivityManager.class);
                var caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
                String wait =
                        prefs.getBoolean("wifiOnly", true)
                                        && (caps == null
                                                || !caps.hasTransport(
                                                        android.net.NetworkCapabilities
                                                                .TRANSPORT_WIFI))
                                ? "Wartet auf WLAN"
                                : caps == null ? "Wartet auf Netzwerk" : "";
                String config =
                        "Uploadziele · Automatik "
                                + (prefs.getBoolean("autoUpload", false) ? "an" : "aus")
                                + (prefs.getBoolean("wifiOnly", true) ? " · Nur WLAN" : "");
                screen.runOnUiThread(() -> {
                    loading = false;
                    if (screen.isDestroyed() || !list.isAttachedToWindow() || list.touching) return;
                    targets.setText(config);
                    if (!jobs.equals(next)
                            || !labels.equals(names)
                            || !waitingFor.equals(wait)
                            || empty.getText().length() == 0) {
                        waitingFor = wait;
                        jobs = next;
                        labels = names;
                        applyFilter();
                        if (restoreIndex > 0 || restoreOffset != 0) {
                            if (!visible.isEmpty())
                                list.setSelectionFromTop(Math.min(restoreIndex, visible.size() - 1), restoreOffset);
                            restoreIndex = restoreOffset = 0;
                        }
                    } else if (visible.stream()
                            .anyMatch(j -> j.state().equals("Wartet") && j.notBefore() > 0))
                        adapter.notifyDataSetChanged();
                });
            } catch (Exception e) {
                screen.runOnUiThread(() -> {
                    loading = false;
                    empty.setVisibility(View.VISIBLE);
                    empty.setText(Audomo.cause(e));
                });
            }
        });
    }

    private void applyFilter() {
        List<UploadQueue.Job> rows = new ArrayList<>();
        for (UploadQueue.Job job : jobs) {
            if (filter == 0
                    || filter == 1 && (job.needsAttention() ||
                            !(job.state().equals("Hochgeladen") || job.state().equals("Cloudkopie gelöscht")))
                    || filter == 2 && job.needsAttention()
                    || filter == 3 && job.state().equals("Wartet")) rows.add(job);
        }
        long now = System.currentTimeMillis();
        rows.sort(
                Comparator.comparingInt(
                                (UploadQueue.Job j) -> {
                                    if (j.state().equals("Upload läuft")) return 0;
                                    if (j.canPrioritize() && j.priority() > 0) return 1;
                                    if (!j.open()) return 5;
                                    if (j.state().equals("Konflikt")) return 4;
                                    return j.notBefore() > now ? 3 : 2;
                                })
                        .thenComparingLong(j -> j.canPrioritize() ? -j.priority() : 0)
                        .thenComparingLong(
                                j -> j.notBefore() > now && j.open() ? j.notBefore() : 0L)
                        .thenComparingLong(j -> j.open() ? j.id() : -j.id()));
        visible = rows;
        long open = jobs.stream().filter(UploadQueue.Job::open).count();
        long issues = jobs.stream().filter(UploadQueue.Job::needsAttention).count();
        totals.setText(open + " offen · " + issues + " mit Klärungsbedarf");
        selected.retainAll(
                rows.stream()
                        .map(UploadQueue.Job::id)
                        .collect(java.util.stream.Collectors.toSet()));
        updateControls();
        filterButton.setText(FILTERS[filter] + " ▾");
        filterButton.setContentDescription("Filter: " + FILTERS[filter]);
        empty.setText(
                jobs.isEmpty() ? "Noch keine Uploadaufträge" : "Keine Aufträge in dieser Ansicht");
        empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }

    private void manage() {
        PopupMenu menu = new PopupMenu(screen, manage);
        List<Long> finished =
                jobs.stream()
                        .filter(j -> !j.open())
                        .map(UploadQueue.Job::id)
                        .collect(java.util.stream.Collectors.toList());
        menu.getMenu()
                .add("Beendete Aufträge entfernen")
                .setEnabled(!finished.isEmpty())
                .setOnMenuItemClickListener(item -> {
                    confirmRemove(finished, false);
                    return true;
                });
        List<Long> all =
                jobs.stream()
                        .map(UploadQueue.Job::id)
                        .collect(java.util.stream.Collectors.toList());
        menu.getMenu()
                .add("Warteschlange leeren")
                .setEnabled(!all.isEmpty())
                .setOnMenuItemClickListener(item -> {
                    confirmRemove(all, true);
                    return true;
                });
        menu.show();
    }

    private void confirmRemove(List<Long> ids, boolean clear) {
        if (ids.isEmpty()) return;
        String title =
                clear
                        ? "Warteschlange leeren?"
                        : ids.size() == 1 ? "Auftrag entfernen?" : "Aufträge entfernen?";
        String amount =
                ids.size() == 1
                        ? "1 Auftrag wird entfernt."
                        : ids.size() + " Aufträge werden entfernt.";
        new AlertDialog.Builder(screen)
                .setTitle(title)
                .setMessage(amount + " Aufnahmen und Cloudkopien bleiben erhalten.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton(clear ? "Leeren" : "Entfernen", (d, w) -> remove(ids))
                .show();
    }

    private void remove(List<Long> ids) {
        change(() -> {
            UploadQueue.Removal result = UploadQueue.get(screen).remove(ids);
            String message =
                    result.removed() == 1
                            ? "1 Auftrag entfernt"
                            : result.removed() + " Aufträge entfernt";
            if (result.finishing() != 0) message += " · Abschlussprüfung am Ziel läuft noch";
            return message;
        });
    }

    private void prioritize(List<Long> ids) {
        change(() -> {
            int moved = UploadQueue.get(screen).prioritize(ids);
            if (moved == 0)
                return "Nur wartende oder fehlgeschlagene Aufträge können vorgezogen werden.";
            return moved == 1
                    ? "1 Auftrag an die Spitze gesetzt"
                    : moved + " Aufträge an die Spitze gesetzt";
        });
    }

    private void change(java.util.concurrent.Callable<String> action) {
        app.uploadControls.execute(() -> {
            String result;
            try {
                result = action.call();
            } catch (Exception e) {
                result = Audomo.cause(e);
            }
            String message = result;
            screen.runOnUiThread(() -> {
                if (screen.isDestroyed() || !list.isAttachedToWindow()) return;
                exitSelection();
                screen.toast(message);
                refresh();
            });
        });
    }

    private List<Long> selectedIds() {
        return visible.stream()
                .map(UploadQueue.Job::id)
                .filter(selected::contains)
                .collect(java.util.stream.Collectors.toList());
    }

    private void enterSelection() {
        selecting = true;
        updateSelection();
    }

    boolean exitSelection() {
        if (!selecting) return false;
        selecting = false;
        selected.clear();
        updateSelection();
        return true;
    }

    private void toggle(long id) {
        if (!selected.add(id)) selected.remove(id);
        updateSelection();
    }

    private void updateSelection() {
        updateControls();
        adapter.notifyDataSetChanged();
    }

    private void updateControls() {
        list.selectionMode = selecting;
        summary.setText(
                selecting
                        ? selected.size() + " ausgewählt"
                        : jobs.size() == 1 ? "1 Auftrag" : jobs.size() + " Aufträge");
        normalActions.setVisibility(selecting ? View.GONE : View.VISIBLE);
        targets.setVisibility(selecting ? View.GONE : View.VISIBLE);
        selectionActions.setVisibility(selecting ? View.VISIBLE : View.GONE);
        filterButton.setVisibility(selecting ? View.GONE : View.VISIBLE);
        cancelSelection.setVisibility(selecting ? View.VISIBLE : View.GONE);
        totals.setVisibility(!selecting && !jobs.isEmpty() ? View.VISIBLE : View.GONE);
        retryErrors.setVisibility(
                !selecting && jobs.stream().anyMatch(j -> j.state().equals("Fehler"))
                        ? View.VISIBLE
                        : View.GONE);
        select.setEnabled(!visible.isEmpty());
        boolean all = !visible.isEmpty() && selected.size() == visible.size();
        toggleAll.setImageResource(all ? R.drawable.ic_deselect_all : R.drawable.ic_select_all);
        toggleAll.setContentDescription(all ? "Alle abwählen" : "Alle auswählen");
        toggleAll.setTooltipText(toggleAll.getContentDescription());
        toggleAll.setEnabled(!visible.isEmpty());
        promoteSelected.setEnabled(
                visible.stream().anyMatch(j -> selected.contains(j.id()) && j.canPrioritize()));
        deleteSelected.setEnabled(!selected.isEmpty());
    }

    private class Rows extends BaseAdapter {
        public int getCount() {
            return visible.size();
        }

        public UploadQueue.Job getItem(int i) {
            return visible.get(i);
        }

        public long getItemId(int i) {
            return i >= 0 && i < visible.size() ? getItem(i).id() : AdapterView.INVALID_ROW_ID;
        }

        public boolean hasStableIds() {
            return true;
        }

        public View getView(int pos, View recycle, ViewGroup parent) {
            QueueSwipeList.Row row =
                    recycle instanceof QueueSwipeList.Row existing
                            ? existing
                            : new QueueSwipeList.Row(screen, new QueueRow());
            UploadQueue.Job job = getItem(pos);
            row.jobId = job.id();
            row.movable = job.canPrioritize();
            ((QueueRow) row.card).bind(job);
            return row;
        }
    }

    private class QueueRow extends LinearLayout {
        final TextView name, destination, status;
        final ImageView icon;
        final CheckBox check;
        final ProgressBar progress;

        QueueRow() {
            super(screen);
            setOrientation(VERTICAL);
            setPadding(screen.dp(14), screen.dp(9), screen.dp(14), screen.dp(9));
            setBackground(screen.surface(MainActivity.PANEL, 12));
            name = screen.text("", 17, MainActivity.INK);
            name.setMaxLines(2);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout top = screen.row();
            check = new CheckBox(screen);
            check.setClickable(false);
            check.setFocusable(false);
            top.addView(check);
            top.addView(name, screen.weight());
            addView(top);
            destination = screen.text("", 13, MainActivity.DIM);
            addView(destination);
            LinearLayout line = screen.row();
            icon = new ImageView(screen);
            line.addView(icon, screen.lp(16, 16));
            status = screen.text("", 14, MainActivity.ACCENT);
            status.setPadding(screen.dp(6), 0, 0, 0);
            line.addView(status, screen.weight());
            addView(line);
            progress = new ProgressBar(screen, null, android.R.attr.progressBarStyleHorizontal);
            addView(progress);
        }

        void bind(UploadQueue.Job job) {
            check.setVisibility(selecting ? View.VISIBLE : View.GONE);
            check.setChecked(selected.contains(job.id()));
            name.setText(RemoteFiles.leaf(job.path()));
            String target =
                    labels.getOrDefault(
                            job.target(),
                            job.provider().isEmpty() ? "Unbekanntes Ziel" : job.provider());
            String folder = RemoteFiles.parent(job.path());
            destination.setText(target + (folder.equals("/") ? "" : " · " + folder));
            boolean running = job.state().equals("Upload läuft");
            String message =
                    job.availability().isEmpty() || job.open()
                            ? CloudScreen.queueStatus(job)
                            : job.availability();
            if (message.equals("Wartet") && !waitingFor.isEmpty()) message = waitingFor;
            if (!job.error().isEmpty()) message += " · " + job.error();
            if (running) message += " · " + job.progress() + " %";
            status.setText(message);
            int color =
                    job.needsAttention()
                            ? 0xffffb2ab
                            : job.state().equals("Hochgeladen") ? 0xffa2cfaf : MainActivity.ACCENT;
            icon.setImageResource(
                    job.needsAttention()
                            ? R.drawable.ic_problem
                            : running
                                    ? R.drawable.ic_upload
                                    : job.open()
                                            ? R.drawable.ic_wait
                                            : job.state().equals("Hochgeladen")
                                                    ? R.drawable.ic_check
                                                    : R.drawable.ic_stop);
            icon.setColorFilter(color);
            status.setTextColor(color);
            progress.setVisibility(running ? View.VISIBLE : View.GONE);
            progress.setProgress(job.progress());
        }
    }
}
