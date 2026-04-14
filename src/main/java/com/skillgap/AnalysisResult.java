package com.skillgap.model;

import java.util.List;

/**
 * MODEL: AnalysisResult
 * OOP: Encapsulation — Data Transfer Object (DTO)
 * Holds the complete result of a skill gap analysis.
 * Serialized to JSON by Spring Boot and sent to the frontend.
 */
public class AnalysisResult {

    private String userName;
    private String roleName;
    private boolean isCustomRole;
    private List<String> matchingSkills;
    private List<String> missingSkills;
    private List<String> requiredSkills;
    private int totalRequired;
    private double gapPercentage;
    private double readinessPercentage;
    private String readinessMessage;
    private String readinessEmoji;

    // ---- Getters ----
    public String getUserName() { return userName; }
    public String getRoleName() { return roleName; }
    public boolean isCustomRole() { return isCustomRole; }
    public List<String> getMatchingSkills() { return matchingSkills; }
    public List<String> getMissingSkills() { return missingSkills; }
    public List<String> getRequiredSkills() { return requiredSkills; }
    public int getTotalRequired() { return totalRequired; }
    public double getGapPercentage() { return gapPercentage; }
    public double getReadinessPercentage() { return readinessPercentage; }
    public String getReadinessMessage() { return readinessMessage; }
    public String getReadinessEmoji() { return readinessEmoji; }

    // ---- Setters ----
    public void setUserName(String v) { this.userName = v; }
    public void setRoleName(String v) { this.roleName = v; }
    public void setCustomRole(boolean v) { this.isCustomRole = v; }
    public void setMatchingSkills(List<String> v) { this.matchingSkills = v; }
    public void setMissingSkills(List<String> v) { this.missingSkills = v; }
    public void setRequiredSkills(List<String> v) { this.requiredSkills = v; }
    public void setTotalRequired(int v) { this.totalRequired = v; }
    public void setGapPercentage(double v) { this.gapPercentage = v; }
    public void setReadinessPercentage(double v) { this.readinessPercentage = v; }
    public void setReadinessMessage(String v) { this.readinessMessage = v; }
    public void setReadinessEmoji(String v) { this.readinessEmoji = v; }
}