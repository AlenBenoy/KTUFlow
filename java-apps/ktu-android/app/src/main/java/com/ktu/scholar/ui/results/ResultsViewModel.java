package com.ktu.scholar.ui.results;

import android.content.Context;
import androidx.lifecycle.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ktu.scholar.core.model.SemesterResult;
import com.ktu.scholar.core.service.GradeCalculatorService;
import com.ktu.scholar.core.service.KtuPortalService;
import com.ktu.scholar.data.db.*;
import com.ktu.scholar.util.CryptoUtil;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;

public class ResultsViewModel extends AndroidViewModel {

    private final AppDatabase db;
    private final MutableLiveData<List<SemesterResult>> semesters      = new MutableLiveData<>();
    private final MutableLiveData<Double>               cgpa           = new MutableLiveData<>();
    private final MutableLiveData<Double>               percentage     = new MutableLiveData<>();
    private final MutableLiveData<String>               classification = new MutableLiveData<>();
    private final MutableLiveData<Boolean>              isLoading      = new MutableLiveData<>(false);

    public ResultsViewModel(android.app.Application app) {
        super(app);
        db = AppDatabase.getInstance(app);
        Executors.newSingleThreadExecutor().execute(this::loadFromCache);
    }

    public LiveData<List<SemesterResult>> getSemesters()      { return semesters; }
    public LiveData<Double>              getCgpa()             { return cgpa; }
    public LiveData<Double>              getPercentage()       { return percentage; }
    public LiveData<String>              getClassification()   { return classification; }
    public LiveData<Boolean>             getIsLoading()        { return isLoading; }

    private void loadFromCache() {
        ResultsCacheEntity cached = db.resultsDao().get();
        if (cached == null || cached.dataJson == null) return;
        try {
            List<SemesterResult> sems = new Gson().fromJson(cached.dataJson,
                    new TypeToken<List<SemesterResult>>() {}.getType());
            semesters.postValue(sems);
            double c = GradeCalculatorService.calculateCgpa(sems);
            cgpa.postValue(c);
            percentage.postValue(GradeCalculatorService.cgpaToPercentage(c));
            classification.postValue(GradeCalculatorService.classify(c));
        } catch (Exception ignored) {}
    }

    public void sync(Context ctx) {
        isLoading.setValue(true);
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                ProfileEntity p = db.profileDao().getProfile();
                if (p == null || p.ktuPasswordEnc == null) return;
                String pass = CryptoUtil.decrypt(p.ktuPasswordEnc, ctx);
                List<SemesterResult> results =
                        new KtuPortalService().fetchResults(p.registerNumber, pass);
                ResultsCacheEntity rc = new ResultsCacheEntity();
                rc.registerNumber = p.registerNumber;
                rc.dataJson = new Gson().toJson(results);
                rc.syncedAt = Instant.now().toString();
                db.resultsDao().upsert(rc);
                loadFromCache();
            } catch (Exception ignored) {}
            isLoading.postValue(false);
        });
    }
}
