package com.skillgap.controller;

import com.skillgap.model.AnalysisResult;
import com.skillgap.model.Role;
import com.skillgap.model.User;
import com.skillgap.service.ApiService;
import com.skillgap.service.DataLoader;
import com.skillgap.service.ResumeParserService;
import com.skillgap.service.SkillGapAnalyzerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * CONTROLLER: SkillGapController
 * Exposes REST endpoints consumed by the frontend.
 *
 * Endpoints:
 *   GET  /api/roles         → list of predefined role names
 *   POST /api/parse-resume  → extract skills from uploaded PDF
 *   POST /api/analyze       → run skill gap analysis
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class SkillGapController {

    @Autowired private DataLoader              dataLoader;
    @Autowired private ApiService              apiService;
    @Autowired private SkillGapAnalyzerService analyzerService;
    @Autowired private ResumeParserService     resumeParserService;

    /**
     * GET /api/roles
     * Returns sorted list of all predefined role names.
     */
    @GetMapping("/roles")
    public ResponseEntity<List<String>> getRoles() {
        return ResponseEntity.ok(dataLoader.getRoleNames());
    }

    /**
     * POST /api/parse-resume
     * Accepts a PDF file upload, extracts skills using PDFBox + Groq.
     */
    @PostMapping("/parse-resume")
    public ResponseEntity<?> parseResume(@RequestParam("file") MultipartFile file) {
        try {
            String skills = resumeParserService.extractSkillsFromPdf(file);
            return ResponseEntity.ok(Map.of("skills", skills));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            System.out.println("[Controller] Resume parse error: " + e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("Could not extract skills from PDF: " + e.getMessage());
        }
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
     * Flow:
     *  1. Check if role exists in roles.json (predefined)
     *  2. If not, fetch real job postings from JSearch API
     *  3. Extract commonly required skills from those job postings via Groq
     *  4. Run gap analysis and return result
     */
    @PostMapping("/analyze")
    public ResponseEntity<?> analyze(@RequestBody Map<String, String> body) {

        // ---- Validate input ----
        String name     = body.getOrDefault("name",   "").trim();
        String skills   = body.getOrDefault("skills", "").trim();
        String roleName = body.getOrDefault("role",   "").trim().toLowerCase();

        if (name.isEmpty())     return ResponseEntity.badRequest().body("Name is required.");
        if (skills.isEmpty())   return ResponseEntity.badRequest().body("Skills are required.");
        if (roleName.isEmpty()) return ResponseEntity.badRequest().body("Role is required.");

        // ---- Build User object ----
        User user = new User(name);
        user.setSkillsFromInput(skills);

        // ---- Find or fetch Role ----
        Role role = dataLoader.findRole(roleName);

        if (role != null) {
            // Predefined role from roles.json — still enrich with JSearch real data
            System.out.println("[Controller] Found predefined role: " + roleName + " — enriching with JSearch.");
            List<String> realSkills = apiService.getSkillsFromJSearch(roleName);
            if (!realSkills.isEmpty()) {
                role.setRequiredSkills(realSkills);
            }
        } else {
            // Custom role — fetch real skills from JSearch
            System.out.println("[Controller] Custom role — fetching real skills from JSearch: " + roleName);
            List<String> realSkills = apiService.getSkillsFromJSearch(roleName);
            role = new Role(roleName);
            role.setRequiredSkills(realSkills);
            role.setCustomRole(true);
        }

        // ---- Analyze ----
        AnalysisResult result = analyzerService.analyze(user, role);

        return ResponseEntity.ok(result);
    }
/**
 * POST /api/suggest-roles
 * Body: { "skills": "java, spring boot, sql" }
 * Returns: { "roles": ["Java Developer", "Backend Developer", "Software Engineer"] }
 */
@PostMapping("/suggest-roles")
public ResponseEntity<?> suggestRoles(@RequestBody Map<String, String> body) {
    String skills = body.getOrDefault("skills", "").trim();
    if (skills.isEmpty()) return ResponseEntity.badRequest().body("Skills are required.");

    List<String> roles = apiService.suggestRolesForSkills(skills);
    return ResponseEntity.ok(Map.of("roles", roles));
}
}
