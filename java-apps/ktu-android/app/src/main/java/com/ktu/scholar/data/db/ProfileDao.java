package com.ktu.scholar.data.db;

import androidx.lifecycle.LiveData;
import androidx.room.*;

@Dao
public interface ProfileDao {
    @Query("SELECT * FROM profiles LIMIT 1")
    ProfileEntity getProfile();

    @Query("SELECT * FROM profiles LIMIT 1")
    LiveData<ProfileEntity> observeProfile();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ProfileEntity profile);

    @Update
    void update(ProfileEntity profile);

    @Query("DELETE FROM profiles")
    void deleteAll();
}
