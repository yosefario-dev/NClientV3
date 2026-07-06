package com.yosefario.nclientv3.utility;

import android.app.Activity;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public final class InsetUtils {
    private InsetUtils() {
    }

    public interface BarInsetListener {
        void onInsets(Insets bars);
    }

    public static void onSystemBarInsets(Activity activity, BarInsetListener listener) {
        View content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            listener.onInsets(insets.getInsets(
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()));
            return insets;
        });
        ViewCompat.requestApplyInsets(content);
    }

    public static void padBottomAndSides(View view, Insets bars) {
        view.setPadding(bars.left, view.getPaddingTop(), bars.right, bars.bottom);
    }
}
