package com.ktu.scholar.data.db;

import androidx.room.*;

@Entity(tableName = "attendance_cache")
public class AttendanceCacheEntity {
    @PrimaryKey
    @NonNull
    public String id = "singleton";
    public String registerNumber;
    public String dataJson;   // JSON array of SubjectAttendance
    public String syncedAt;
}
