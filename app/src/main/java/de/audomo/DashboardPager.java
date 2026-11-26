package de.audomo;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Three live pages share a viewport. Position is in page widths, including during a drag. */
final class DashboardPager extends FrameLayout {
    private static final PathInterpolator SETTLE = new PathInterpolator(.2f, 0f, 0f, 1f);
    final LinearLayout[] pages = new LinearLayout[3];
    private final Strip strip;
    private final Runnable rested;
    private ValueAnimator animator;
    private float position;
    private int selected;

    DashboardPager(MainActivity screen, Strip strip, int initial, Runnable rested) {
        super(screen);
        this.strip = strip;
        this.rested = rested;
        for (int i = 0; i < pages.length; i++) {
            pages[i] = screen.column();
            pages[i].setPadding(screen.dp(20), 0, screen.dp(20), 0);
            addView(pages[i], new LayoutParams(-1, -1));
        }
        select(initial);
        move(initial);
    }

    float position() { return position; }

    void select(int index) {
        selected = index;
        for (int i = 0; i < pages.length; i++) {
            pages[i].setImportantForAccessibility(i == index ? IMPORTANT_FOR_ACCESSIBILITY_AUTO
                    : IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            pages[i].setDescendantFocusability(i == index ? FOCUS_AFTER_DESCENDANTS : FOCUS_BLOCK_DESCENDANTS);
            strip.tabs[i].setSelected(i == index);
        }
    }

    void interrupt() {
        if (animator != null) animator.cancel();
    }

    void move(float next) {
        position = Math.max(0, Math.min(pages.length - 1, next));
        for (int i = 0; i < pages.length; i++) {
            float offset = i - position;
            pages[i].setTranslationX(offset * getWidth());
            pages[i].setVisibility(Math.abs(offset) < 1f ? VISIBLE : INVISIBLE);
        }
        strip.move(position);
        if (position == selected) postOnAnimation(rested);
    }

    void settle() {
        interrupt();
        float remaining = Math.abs(selected - position);
        if (remaining == 0 || !ValueAnimator.areAnimatorsEnabled() || getWidth() == 0) {
            move(selected);
            return;
        }
        animator = ValueAnimator.ofFloat(position, selected);
        animator.setDuration(150 + Math.round(100 * Math.min(remaining, 2)));
        animator.setInterpolator(SETTLE);
        animator.addUpdateListener(frame -> move((float)frame.getAnimatedValue()));
        animator.start();
    }

    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        move(position);
    }

    @Override protected void onDetachedFromWindow() {
        interrupt();
        super.onDetachedFromWindow();
    }

    static final class Strip extends View {
        final TextView[] tabs;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int padding, thickness;
        private float position;

        Strip(MainActivity screen, TextView... tabs) {
            super(screen);
            this.tabs = tabs;
            padding = screen.dp(5);
            thickness = screen.dp(3);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void move(float next) {
            position = next;
            for (int i = 0; i < tabs.length; i++) {
                float weight = Math.max(0, 1f - Math.abs(i - position));
                int color = androidx.core.graphics.ColorUtils.blendARGB(MainActivity.DIM, MainActivity.ACCENT, weight);
                if (tabs[i].getCurrentTextColor() != color) tabs[i].setTextColor(color);
            }
            invalidate();
        }

        private float extent(TextView tab) {
            return Math.min(tab.getWidth(), tab.getPaint().measureText(tab.getText().toString()) + 2 * padding);
        }

        @Override protected void onDraw(Canvas canvas) {
            int i = (int)position;
            TextView from = tabs[i], to = tabs[Math.min(i + 1, tabs.length - 1)];
            float t = position - i;
            float center = from.getLeft() + from.getWidth() * .5f;
            center += (to.getLeft() + to.getWidth() * .5f - center) * t;
            float half = (extent(from) * (1 - t) + extent(to) * t) * .5f;
            paint.setColor(MainActivity.LINE);
            canvas.drawRect(0, getHeight() - 1, getWidth(), getHeight(), paint);
            paint.setColor(MainActivity.ACCENT);
            canvas.drawRoundRect(center - half, getHeight() - thickness, center + half, getHeight(),
                    thickness * .5f, thickness * .5f, paint);
        }
    }
}
