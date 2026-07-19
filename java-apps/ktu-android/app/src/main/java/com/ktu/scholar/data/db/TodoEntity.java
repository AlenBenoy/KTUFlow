package com.ktu.scholar.data.db;

import androidx.room.*;

@Entity(tableName = "todos")
public class TodoEntity {
    @PrimaryKey
    @NonNull
    public String id = "";
    public String title;
    public String description;
    public String dueDate;
    public boolean completed;
    public String priority;   // LOW / MEDIUM / HIGH
    public String category;   // ASSIGNMENT / EXAM / PROJECT / PERSONAL / OTHER
    public String reminderTime;
    public String createdAt;
    public String updatedAt;
}
