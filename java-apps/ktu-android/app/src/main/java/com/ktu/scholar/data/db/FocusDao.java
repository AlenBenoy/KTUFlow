package com.ktu.scholar.data.db;

import androidx.lifecycle.LiveData;
import androidx.room.*;

@Dao
public interface FocusDao {
    @Query("SELECT * FROM focus_settings LIMIT 1")
    FocusSettingsEntity get();

    @Query("SELECT * FROM focus_settings LIMIT 1")
    LiveData<FocusSettingsEntity> observe();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(FocusSettingsEntity settings);
}
