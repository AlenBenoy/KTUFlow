package com.ktu.scholar.receiver;

import android.app.*;
import android.content.*;
import androidx.core.app.NotificationCompat;
import com.ktu.scholar.KtuScholarApp;
import com.ktu.scholar.R;

/** Fires a local notification for todo reminders. */
public class ReminderReceiver extends BroadcastReceiver {

    public static final String EXTRA_TITLE = "reminder_title";
    public static final String EXTRA_ID    = "reminder_id";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String title = intent.getStringExtra(EXTRA_TITLE);
        int    id    = intent.getIntExtra(EXTRA_ID, 1);

        NotificationManager nm = (NotificationManager)
                ctx.getSystemService(Context.NOTIFICATION_SERVICE);

        Notification notif = new NotificationCompat.Builder(ctx, KtuScholarApp.CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("KTU Scholar Reminder")
                .setContentText(title != null ? title : "You have a pending task!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build();

        nm.notify(id, notif);
    }
}
