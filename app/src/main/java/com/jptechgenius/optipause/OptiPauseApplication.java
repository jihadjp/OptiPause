package com.jptechgenius.optipause;

import android.app.Application;
import android.os.Build;

import com.google.android.material.color.DynamicColors;

/**
 * OptiPauseApplication
 *
 * Application subclass that applies Material 3 dynamic color (Monet)
 * on devices running Android 12+ (API 31+).
 *
 * On older devices the static seed-color palette from colors.xml is used.
 * No other global state is initialised here to keep startup time minimal.
 */
public class OptiPauseApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Apply Monet / dynamic color system-wide on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyToActivitiesIfAvailable(this);
        }
    }
}
