package com.skillgap.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillgap.model.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.*;

/**
 * SERVICE: DataLoader
 * OOP: Abstraction — hides all file I/O and JSON parsing
 * Loads roles from roles.json into a HashMap at startup.
 * Uses Jackson (included via Spring Boot) for clean JSON parsing.
 */
@Service
public class DataLoader {

    @Value("${roles.json.path:roles.json}")
    private String jsonPath;

    // In-memory store: role name → Role object
    private HashMap<String, Role> rolesMap = new HashMap<>();

    /**
     * Runs automatically after Spring creates this bean.
     * Loads all roles from roles.json into memory.
     */
    @PostConstruct
    public void loadRoles() {
        try {
            InputStream is = new ClassPathResource(jsonPath).getInputStream();
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(is);
            JsonNode rolesArray = root.get("roles");

            if (rolesArray == null || !rolesArray.isArray()) {
                System.out.println("[DataLoader] No 'roles' array found in JSON.");
                return;
            }

            for (JsonNode roleNode : rolesArray) {
                String name = roleNode.get("name").asText().trim().toLowerCase();
                Role role = new Role(name);

                JsonNode skillsArray = roleNode.get("required_skills");
                if (skillsArray != null && skillsArray.isArray()) {
                    List<String> skills = new ArrayList<>();
                    for (JsonNode skill : skillsArray) {
                        skills.add(skill.asText().trim().toLowerCase());
                    }
                    role.setRequiredSkills(skills);
                }

                rolesMap.put(name, role);
            }

            System.out.println("[DataLoader] Loaded " + rolesMap.size() + " roles from " + jsonPath);

        } catch (Exception e) {
            System.out.println("[DataLoader] ERROR loading roles.json: " + e.getMessage());
        }
    }

    /** Returns the full roles map */
    public HashMap<String, Role> getRolesMap() { return rolesMap; }

    /** Returns sorted list of all role names for the frontend dropdown */
    public List<String> getRoleNames() {
        List<String> names = new ArrayList<>(rolesMap.keySet());
        Collections.sort(names);
        return names;
    }

    /** Returns a Role by name, or null if not found */
    public Role findRole(String roleName) {
        return rolesMap.get(roleName.trim().toLowerCase());
    }
}