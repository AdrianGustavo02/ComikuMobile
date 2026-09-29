package com.example.comiku.core.ui;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.example.comiku.R;

public final class StatusBarUtils {
    private static final String TAG_STATUS_BAR_SHADOW = "status_bar_shadow";
    private static final String STATUS_BAR_COLOR = "#12091D";

    private StatusBarUtils() {
    }

    public static void applyDarkStatusBar(@NonNull Activity activity) {
        Window window = activity.getWindow();
        if (window == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(Color.parseColor(STATUS_BAR_COLOR));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            View decorView = window.getDecorView();
            decorView.setSystemUiVisibility(decorView.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        addStatusBarShadow(activity);
    }

    private static void addStatusBarShadow(@NonNull Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }

        ViewGroup contentRoot = activity.findViewById(android.R.id.content);
        if (contentRoot == null) {
            return;
        }

        for (int i = 0; i < contentRoot.getChildCount(); i++) {
            View child = contentRoot.getChildAt(i);
            if (TAG_STATUS_BAR_SHADOW.equals(child.getTag())) {
                return;
            }
        }

        View sombra = new View(activity);
        sombra.setTag(TAG_STATUS_BAR_SHADOW);
        sombra.setFocusable(false);
        sombra.setClickable(false);
        sombra.setBackgroundResource(R.drawable.status_bar_shadow);

        int altoSombra = (int) (64 * activity.getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                altoSombra
        );
        params.topMargin = 0;
        sombra.setLayoutParams(params);
        sombra.setElevation(18f);
        sombra.setTranslationZ(18f);
        sombra.bringToFront();

        contentRoot.addView(sombra, contentRoot.getChildCount());
    }
}
