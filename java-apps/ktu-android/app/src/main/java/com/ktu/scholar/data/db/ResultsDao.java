package com.ktu.scholar.data.db;

import androidx.lifecycle.LiveData;
import androidx.room.*;

@Dao
public interface ResultsDao {
    @Query("SELECT * FROM results_cache LIMIT 1")
    ResultsCacheEntity get();

    @Query("SELECT * FROM results_cache LIMIT 1")
    LiveData<ResultsCacheEntity> observe();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(ResultsCacheEntity entity);
}
