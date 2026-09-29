package com.example.comiku.screens;

import android.content.Context;
import android.widget.ListPopupWindow;
import android.widget.Spinner;

import com.example.comiku.R;

public class LimitedHeightSpinner extends Spinner {
    private static final int MAX_HEIGHT_DP = 360;
    private static final int ITEM_HEIGHT_DP = 42;

    public LimitedHeightSpinner(Context context) {
        super(context);
    }

    @Override
    public boolean performClick() {
        if (getAdapter() == null) {
            return super.performClick();
        }

        final ListPopupWindow popup = new ListPopupWindow(getContext());
        popup.setAnchorView(this);
        popup.setModal(true);
        popup.setAdapter((android.widget.ListAdapter) getAdapter());
        popup.setBackgroundDrawable(getResources().getDrawable(R.drawable.bg_library_genre_dropdown));
        popup.setOnItemClickListener((parent, view, position, id) -> {
            setSelection(position);
            if (getOnItemSelectedListener() != null) {
                getOnItemSelectedListener().onItemSelected(parent, view, position, id);
            }
            popup.dismiss();
        });

        int itemCount = getAdapter().getCount();
        int alturaMaxima = Math.min(dpToPx(MAX_HEIGHT_DP), itemCount * dpToPx(ITEM_HEIGHT_DP));
        popup.setHeight(Math.max(alturaMaxima, dpToPx(ITEM_HEIGHT_DP)));
        popup.show();
        return true;
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
