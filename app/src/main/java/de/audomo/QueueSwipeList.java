package de.audomo;

import android.graphics.Color;
import android.view.*;
import android.widget.*;

import java.util.function.LongConsumer;

final class QueueSwipeList extends ListView {
    static final class Row extends FrameLayout {
        final TextView hint;
        final View card;
        long jobId;
        boolean movable;

        Row(MainActivity screen, View card) {
            super(screen);
            this.card = card;
            hint = screen.text("", 14, Color.WHITE);
            hint.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            hint.setPadding(screen.dp(14), 0, screen.dp(14), 0);
            addView(hint, new LayoutParams(-1, -1));
            addView(card);
            reset();
        }

        void drag(float dx) {
            boolean right = dx > 0;
            hint.setGravity((right ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL);
            hint.setBackgroundColor(
                    right ? (movable ? 0xff59417f : MainActivity.LINE) : 0xff853c48);
            hint.setText(right ? (movable ? "An die Spitze" : "Nicht verschiebbar") : "Entfernen");
            hint.setVisibility(VISIBLE);
            card.setTranslationX(dx);
        }

        void reset() {
            card.setTranslationX(0);
            hint.setVisibility(INVISIBLE);
        }
    }

    private final LongConsumer remove, promote;
    private final int slop;
    boolean selectionMode;
    boolean touching;
    private boolean swiping, vertical;
    private float x, y;
    private long downId;
    private Row row;

    QueueSwipeList(MainActivity screen, LongConsumer remove, LongConsumer promote) {
        super(screen);
        this.remove = remove;
        this.promote = promote;
        slop = ViewConfiguration.get(screen).getScaledTouchSlop();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        float dx = e.getX() - x, dy = e.getY() - y;
        switch (action) {
            case MotionEvent.ACTION_DOWN -> {
                resetGesture();
                touching = true;
                x = e.getX();
                y = e.getY();
                int index = pointToPosition((int) x, (int) y);
                View child =
                        index == INVALID_POSITION
                                ? null
                                : getChildAt(index - getFirstVisiblePosition());
                if (!selectionMode && child instanceof Row item) {
                    row = item;
                    downId = item.jobId;
                }
            }
            case MotionEvent.ACTION_POINTER_DOWN -> {
                if (swiping) {
                    resetGesture();
                    return true;
                }
                vertical = true;
            }
            case MotionEvent.ACTION_MOVE -> {
                if (!selectionMode && !vertical && row != null && row.jobId == downId) {
                    if (!swiping && Math.abs(dy) > slop && Math.abs(dy) >= Math.abs(dx))
                        vertical = true;
                    if (!swiping && Math.abs(dx) > slop && Math.abs(dx) > 1.5f * Math.abs(dy)) {
                        MotionEvent cancel = MotionEvent.obtain(e);
                        cancel.setAction(MotionEvent.ACTION_CANCEL);
                        super.dispatchTouchEvent(cancel);
                        cancel.recycle();
                        swiping = true;
                    }
                    if (swiping) {
                        row.drag(Math.max(-getWidth() * .85f, Math.min(getWidth() * .85f, dx)));
                        return true;
                    }
                }
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                boolean handled = swiping;
                boolean apply =
                        handled
                                && row != null
                                && row.jobId == downId
                                && action == MotionEvent.ACTION_UP
                                && Math.abs(dx) >= Math.max(slop * 3, row.getWidth() * .27f);
                long id = downId;
                resetGesture();
                if (apply) (dx < 0 ? remove : promote).accept(id);
                if (handled) return true;
            }
        }
        return super.dispatchTouchEvent(e);
    }

    private void resetGesture() {
        if (row != null) row.reset();
        row = null;
        touching = swiping = vertical = false;
    }

    @Override
    protected void onDetachedFromWindow() {
        resetGesture();
        super.onDetachedFromWindow();
    }
}
