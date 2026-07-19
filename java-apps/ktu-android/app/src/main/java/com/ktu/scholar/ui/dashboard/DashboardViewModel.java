package com.ktu.scholar.ui.dashboard;

import android.content.Context;
import androidx.lifecycle.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ktu.scholar.data.db.*;
import com.ktu.scholar.util.CryptoUtil;

import java.util.List;
import java.util.concurrent.Executors;

public class DashboardViewModel extends AndroidViewModel {

    private final AppDatabase db;
    private final MutableLiveData<Double>  cgpa         = new MutableLiveData<>();
    private final MutableLiveData<Double>  overallAtt   = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading    = new MutableLiveData<>(false);

    public DashboardViewModel(android.app.Application app) {
        super(app);
        db = AppDatabase.getInstance(app);
        // Load cached values
        Executors.newSingleThreadExecutor().execute(this::loadCached);
    }

    public LiveData<ProfileEntity>           getProfile()         { return db.profileDao().observeProfile(); }
    public LiveData<List<TodoEntity>>        getPendingTodos()    { return db.todoDao().observePending(); }
    public LiveData<List<AnnouncementEntity>> getAnnouncements()  { return db.announcementDao().observeAll(); }
    public LiveData<Double>                  getCgpa()            { return cgpa; }
    public LiveData<Double>                  getOverallAttendance(){ return overallAtt; }
    public LiveData<Boolean>                 getIsLoading()       { return isLoading; }

    private void loadCached() {
        // Results → CGPA
        ResultsCacheEntity rc = db.resultsDao().get();
        if (rc != null && rc.dataJson != null) {
            // Quick CGPA parse: look for "sgpa" values across semesters and average weighted
            // (Full parse lives in ktu-core; on Android we keep it simple)
            try {
                Gson gson = new Gson();
                var sems = gson.fromJson(rc.dataJson,
                        new TypeToken<java.util.List<java.util.Map<String, Object>>>(){}.getType());
                // Each sem has {semester, sgpa, totalCredits}
                double totalPoints = 0, totalCred = 0;
                for (var sem : sems) {
                    double tc  = ((Number) sem.getOrDefault("totalCredits", 0)).doubleValue();
                    double sg  = ((Number) sem.getOrDefault("sgpa", 0)).doubleValue();
                    totalPoints += sg * tc;
                    totalCred   += tc;
                }
                if (totalCred > 0) cgpa.postValue(Math.round(totalPoints/totalCred*100)/100.0);
            } catch (Exception ignored) {}
        }
        // Attendance → overall
        AttendanceCacheEntity ac = db.attendanceDao().get();
        if (ac != null && ac.dataJson != null) {
            try {
                Gson gson = new Gson();
                var subjects = gson.fromJson(ac.dataJson,
                        new TypeToken<java.util.List<java.util.Map<String, Object>>>(){}.getType());
                double total = 0, attended = 0;
                for (var s : subjects) {
                    total    += ((Number) s.getOrDefault("totalClasses", 0)).doubleValue();
                    attended += ((Number) s.getOrDefault("attendedClasses", 0)).doubleValue();
                }
                if (total > 0) overallAtt.postValue(Math.round(attended/total*1000)/10.0);
            } catch (Exception ignored) {}
        }
    }

    public void sync(Context ctx) {
        isLoading.setValue(true);
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                ProfileEntity p = db.profileDao().getProfile();
                if (p != null) {
                    // Import ktu-core services (after adding ktu-core.jar to libs/)
                    com.ktu.scholar.core.service.KtuPortalService ktu =
                            new com.ktu.scholar.core.service.KtuPortalService();
                    com.ktu.scholar.core.service.EzygoService ezygo =
                            new com.ktu.scholar.core.service.EzygoService();
                    com.ktu.scholar.core.service.AnnouncementService annSvc =
                            new com.ktu.scholar.core.service.AnnouncementService();

                    if (p.ktuPasswordEnc != null) {
                        String pass = CryptoUtil.decrypt(p.ktuPasswordEnc, ctx);
                        var results = ktu.fetchResults(p.registerNumber, pass);
                        ResultsCacheEntity rc = new ResultsCacheEntity();
                        rc.registerNumber = p.registerNumber;
                        rc.dataJson = new Gson().toJson(results);
                        rc.syncedAt = java.time.Instant.now().toString();
                        db.resultsDao().upsert(rc);
                    }
                    if (p.ezygoPasswordEnc != null) {
                        String pass = CryptoUtil.decrypt(p.ezygoPasswordEnc, ctx);
                        var att = ezygo.fetchAttendance(p.registerNumber, pass);
                        AttendanceCacheEntity ac = new AttendanceCacheEntity();
                        ac.registerNumber = p.registerNumber;
                        ac.dataJson = new Gson().toJson(att);
                        ac.syncedAt = java.time.Instant.now().toString();
                        db.attendanceDao().upsert(ac);
                    }
                    // Announcements
                    var anns = annSvc.fetchAnnouncements();
                    db.announcementDao().deleteAll();
                    var entities = new java.util.ArrayList<AnnouncementEntity>();
                    for (var a : anns) {
                        AnnouncementEntity e = new AnnouncementEntity();
                        e.id = a.getId(); e.title = a.getTitle();
                        e.content = a.getContent(); e.category = a.getCategory().name();
                        e.date = a.getDate(); e.link = a.getLink();
                        e.isNew = true; e.createdAt = java.time.Instant.now().toString();
                        entities.add(e);
                    }
                    db.announcementDao().insertAll(entities);
                }
            } catch (Exception ignored) {}
            isLoading.postValue(false);
            loadCached();
        });
    }
}
