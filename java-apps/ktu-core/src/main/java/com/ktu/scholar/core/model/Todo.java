package com.ktu.scholar.core.model;

/**
 * A student to-do item with optional due date and reminder.
 */
public class Todo {

    public enum Priority { LOW, MEDIUM, HIGH }
    public enum Category { ASSIGNMENT, EXAM, PROJECT, PERSONAL, OTHER }

    private String id;
    private String title;
    private String description;
    private String dueDate;       // ISO-8601 date string
    private boolean completed;
    private Priority priority;
    private Category category;
    private String reminderTime;  // ISO-8601 date-time
    private String createdAt;
    private String updatedAt;

    public Todo() {
        this.priority = Priority.MEDIUM;
        this.category = Category.OTHER;
        this.completed = false;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDueDate() { return dueDate; }
    public void setDueDate(String dueDate) { this.dueDate = dueDate; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    public String getReminderTime() { return reminderTime; }
    public void setReminderTime(String reminderTime) { this.reminderTime = reminderTime; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
