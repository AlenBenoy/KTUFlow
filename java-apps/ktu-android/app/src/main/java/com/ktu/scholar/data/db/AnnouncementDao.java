package com.ktu.scholar.data.db;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import java.util.List;

@Dao
public interface AnnouncementDao {
    @Query("SELECT * FROM announcements ORDER BY date DESC LIMIT 30")
    LiveData<List<AnnouncementEntity>> observeAll();

    @Query("SELECT * FROM announcements ORDER BY date DESC LIMIT 30")
    List<AnnouncementEntity> getAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<AnnouncementEntity> announcements);

    @Query("DELETE FROM announcements")
    void deleteAll();
}
