package com.ktu.scholar.worker;

import android.content.Context;
import android.app.NotificationManager;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.gson.Gson;
import com.ktu.scholar.KtuScholarApp;
import com.ktu.scholar.R;
import com.ktu.scholar.core.model.SemesterResult;
import com.ktu.scholar.core.model.SubjectAttendance;
import com.ktu.scholar.core.model.Announcement;
import com.ktu.scholar.core.service.*;
import com.ktu.scholar.data.db.*;
import com.ktu.scholar.util.CryptoUtil;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Periodic background sync for results, attendance, and announcements.
 * Scheduled every 6 hours via WorkManager.
 */
public class SyncWorker extends Worker {

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull @Override
    public Result doWork() {
        try {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            ProfileEntity p = db.profileDao().getProfile();

            if (p != null) {
                // Results
                if (p.ktuPasswordEnc != null) {
                    String pass = CryptoUtil.decrypt(p.ktuPasswordEnc, getApplicationContext());
                    List<SemesterResult> results =
                            new KtuPortalService().fetchResults(p.registerNumber, pass);
                    ResultsCacheEntity rc = new ResultsCacheEntity();
                    rc.registerNumber = p.registerNumber;
                    rc.dataJson = new Gson().toJson(results);
                    rc.syncedAt = Instant.now().toString();
                    db.resultsDao().upsert(rc);
                }
                // Attendance
                if (p.ezygoPasswordEnc != null) {
                    String pass = CryptoUtil.decrypt(p.ezygoPasswordEnc, getApplicationContext());
                    List<SubjectAttendance> att =
                            new EzygoService().fetchAttendance(p.registerNumber, pass);
                    AttendanceCacheEntity ac = new AttendanceCacheEntity();
                    ac.registerNumber = p.registerNumber;
                    ac.dataJson = new Gson().toJson(att);
                    ac.syncedAt = Instant.now().toString();
                    db.attendanceDao().upsert(ac);
                }
            }

            // Announcements (public — no login needed)
            List<Announcement> anns = new AnnouncementService().fetchAnnouncements();
            List<AnnouncementEntity> entities = new ArrayList<>();
            for (Announcement a : anns) {
                AnnouncementEntity e = new AnnouncementEntity();
                e.id = a.getId(); e.title = a.getTitle();
                e.content = a.getContent(); e.category = a.getCategory().name();
                e.date = a.getDate(); e.link = a.getLink();
                e.isNew = true; e.createdAt = Instant.now().toString();
                entities.add(e);
            }
            AppDatabase db2 = AppDatabase.getInstance(getApplicationContext());
            db2.announcementDao().deleteAll();
            db2.announcementDao().insertAll(entities);

            // Notify user of new announcements
            if (!entities.isEmpty()) postNotification(entities.get(0).title);

            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }

    private void postNotification(String firstTitle) {
        NotificationManager nm = (NotificationManager) getApplicationContext()
                .getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(9001,
                new NotificationCompat.Builder(getApplicationContext(), KtuScholarApp.CHANNEL_ANNOUNCEMENTS)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle("New KTU Announcement")
                        .setContentText(firstTitle)
                        .setAutoCancel(true)
                        .build());
    }
}
