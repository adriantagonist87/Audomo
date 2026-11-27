package de.audomo;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

import java.util.*;
import java.util.stream.Collectors;

final class QueueDashboard {
    private static final int ALL = 0, OPEN = 1, REVIEW = 2, WAITING = 3;
    private static final String[] FILTERS = {"Alle Aufträge", "Offene Aufträge", "Zu prüfen", "Wartend"};
    private static final String[] EMPTY = {"Noch keine Uploadaufträge", "Keine offenen Aufträge",
            "Keine Aufträge zu prüfen", "Keine wartenden Aufträge"};
    private final MainActivity screen;
    private final Audomo app;
    private final TextView summary, empty;
    private final Button filterButton;
    private String waitingFor = "";
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
    private boolean reviewingIssues;
    private String readMessage = "Aufträge werden geladen …";

    QueueDashboard(MainActivity screen, LinearLayout body, Bundle state) {
        this.screen = screen;
        app = (Audomo) screen.getApplication();
        reviewingIssues = state != null && state.getBoolean("queueReviewingIssues");
        filter = activeFilter();
        if (state != null) {
            selecting = state.getBoolean("queueSelecting");
            long[] saved = state.getLongArray("queueSelected");
            if (saved != null) for (long id : saved) selected.add(id);
            restoreIndex = state.getInt("queueIndex");
            restoreOffset = state.getInt("queueOffset");
        }
        list = new QueueSwipeList(screen, id -> remove(List.of(id), Removal.SELECTED), id -> prioritize(List.of(id)));
        LinearLayout heading = screen.row();
        heading.setPadding(0, screen.dp(12), 0, screen.dp(4));
        body.addView(heading);
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
                                                    reviewingIssues = false;
                                                    filter = i;
                                                    Profile.prefs(screen).edit().putInt("queueFilter", i).apply();
                                                    applyFilter();
                                                    if (!visible.isEmpty()) list.setSelection(0);
                                                    dialog.dismiss();
                                                })
                                        .setNegativeButton("Abbrechen", null)
                                        .show());
        filterButton.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        filterButton.setPadding(screen.dp(14), 0, screen.dp(10), 0);
        filterButton.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_expand, 0);
        heading.addView(filterButton, screen.weight());
        summary = screen.text("", 15, MainActivity.DIM);
        summary.setPadding(screen.dp(12), 0, screen.dp(4), 0);
        heading.addView(summary);
        manage = screen.toolbarIcon("Warteschlange verwalten", R.drawable.ic_more);
        manage.setOnClickListener(v -> manage());
        heading.addView(manage, screen.lp(48, 48));
        cancelSelection = screen.button("Abbrechen", v -> exitSelection());
        heading.addView(cancelSelection);
        empty = screen.text(readMessage, 19, MainActivity.INK);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, screen.dp(40), 0, screen.dp(12));
        body.addView(empty);
        normalActions = screen.row();
        normalActions.setPadding(0, screen.dp(4), 0, screen.dp(6));
        Button add = screen.button("Upload hinzufügen", v -> ManualUploadDialog.chooseRecordings(screen));
        add.setPadding(screen.dp(16), 0, screen.dp(16), 0);
        add.setCompoundDrawablePadding(screen.dp(8));
        add.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_upload, 0, 0, 0);
        normalActions.addView(add);
        select = screen.button("Auswählen", v -> enterSelection());
        LinearLayout.LayoutParams selectLayout = screen.lp(-2, -2);
        selectLayout.setMarginStart(screen.dp(8));
        normalActions.addView(select, selectLayout);
        body.addView(normalActions);
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
        deleteSelected = screen.button("Entfernen", v -> confirmRemove(selectedIds(), Removal.SELECTED));
        selectionActions.addView(promoteSelected, screen.weight());
        selectionActions.addView(deleteSelected, screen.weight());

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
        state.putBoolean("queueReviewingIssues", reviewingIssues);
        state.putBoolean("queueSelecting", selecting);
        state.putLongArray("queueSelected", selected.stream().mapToLong(Long::longValue).toArray());
        state.putInt("queueIndex", list.getFirstVisiblePosition());
        View first = list.getChildAt(0);
        state.putInt("queueOffset", first == null ? 0 : first.getTop());
    }

    private int activeFilter() {
        if (reviewingIssues) return REVIEW;
        int value = Profile.prefs(screen).getInt("queueFilter", OPEN);
        return value >= 0 && value < FILTERS.length ? value : OPEN;
    }

    void refresh() {
        int current = activeFilter();
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
                screen.runOnUiThread(() -> {
                    loading = false;
                    if (screen.isDestroyed() || !list.isAttachedToWindow() || list.touching) return;
                    if (!jobs.equals(next)
                            || !labels.equals(names)
                            || !waitingFor.equals(wait)
                            || readMessage != null) {
                        readMessage = null;
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
                    if (screen.isDestroyed() || !list.isAttachedToWindow()) return;
                    readMessage = "Warteschlange konnte nicht geladen werden. " + Audomo.cause(e);
                    jobs = List.of();
                    applyFilter();
                });
            }
        });
    }

    private void applyFilter() {
        List<UploadQueue.Job> rows = new ArrayList<>();
        for (UploadQueue.Job job : jobs) {
            if (switch (filter) {
                case OPEN -> !job.finished();
                case REVIEW -> job.needsAttention();
                case WAITING -> job.state().equals("Wartet");
                default -> true;
            }) rows.add(job);
        }
        long now = System.currentTimeMillis();
        rows.sort(
                Comparator.comparingInt(
                                (UploadQueue.Job j) -> {
                                    if (j.state().equals("Upload läuft")) return 0;
                                    if (j.canPrioritize() && j.priority() > 0) return 1;
                                    if (j.needsAttention()) return 2;
                                    if (j.finished()) return 5;
                                    return j.notBefore() > now ? 4 : 3;
                                })
                        .thenComparingLong(j -> j.canPrioritize() ? -j.priority() : 0)
                        .thenComparingLong(
                                j -> j.notBefore() > now && j.open() ? j.notBefore() : 0L)
                        .thenComparingLong(j -> j.open() ? j.id() : -j.id()));
        visible = rows;
        boolean hadSelection = !selected.isEmpty();
        selected.retainAll(
                rows.stream()
                        .map(UploadQueue.Job::id)
                        .collect(Collectors.toSet()));
        if (rows.isEmpty() || (hadSelection && selected.isEmpty())) selecting = false;
        updateControls();
        adapter.notifyDataSetChanged();
    }

    private void manage() {
        PopupMenu menu = new PopupMenu(screen, manage);
        menu.getMenu().add("Uploadziele").setOnMenuItemClickListener(item -> {
            new CloudScreen(screen).manage(List.of());
            return true;
        });
        List<Long> failed = visible.stream().filter(j -> j.state().equals("Fehler"))
                .map(UploadQueue.Job::id).collect(Collectors.toList());
        if (!failed.isEmpty())
            menu.getMenu().add("Fehlgeschlagene erneut versuchen").setOnMenuItemClickListener(item -> {
                change(() -> {
                    int count = UploadQueue.get(screen).retryErrors(failed);
                    return count == 1 ? "1 Auftrag freigegeben" : count + " Aufträge freigegeben";
                });
                return true;
            });
        if (filter == ALL && !visible.isEmpty()) {
            List<Long> finished = visible.stream().filter(UploadQueue.Job::finished)
                    .map(UploadQueue.Job::id).collect(Collectors.toList());
            if (!finished.isEmpty() && finished.size() < visible.size())
                menu.getMenu().add("Beendete Aufträge entfernen").setOnMenuItemClickListener(item -> {
                    confirmRemove(finished, Removal.FINISHED);
                    return true;
                });
            List<Long> all = visible.stream().map(UploadQueue.Job::id).collect(Collectors.toList());
            menu.getMenu().add("Warteschlange leeren").setOnMenuItemClickListener(item -> {
                confirmRemove(all, Removal.ALL);
                return true;
            });
        }
        menu.show();
    }

    private enum Removal { SELECTED, FINISHED, ALL }

    private void confirmRemove(List<Long> ids, Removal scope) {
        if (ids.isEmpty()) return;
        String title = switch (scope) {
            case ALL -> "Warteschlange leeren?";
            case FINISHED -> "Beendete Aufträge entfernen?";
            case SELECTED -> ids.size() == 1 ? "Auftrag entfernen?" : "Aufträge entfernen?";
        };
        String amount =
                ids.size() == 1
                        ? "1 Auftrag wird entfernt."
                        : ids.size() + " Aufträge werden entfernt.";
        new AlertDialog.Builder(screen)
                .setTitle(title)
                .setMessage(amount + " Aufnahmen und Cloudkopien bleiben erhalten.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton(scope == Removal.ALL ? "Leeren" : "Entfernen",
                        (d, w) -> remove(ids, scope))
                .show();
    }

    private void remove(List<Long> ids, Removal scope) {
        change(() -> {
            UploadQueue.Removal result = UploadQueue.get(screen).remove(ids, scope == Removal.FINISHED);
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
                .collect(Collectors.toList());
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
                        : visible.size() == 1 ? "1 Auftrag" : visible.size() + " Aufträge");
        summary.setVisibility(selecting || !visible.isEmpty() ? View.VISIBLE : View.GONE);
        filterButton.setText(FILTERS[filter]);
        filterButton.setContentDescription("Filter: " + FILTERS[filter]);
        empty.setText(readMessage == null ? EMPTY[filter] : readMessage);
        empty.setVisibility(visible.isEmpty() && !selecting ? View.VISIBLE : View.GONE);
        normalActions.setVisibility(selecting ? View.GONE : View.VISIBLE);
        normalActions.setGravity(visible.isEmpty() ? Gravity.CENTER : Gravity.START);
        manage.setVisibility(selecting ? View.GONE : View.VISIBLE);
        selectionActions.setVisibility(selecting ? View.VISIBLE : View.GONE);
        filterButton.setVisibility(selecting ? View.GONE : View.VISIBLE);
        cancelSelection.setVisibility(selecting ? View.VISIBLE : View.GONE);
        select.setVisibility(visible.isEmpty() ? View.GONE : View.VISIBLE);
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
            destination.setText(job.provider().equals("Dropbox") || target.startsWith("Dropbox/")
                    ? "Dropbox" + folder : target + (folder.equals("/") ? "" : " · " + folder));
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
