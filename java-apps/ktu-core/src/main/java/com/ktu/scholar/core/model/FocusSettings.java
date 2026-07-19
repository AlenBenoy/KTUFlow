package com.ktu.scholar.core.model;

import java.util.ArrayList;
import java.util.List;

public class FocusSettings {
    private boolean enabled;
    private int cooldownMinutes = 30;
    private List<String> blockedApps = new ArrayList<>();
    private List<String> blockedWebsites = new ArrayList<>();
    private int sessionDurationMinutes = 60;

    public FocusSettings() {}

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getCooldownMinutes() { return cooldownMinutes; }
    public void setCooldownMinutes(int cooldownMinutes) { this.cooldownMinutes = cooldownMinutes; }

    public List<String> getBlockedApps() { return blockedApps; }
    public void setBlockedApps(List<String> blockedApps) { this.blockedApps = blockedApps; }

    public List<String> getBlockedWebsites() { return blockedWebsites; }
    public void setBlockedWebsites(List<String> blockedWebsites) { this.blockedWebsites = blockedWebsites; }

    public int getSessionDurationMinutes() { return sessionDurationMinutes; }
    public void setSessionDurationMinutes(int sessionDurationMinutes) { this.sessionDurationMinutes = sessionDurationMinutes; }
}
