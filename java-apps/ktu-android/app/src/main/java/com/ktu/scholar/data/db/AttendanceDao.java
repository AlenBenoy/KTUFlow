package com.ktu.scholar.data.db;

import androidx.lifecycle.LiveData;
import androidx.room.*;

@Dao
public interface AttendanceDao {
    @Query("SELECT * FROM attendance_cache LIMIT 1")
    AttendanceCacheEntity get();

    @Query("SELECT * FROM attendance_cache LIMIT 1")
    LiveData<AttendanceCacheEntity> observe();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(AttendanceCacheEntity entity);
}
