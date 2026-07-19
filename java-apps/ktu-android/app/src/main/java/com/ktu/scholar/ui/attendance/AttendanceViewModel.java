package com.ktu.scholar.ui.attendance;

import android.content.Context;
import androidx.lifecycle.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ktu.scholar.core.model.SubjectAttendance;
import com.ktu.scholar.core.service.EzygoService;
import com.ktu.scholar.data.db.*;
import com.ktu.scholar.util.CryptoUtil;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;

public class AttendanceViewModel extends AndroidViewModel {

    private final AppDatabase db;
    private final MutableLiveData<List<SubjectAttendance>> subjects   = new MutableLiveData<>();
    private final MutableLiveData<Double>                  overall    = new MutableLiveData<>();
    private final MutableLiveData<Boolean>                 isLoading  = new MutableLiveData<>(false);

    public AttendanceViewModel(android.app.Application app) {
        super(app);
        db = AppDatabase.getInstance(app);
        Executors.newSingleThreadExecutor().execute(this::loadFromCache);
    }

    public LiveData<List<SubjectAttendance>> getSubjects()         { return subjects; }
    public LiveData<Double>                  getOverallPercentage(){ return overall; }
    public LiveData<Boolean>                 getIsLoading()        { return isLoading; }

    private void loadFromCache() {
        AttendanceCacheEntity cached = db.attendanceDao().get();
        if (cached == null || cached.dataJson == null) return;
        try {
            List<SubjectAttendance> list = new Gson().fromJson(cached.dataJson,
                    new TypeToken<List<SubjectAttendance>>() {}.getType());
            subjects.postValue(list);
            double tot = list.stream().mapToInt(SubjectAttendance::getTotalClasses).sum();
            double att = list.stream().mapToInt(SubjectAttendance::getAttendedClasses).sum();
            if (tot > 0) overall.postValue(Math.round(att/tot*1000)/10.0);
        } catch (Exception ignored) {}
    }

    public void sync(Context ctx) {
        isLoading.setValue(true);
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                ProfileEntity p = db.profileDao().getProfile();
                if (p == null || p.ezygoPasswordEnc == null) return;
                String pass = CryptoUtil.decrypt(p.ezygoPasswordEnc, ctx);
                List<SubjectAttendance> att =
                        new EzygoService().fetchAttendance(p.registerNumber, pass);
                AttendanceCacheEntity ac = new AttendanceCacheEntity();
                ac.registerNumber = p.registerNumber;
                ac.dataJson = new Gson().toJson(att);
                ac.syncedAt = Instant.now().toString();
                db.attendanceDao().upsert(ac);
                loadFromCache();
            } catch (Exception ignored) {}
            isLoading.postValue(false);
        });
    }
}
