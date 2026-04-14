package com.skillgap.model;

import java.util.ArrayList;
import java.util.List;

/**
 * MODEL: User
 * OOP: Encapsulation — private fields, public getters/setters
 * Stores the user's name and their current skill list.
 */
public class User {

    private String name;
    private List<String> currentSkills;

    public User(String name) {
        this.name = name;
        this.currentSkills = new ArrayList<>();
    }

    /**
     * Accepts a comma-separated skills string,
     * normalizes (lowercase + trim), and stores.
     */
    public void setSkillsFromInput(String rawInput) {
        currentSkills.clear();
        for (String skill : rawInput.split(",")) {
            String normalized = skill.trim().toLowerCase();
            if (!normalized.isEmpty()) {
                currentSkills.add(normalized);
            }
        }
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<String> getCurrentSkills() { return currentSkills; }
    public void setCurrentSkills(List<String> skills) { this.currentSkills = skills; }
}