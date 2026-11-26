package de.audomo;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.util.List;
import java.util.function.IntFunction;

/** The same field and anchored menu are used throughout the profile settings. */
final class ProfileDropdown<T> extends ArrayAdapter<T> {
    private final Spinner field;
    private final IntFunction<String> caption;

    ProfileDropdown(Spinner field, List<T> choices, IntFunction<String> caption) {
        super(field.getContext(), android.R.layout.simple_spinner_item, choices);
        this.field = field;
        this.caption = caption;
        field.setBackground(new RippleDrawable(ColorStateList.valueOf(0x337e61aa),
                shape(0xff1c182e, 0xff615674, 9), null));
        GradientDrawable popup = shape(0xff28243b, 0xff655975, 12);
        popup.setPadding(dp(5), dp(5), dp(5), dp(5));
        field.setPopupBackgroundDrawable(popup);
        field.setPadding(dp(5), 0, dp(5), 0);
        field.setDropDownWidth(ViewGroup.LayoutParams.MATCH_PARENT);
        field.setDropDownVerticalOffset(dp(4));
    }

    @Override public View getView(int position, View recycled, ViewGroup parent) {
        return row(position, false);
    }

    @Override public View getDropDownView(int position, View recycled, ViewGroup parent) {
        return row(position, true);
    }

    private View row(int position, boolean menu) {
        T item = getItem(position);
        boolean selected = menu && position == field.getSelectedItemPosition();
        LinearLayout line = new LinearLayout(getContext());
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setMinimumHeight(dp(48));
        line.setPadding(dp(12), dp(6), dp(12), dp(6));
        if (selected) {
            line.setBackground(shape(0xff463859, 0, 8));
            line.setStateDescription("Ausgewählt");
        }
        LinearLayout labels = new LinearLayout(getContext());
        labels.setOrientation(LinearLayout.VERTICAL);
        line.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        TextView title = new TextView(getContext());
        title.setText(item.toString());
        title.setTextSize(16);
        title.setTextColor(MainActivity.INK);
        title.setMaxLines(menu ? 2 : 1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        if (selected) title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        labels.addView(title);
        if (menu && caption != null) {
            TextView detail = new TextView(getContext());
            detail.setText(caption.apply(position));
            detail.setTextSize(13);
            detail.setTextColor(MainActivity.DIM);
            labels.addView(detail);
        }
        ImageView icon = new ImageView(getContext());
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        icon.setImageResource(menu ? R.drawable.ic_check : R.drawable.ic_expand);
        icon.setColorFilter(MainActivity.ACCENT);
        icon.setVisibility(menu && !selected ? View.INVISIBLE : View.VISIBLE);
        LinearLayout.LayoutParams end = new LinearLayout.LayoutParams(dp(20), dp(20));
        end.setMarginStart(dp(8));
        line.addView(icon, end);
        return line;
    }

    private int dp(int n) { return Math.round(n * getContext().getResources().getDisplayMetrics().density); }

    private GradientDrawable shape(int fill, int border, int corner) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(corner));
        if (border != 0) drawable.setStroke(dp(1), border);
        return drawable;
    }
}
