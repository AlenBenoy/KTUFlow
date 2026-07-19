package com.ktu.scholar.core.model;

public class Announcement {

    public enum Category { EXAM, RESULT, ACADEMIC, GENERAL }

    private String id;
    private String title;
    private String content;
    private Category category;
    private String date;   // yyyy-MM-dd
    private String link;
    private boolean isNew;

    public Announcement() { this.category = Category.GENERAL; this.isNew = true; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }

    public boolean isNew() { return isNew; }
    public void setNew(boolean isNew) { this.isNew = isNew; }
}
