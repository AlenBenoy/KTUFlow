package com.ktu.scholar.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.work.*;
import com.ktu.scholar.worker.SyncWorker;

import java.util.concurrent.TimeUnit;

/** Re-schedules background sync after device reboot. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            PeriodicWorkRequest sync = new PeriodicWorkRequest.Builder(
                    SyncWorker.class, 6, TimeUnit.HOURS)
                    .setConstraints(new Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build())
                    .build();
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "ktu_sync", ExistingPeriodicWorkPolicy.REPLACE, sync);
        }
    }
}
