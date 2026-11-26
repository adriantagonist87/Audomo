package de.audomo;

import android.graphics.Rect;
import android.view.*;
import android.widget.*;

final class DashboardGestures extends LinearLayout {
    private final MainActivity screen;
    private final int slop;
    private float startX, startY, origin;
    private int prepared;
    private VelocityTracker velocity;
    private boolean blockPageTap;
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

    private void track(MotionEvent event) {
        if (velocity == null) velocity = VelocityTracker.obtain();
        velocity.addMovement(event);
    }

    private void releaseTracker() {
        if (velocity != null) velocity.recycle();
        velocity = null;
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        DashboardPager pager = screen.pager();
        if (pager == null) return super.dispatchTouchEvent(event);
        float x = event.getRawX(), y = event.getRawY();
        float dx = x - startX, dy = y - startY;
        int current = screen.dashboardIndex();
        int next = current + (dx < 0 ? 1 : -1);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                releaseTracker();
                track(event);
                startX = x;
                startY = y;
                prepared = -1;
                swiping = false;
                blockPageTap = pager.position() != current && contains(pager, x, y);
                canSwipe = !screen.trashView && !handlesHorizontalDrag(this, x, y);
                outsideTap = screen.outsidePlayer(x, y);
            }
            case MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> {
                canSwipe = outsideTap = false;
                releaseTracker();
                if (swiping) {
                    pager.settle();
                    swiping = false;
                    blockPageTap = true;
                }
            }
            case MotionEvent.ACTION_MOVE -> {
                if (canSwipe) track(event);
                if (Math.abs(dx) > slop || Math.abs(dy) > slop) outsideTap = false;
                if (!swiping && Math.abs(dy) > slop && Math.abs(dy) >= Math.abs(dx)) canSwipe = false;
                if (!swiping && canSwipe && Math.abs(dx) > slop && Math.abs(dx) > 1.4f * Math.abs(dy)
                        && (pager.position() != current || next >= 0 && next <= 2)) {
                    if (!blockPageTap) cancelChild(event);
                    pager.interrupt();
                    origin = pager.position();
                    swiping = true;
                }
                if (swiping) {
                    float position = Math.max(current - 1, Math.min(current + 1,
                            origin - dx / Math.max(1, pager.getWidth())));
                    int neighbour = current + Float.compare(position, current);
                    if (neighbour != current && neighbour != prepared && neighbour >= 0 && neighbour <= 2) {
                        screen.preparePage(neighbour, null);
                        prepared = neighbour;
                    }
                    pager.move(position);
                    return true;
                }
            }
            case MotionEvent.ACTION_UP -> {
                if (swiping) {
                    track(event);
                    velocity.computeCurrentVelocity(1000);
                    float speed = -velocity.getXVelocity();
                    releaseTracker();
                    float offset = pager.position() - current;
                    boolean flick = Math.abs(speed) > screen.dp(500) && Math.abs(dx) > slop * 2;
                    boolean commit = flick ? speed * offset > 0 : Math.abs(offset) >= .33f;
                    swiping = false;
                    if (commit && offset != 0) screen.swipeTo(current + (offset > 0 ? 1 : -1));
                    else pager.settle();
                    return true;
                }
                releaseTracker();
                if (outsideTap && Math.abs(dx) <= slop && Math.abs(dy) <= slop
                        && event.getEventTime() - event.getDownTime() < ViewConfiguration.getLongPressTimeout()
                        && screen.outsidePlayer(x, y)) {
                    cancelChild(event);
                    screen.closePlayer();
                    return true;
                }
            }
        }
        return blockPageTap || super.dispatchTouchEvent(event);
    }
}
