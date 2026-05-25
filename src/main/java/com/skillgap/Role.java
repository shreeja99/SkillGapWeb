package com.skillgap.model;

import java.util.ArrayList;
import java.util.List;


public class Role {

    private String roleName;
    private List<String> requiredSkills;
    private boolean isCustomRole; 

    public Role(String roleName) {
        this.roleName = roleName.trim().toLowerCase();
        this.requiredSkills = new ArrayList<>();
        this.isCustomRole = false;
    }

    public void addRequiredSkill(String skill) {
        String normalized = skill.trim().toLowerCase();
        if (!normalized.isEmpty()) requiredSkills.add(normalized);
    }

    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName.trim().toLowerCase(); }

    public List<String> getRequiredSkills() { return requiredSkills; }
    public void setRequiredSkills(List<String> skills) { this.requiredSkills = skills; }

    public boolean isCustomRole() { return isCustomRole; }
    public void setCustomRole(boolean customRole) { this.isCustomRole = customRole; }
}