package com.ktu.scholar;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

public class KtuScholarApp extends Application {

    public static final String CHANNEL_ANNOUNCEMENTS = "ktu_announcements";
    public static final String CHANNEL_REMINDERS     = "ktu_reminders";
    public static final String CHANNEL_SYNC          = "ktu_sync";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);

            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ANNOUNCEMENTS,
                    "KTU Announcements",
                    NotificationManager.IMPORTANCE_DEFAULT));

            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL_REMINDERS,
                    "Task Reminders",
                    NotificationManager.IMPORTANCE_HIGH));

            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL_SYNC,
                    "Background Sync",
                    NotificationManager.IMPORTANCE_LOW));
        }
    }
}
