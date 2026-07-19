package com.ktu.scholar.data.db;

import android.content.Context;
import androidx.room.*;

@Database(
    entities = {
        ProfileEntity.class,
        TodoEntity.class,
        ResultsCacheEntity.class,
        AttendanceCacheEntity.class,
        AnnouncementEntity.class,
        FocusSettingsEntity.class
    },
    version = 1,
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "ktu_scholar.db")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    public abstract ProfileDao    profileDao();
    public abstract TodoDao       todoDao();
    public abstract ResultsDao    resultsDao();
    public abstract AttendanceDao attendanceDao();
    public abstract AnnouncementDao announcementDao();
    public abstract FocusDao      focusDao();
}
