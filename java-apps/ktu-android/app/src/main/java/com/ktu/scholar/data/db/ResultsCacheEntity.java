package com.ktu.scholar.data.db;

import androidx.room.*;

@Entity(tableName = "results_cache")
public class ResultsCacheEntity {
    @PrimaryKey
    @NonNull
    public String id = "singleton";
    public String registerNumber;
    public String dataJson;   // JSON array of SemesterResult
    public String syncedAt;
}
