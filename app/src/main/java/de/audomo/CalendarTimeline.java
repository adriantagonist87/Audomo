package de.audomo;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.*;
import android.text.style.StyleSpan;
import android.view.*;
import android.widget.*;

import androidx.core.graphics.ColorUtils;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

final class CalendarTimeline extends LinearLayout {
    private static final DateTimeFormatter CLOCK =
            DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN);
    private final LinearLayout allDayList;
    private final ScrollView allDaySection, hours;
    private final FrameLayout grid;
    private final HorizontalScrollView horizontal;
    private final List<CalendarNames.Appointment> timed = new ArrayList<>();
    private final Consumer<CalendarNames.Event> pick;
    private final CalendarNames.Event chosen;
    private final ZoneId zone;
    private final long start, end;
    private final int pitch, gutter;
    private int restoreY;

    CalendarTimeline(
            Context context,
            LocalDate date,
            ZoneId zone,
            List<CalendarNames.Appointment> events,
            CalendarNames.Event chosen,
            int restoreY,
            Consumer<CalendarNames.Event> pick) {
        super(context);
        this.pick = pick;
        this.chosen = chosen;
        this.zone = zone;
        this.restoreY = restoreY;
        start = date.atStartOfDay(zone).toInstant().toEpochMilli();
        end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        pitch = dp(84);
        gutter = dp(50);
        setOrientation(VERTICAL);
        allDayList = new LinearLayout(context);
        allDayList.setOrientation(VERTICAL);
        allDaySection = new ScrollView(context);
        allDaySection.addView(allDayList);
        addView(allDaySection, new LinearLayout.LayoutParams(-1, -2));
        boolean first = true;
        for (CalendarNames.Appointment item : events) {
            if (!item.event().allDay()) {
                timed.add(item);
                continue;
            }
            if (first) {
                allDayList.addView(label("Ganztägig", 13, MainActivity.DIM));
                first = false;
            }
            allDayList.addView(card(item, true), new LinearLayout.LayoutParams(-1, -2));
        }
        horizontal = new HorizontalScrollView(context);
        horizontal.setFillViewport(true);
        grid = new FrameLayout(context);
        horizontal.addView(grid);
        allDaySection.setVisibility(first ? GONE : VISIBLE);
        hours = new ScrollView(context);
        hours.addView(horizontal);
        addView(hours, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    int scrollPosition() {
        return hours.getScrollY();
    }

    @Override
    protected void onMeasure(int width, int height) {
        if (allDaySection.getVisibility() == VISIBLE) {
            allDayList.measure(width, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            allDaySection.getLayoutParams().height =
                    Math.min(allDayList.getMeasuredHeight(), MeasureSpec.getSize(height) / 3);
        }
        super.onMeasure(width, height);
    }

    private int dp(float n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private TextView label(CharSequence value, int sp, int color) {
        TextView text = new TextView(getContext());
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(color);
        return text;
    }

    private long clamp(long millis) {
        return Math.max(start, Math.min(end, millis));
    }

    private int y(long millis) {
        return (int) Math.round((clamp(millis) - start) / 3600000.0 * pitch);
    }

    private String clock(long millis) {
        return CLOCK.format(Instant.ofEpochMilli(millis).atZone(zone));
    }

    private boolean selected(CalendarNames.Event event) {
        return chosen != null
                && chosen.id() == event.id()
                && chosen.calendar() == event.calendar()
                && chosen.begin() == event.begin();
    }

    private TextView card(CalendarNames.Appointment item, boolean allDay) {
        CalendarNames.Event event = item.event();
        String title =
                event.title() == null || event.title().isBlank()
                        ? "Termin ohne Titel"
                        : event.title();
        String times =
                allDay
                        ? "Ganztägig"
                        : clock(clamp(event.begin()))
                                + "–"
                                + (event.end() >= end ? "24:00" : clock(clamp(event.end())));
        String calendar = event.calendarName() == null ? "" : event.calendarName();
        SpannableStringBuilder lines = new SpannableStringBuilder();
        if (!allDay) lines.append(times).append("\n");
        int at = lines.length();
        lines.append(title);
        lines.setSpan(
                new StyleSpan(Typeface.BOLD), at, lines.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (!calendar.isBlank()) lines.append(" · ").append(calendar);
        if (item.location() != null && !item.location().isBlank())
            lines.append("\n").append(item.location());
        int color = ColorUtils.setAlphaComponent(item.color(), 255);
        int fill = ColorUtils.blendARGB(MainActivity.PANEL, color, .52f);
        int ink =
                ColorUtils.calculateContrast(Color.WHITE, fill) >= 4.5 ? Color.WHITE : Color.BLACK;
        TextView view = label(lines, 14, ink);
        view.setPadding(dp(7), dp(3), dp(6), dp(3));
        view.setIncludeFontPadding(false);
        view.setGravity(Gravity.TOP);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setMinHeight(dp(48));
        GradientDrawable background = new GradientDrawable();
        background.setColor(fill);
        background.setCornerRadius(dp(5));
        background.setStroke(
                dp(selected(event) ? 2 : 1), selected(event) ? MainActivity.INK : color);
        view.setBackground(background);
        view.setSelected(selected(event));
        view.setContentDescription(
                title
                        + ", "
                        + times
                        + ", "
                        + calendar
                        + (item.location() == null || item.location().isBlank()
                                ? ""
                                : ", " + item.location()));
        view.setFocusable(true);
        view.setOnClickListener(v -> pick.accept(event));
        return view;
    }

    private record Block(CalendarNames.Appointment item, int top, int bottom, int lane) {}

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width > 0 && width != oldWidth) post(() -> layoutDay(width));
    }

    private void place(View view, int left, int top, int width, int height) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height);
        lp.leftMargin = left;
        lp.topMargin = top;
        grid.addView(view, lp);
    }

    private void layoutDay(int width) {
        grid.removeAllViews();
        List<List<Block>> groups = new ArrayList<>();
        List<Block> group = new ArrayList<>();
        List<Integer> laneEnds = new ArrayList<>();
        int groupEnd = -1, maxLanes = 1;
        int minimum =
                Math.max(
                        dp(48),
                        (int) (34 * getResources().getDisplayMetrics().scaledDensity) + dp(8));
        for (var item : timed) {
            int top = y(item.event().begin()),
                    bottom = Math.max(y(item.event().end()), top + minimum);
            if (top >= groupEnd) {
                group = new ArrayList<>();
                groups.add(group);
                laneEnds.clear();
            }
            int lane = 0;
            while (lane < laneEnds.size() && laneEnds.get(lane) > top) lane++;
            if (lane == laneEnds.size()) laneEnds.add(bottom);
            else laneEnds.set(lane, bottom);
            group.add(new Block(item, top, bottom, lane));
            groupEnd = Math.max(groupEnd, bottom);
            maxLanes = Math.max(maxLanes, laneEnds.size());
        }
        int available = Math.max(dp(80), width - gutter);
        int totalWidth = gutter + Math.max(available, maxLanes * dp(96));
        int height = Math.max(y(end) + dp(20), groupEnd + dp(4));
        grid.setLayoutParams(new FrameLayout.LayoutParams(totalWidth, height));
        for (long point = start; point < end; point += 3600000L) {
            String hour = clock(point);
            TextView time = label(hour, 12, MainActivity.DIM);
            ZonedDateTime local = Instant.ofEpochMilli(point).atZone(zone);
            if (zone.getRules().getValidOffsets(local.toLocalDateTime()).size() > 1)
                time.setText(hour + "\n" + local.getOffset());
            place(time, 0, y(point), gutter, -2);
            View rule = new View(getContext());
            rule.setBackgroundColor(MainActivity.LINE);
            place(rule, gutter, y(point), totalWidth - gutter, dp(1));
        }
        for (var cluster : groups) {
            int lanes = cluster.stream().mapToInt(Block::lane).max().orElse(0) + 1;
            int span = Math.max(available, lanes * dp(96));
            for (Block b : cluster) {
                int left = b.lane() * span / lanes;
                TextView event = card(b.item(), false);
                int h = b.bottom() - b.top() - dp(2);
                event.setMaxLines(Math.max(2, (h - dp(6)) / Math.max(1, event.getLineHeight())));
                place(
                        event,
                        gutter + left + dp(1),
                        b.top() + dp(1),
                        (b.lane() + 1) * span / lanes - left - dp(2),
                        h);
            }
        }
        if (restoreY != -2) {
            int scroll = restoreY;
            if (scroll < 0)
                scroll =
                        timed.isEmpty()
                                ? 0
                                : Math.max(0, y(timed.get(0).event().begin()) - pitch / 2);
            int initial = scroll;
            restoreY = -2;
            hours.post(() -> hours.scrollTo(0, initial));
        }
    }
}
