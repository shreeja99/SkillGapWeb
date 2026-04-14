package com.skillgap.controller;

import com.skillgap.model.AnalysisResult;
import com.skillgap.model.Role;
import com.skillgap.model.User;
import com.skillgap.service.ApiService;
import com.skillgap.service.DataLoader;
import com.skillgap.service.SkillGapAnalyzerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * CONTROLLER: SkillGapController
 * Exposes REST endpoints consumed by the frontend.
 *
 * Endpoints:
 *   GET  /api/roles         → list of predefined role names
 *   POST /api/analyze       → run skill gap analysis
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*") // Allow frontend requests from any origin
public class SkillGapController {

    @Autowired private DataLoader             dataLoader;
    @Autowired private ApiService             apiService;
    @Autowired private SkillGapAnalyzerService analyzerService;

    /**
     * GET /api/roles
     * Returns sorted list of all predefined role names.
     * Used by frontend to populate the role dropdown.
     */
    @GetMapping("/roles")
    public ResponseEntity<List<String>> getRoles() {
        return ResponseEntity.ok(dataLoader.getRoleNames());
    }

    /**
     * POST /api/analyze
     * Request body (JSON):
     * {
     *   "name":   "Ravi",
     *   "skills": "java, spring boot, sql",
     *   "role":   "java developer"
     * }
     *
     * Returns AnalysisResult as JSON.
     */
    @PostMapping("/analyze")
    public ResponseEntity<?> analyze(@RequestBody Map<String, String> body) {

        // ---- Validate input ----
        String name   = body.getOrDefault("name",   "").trim();
        String skills = body.getOrDefault("skills", "").trim();
        String roleName = body.getOrDefault("role", "").trim().toLowerCase();

        if (name.isEmpty())    return ResponseEntity.badRequest().body("Name is required.");
        if (skills.isEmpty())  return ResponseEntity.badRequest().body("Skills are required.");
        if (roleName.isEmpty()) return ResponseEntity.badRequest().body("Role is required.");

        // ---- Build User object ----
        User user = new User(name);
        user.setSkillsFromInput(skills);

        // ---- Find or fetch Role ----
        Role role = dataLoader.findRole(roleName);

        if (role != null) {
            // Predefined role from JSON
            System.out.println("[Controller] Found predefined role: " + roleName);
        } else {
            // Custom role — call Groq API
            System.out.println("[Controller] Custom role, calling API: " + roleName);
            List<String> apiSkills = apiService.getSkillsForRole(roleName);
            role = new Role(roleName);
            role.setRequiredSkills(apiSkills);
            role.setCustomRole(true);
        }

        // ---- Analyze ----
        AnalysisResult result = analyzerService.analyze(user, role);

        return ResponseEntity.ok(result);
    }
}