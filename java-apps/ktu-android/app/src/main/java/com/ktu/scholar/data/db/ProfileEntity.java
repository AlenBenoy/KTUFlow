package com.ktu.scholar.data.db;

import androidx.room.*;

@Entity(tableName = "profiles")
public class ProfileEntity {
    @PrimaryKey
    @NonNull
    public String id = "";
    public String registerNumber;
    public String name;
    public String email;
    public String dateOfBirth;
    public String branch;
    public int    semester;
    public String college;
    public String ktuPasswordEnc;    // AES-256-GCM encrypted
    public String ezygoPasswordEnc;  // AES-256-GCM encrypted
    public String createdAt;
    public String updatedAt;
}
