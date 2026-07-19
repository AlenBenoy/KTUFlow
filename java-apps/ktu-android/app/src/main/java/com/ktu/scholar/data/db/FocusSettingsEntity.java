package com.ktu.scholar.data.db;

import androidx.room.*;

@Entity(tableName = "focus_settings")
public class FocusSettingsEntity {
    @PrimaryKey
    @NonNull
    public String id = "singleton";
    public boolean enabled;
    public int cooldownMinutes = 30;
    public String blockedAppsJson    = "[]";
    public String blockedWebsitesJson = "[]";
    public int sessionDurationMinutes = 60;
    public String updatedAt;
}
