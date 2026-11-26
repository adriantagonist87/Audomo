package de.audomo;

import android.graphics.Rect;
import android.view.*;
import android.widget.*;

final class DashboardGestures extends LinearLayout {
    private final MainActivity screen;
    private final int slop;
    private float startX, startY;
    private boolean canSwipe, swiping, outsideTap;

    DashboardGestures(MainActivity screen) {
        super(screen);
        this.screen = screen;
        slop = ViewConfiguration.get(screen).getScaledTouchSlop();
        setOrientation(VERTICAL);
        setClickable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    static boolean contains(View view, float x, float y) {
        if (view == null || !view.isShown()) return false;
        Rect bounds = new Rect();
        if (!view.getLocalVisibleRect(bounds)) return false;
        int[] origin = new int[2];
        view.getLocationOnScreen(origin);
        return bounds.contains((int)x - origin[0], (int)y - origin[1]);
    }

    private boolean handlesHorizontalDrag(View view, float x, float y) {
        if (!contains(view, x, y)) return false;
        if (view instanceof SeekBar || view instanceof EditText || view instanceof QueueSwipeList.Row)
            return true;
        if (view instanceof ViewGroup group) {
            for (int i = group.getChildCount() - 1; i >= 0; i--)
                if (handlesHorizontalDrag(group.getChildAt(i), x, y)) return true;
        }
        return false;
    }

    private void cancelChild(MotionEvent original) {
        MotionEvent cancel = MotionEvent.obtain(original);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        super.dispatchTouchEvent(cancel);
        cancel.recycle();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        float x = event.getRawX(), y = event.getRawY();
        float dx = x - startX, dy = y - startY;
        int next = screen.dashboardIndex() + (dx < 0 ? 1 : -1);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                startX = x;
                startY = y;
                swiping = false;
                canSwipe = !screen.trashView && !handlesHorizontalDrag(this, x, y);
                outsideTap = screen.outsidePlayer(x, y);
            }
            case MotionEvent.ACTION_POINTER_DOWN -> {
                outsideTap = canSwipe = false;
                if (swiping) return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                if (swiping) return true;
                if (Math.abs(dx) > slop || Math.abs(dy) > slop) outsideTap = false;
                if (Math.abs(dy) > slop && Math.abs(dy) >= Math.abs(dx)) canSwipe = false;
                if (canSwipe && next >= 0 && next <= 2 && Math.abs(dx) > slop * 2
                        && Math.abs(dx) > 1.6f * Math.abs(dy)) {
                    cancelChild(event);
                    swiping = true;
                    return true;
                }
            }
            case MotionEvent.ACTION_UP -> {
                if (swiping) {
                    if (canSwipe && Math.abs(dx) >= Math.max(screen.dp(52), getWidth() * .14f)
                            && Math.abs(dx) > 1.5f * Math.abs(dy)) screen.swipeTo(next);
                    return true;
                }
                if (outsideTap && Math.abs(dx) <= slop && Math.abs(dy) <= slop
                        && event.getEventTime() - event.getDownTime() < ViewConfiguration.getLongPressTimeout()
                        && screen.outsidePlayer(x, y)) {
                    cancelChild(event);
                    screen.closePlayer();
                    return true;
                }
            }
            case MotionEvent.ACTION_CANCEL -> { if (swiping) return true; }
        }
        return super.dispatchTouchEvent(event);
    }
}
