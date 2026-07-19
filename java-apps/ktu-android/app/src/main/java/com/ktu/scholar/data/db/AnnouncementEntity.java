package com.ktu.scholar.data.db;

import androidx.room.*;

@Entity(tableName = "announcements")
public class AnnouncementEntity {
    @PrimaryKey
    @NonNull
    public String id = "";
    public String title;
    public String content;
    public String category;   // EXAM / RESULT / ACADEMIC / GENERAL
    public String date;
    public String link;
    public boolean isNew;
    public String createdAt;
}
