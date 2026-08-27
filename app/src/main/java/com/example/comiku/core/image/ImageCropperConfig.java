package com.example.comiku.core.image;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

public final class ImageCropperConfig {
    public static final String EXTRA_INPUT_URI = "extra_input_uri";
    public static final String EXTRA_OUTPUT_FILENAME = "extra_output_filename";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_ASPECT_X = "extra_aspect_x";
    public static final String EXTRA_ASPECT_Y = "extra_aspect_y";
    public static final String EXTRA_RESULT_URI = "extra_result_uri";

    public static final int DEFAULT_ASPECT_X = 1;
    public static final int DEFAULT_ASPECT_Y = 1;

    private ImageCropperConfig() {
    }


    public static Intent createIntent(
            Context contexto,
            Uri inputUri,
            String outputFileName,
            String title,
            int aspectX,
            int aspectY
    ) {
        Intent intent = new Intent(contexto, com.example.comiku.screens.ImageCropperActivity.class);
        intent.putExtra(EXTRA_INPUT_URI, inputUri != null ? inputUri.toString() : "");
        intent.putExtra(EXTRA_OUTPUT_FILENAME, outputFileName);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_ASPECT_X, aspectX);
        intent.putExtra(EXTRA_ASPECT_Y, aspectY);
        return intent;
    }
}
