package com.ktu.scholar.core.model;

/**
 * Represents the logged-in student's identity and portal credentials.
 * Passwords are stored encrypted; this object holds plaintext only in memory.
 */
public class Profile {
    private String id;
    private String registerNumber;
    private String name;
    private String email;
    private String dateOfBirth;   // ISO-8601: yyyy-MM-dd
    private String branch;
    private int semester;
    private String college;
    private String ezygoId;
    // Held in memory as plaintext; persisted encrypted
    private String ktuPassword;
    private String ezygoPassword;

    public Profile() {}

    // ── Getters & Setters ─────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRegisterNumber() { return registerNumber; }
    public void setRegisterNumber(String registerNumber) { this.registerNumber = registerNumber; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(String dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }

    public int getSemester() { return semester; }
    public void setSemester(int semester) { this.semester = semester; }

    public String getCollege() { return college; }
    public void setCollege(String college) { this.college = college; }

    public String getEzygoId() { return ezygoId; }
    public void setEzygoId(String ezygoId) { this.ezygoId = ezygoId; }

    public String getKtuPassword() { return ktuPassword; }
    public void setKtuPassword(String ktuPassword) { this.ktuPassword = ktuPassword; }

    public String getEzygoPassword() { return ezygoPassword; }
    public void setEzygoPassword(String ezygoPassword) { this.ezygoPassword = ezygoPassword; }

    public boolean hasKtuCredentials() { return ktuPassword != null && !ktuPassword.isEmpty(); }
    public boolean hasEzygoCredentials() { return ezygoPassword != null && !ezygoPassword.isEmpty(); }

    @Override
    public String toString() {
        return "Profile{registerNumber='" + registerNumber + "', name='" + name +
               "', semester=" + semester + ", branch='" + branch + "'}";
    }
}
