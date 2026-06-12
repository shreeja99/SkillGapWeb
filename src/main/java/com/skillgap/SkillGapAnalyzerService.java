package com.skillgap.service;

import com.skillgap.model.AnalysisResult;
import com.skillgap.model.Role;
import com.skillgap.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SkillGapAnalyzerService {

    @Autowired
    private ApiService apiService;

    @Value("${groq.api.key:YOUR_GROQ_API_KEY_HERE}")
    private String apiKey;

    @Value("${groq.model:llama-3.1-8b-instant}")
    private String model;

    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final int    TIMEOUT  = 15000;

    // ─────────────────────────────────────────────────────────────────────────
    // Main entry point
    // ─────────────────────────────────────────────────────────────────────────

    public AnalysisResult analyze(User user, Role role) {
        // FIX: use getCurrentSkills(), not getSkills()
        List<String> userSkills     = user.getCurrentSkills();
        List<String> requiredSkills = role.getRequiredSkills();

        if (userSkills == null || userSkills.isEmpty()) {
            return buildResult(user, role, new ArrayList<>(), requiredSkills);
        }

        String userSkillsCsv = String.join(", ", userSkills);

        // Step 1: semantically expand the user's skills
        List<String> expandedSkills = apiService.expandSkillsSemanticaly(userSkillsCsv);
        System.out.println("[Analyzer] Expanded user skills: " + expandedSkills);

        // Step 2: semantic gap analysis via Groq
        SemanticGapResult gap = computeSemanticGap(expandedSkills, requiredSkills);

        System.out.println("[Analyzer] Matching: " + gap.matching);
        System.out.println("[Analyzer] Missing:  " + gap.missing);

        return buildResult(user, role, gap.matching, gap.missing);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Semantic gap analysis via Groq
    // ─────────────────────────────────────────────────────────────────────────

    private SemanticGapResult computeSemanticGap(List<String> expandedUserSkills,
                                                  List<String> requiredSkills) {
        if (apiKey == null || apiKey.equals("YOUR_GROQ_API_KEY_HERE")) {
            return naiveMatch(expandedUserSkills, requiredSkills);
        }

        try {
            String userSkillsStr     = String.join(", ", expandedUserSkills);
            String requiredSkillsStr = requiredSkills.stream()
                    .map(s -> "- " + s)
                    .collect(Collectors.joining("\n"));

            String prompt =
                "A candidate has these skills (including inferred/implied ones):\n"
                + userSkillsStr + "\n\n"
                + "The job requires EXACTLY these " + requiredSkills.size() + " skills — you MUST classify every single one:\n"
                + requiredSkillsStr + "\n\n"
                + "CRITICAL: Your response must account for ALL " + requiredSkills.size() + " skills above. "
                + "Every skill must appear in either HAVE or MISSING — do not skip any.\n\n"
                + "Rules for HAVE (be generous — real-world implied knowledge counts):\n"
                + "- Exact or case-insensitive match\n"
                + "- Superset covers subset: Next.js → HAVE React, JavaScript, HTML, CSS\n"
                + "- Framework implies language: PyTorch/TensorFlow → HAVE Python, Machine Learning, Deep Learning, NumPy\n"
                + "- Next.js/React/Vue/Angular/any web framework → HAVE HTML, CSS, JavaScript\n"
                + "- Built any web app or used any backend framework → HAVE HTML, CSS, REST APIs\n"
                + "- MongoDB/Mongoose → HAVE NoSQL, Database Design\n"
                + "- Used any database in projects → HAVE SQL concepts unless only NoSQL shown\n"
                + "- tRPC/GraphQL/REST API experience → HAVE API design\n"
                + "- Docker → HAVE Linux, DevOps basics\n"
                + "- GitHub Actions/CI-CD in resume → HAVE CI/CD\n"
                + "- PostgreSQL/MySQL → HAVE SQL\n"
                + "- Spring Boot → HAVE Java, REST APIs, OOP\n"
                + "- scikit-learn/XGBoost → HAVE Python, Machine Learning, Statistics\n"
                + "- Figma → HAVE Wireframing, Prototyping, UI Design\n"
                + "- SolidWorks/AutoCAD/CATIA/Creo → HAVE CAD, Mechanical Design, Technical Drawing\n"
                + "- MATLAB → HAVE Numerical Analysis, Simulation\n"
                + "- ANSYS → HAVE FEA, Simulation\n"
                + "- Revit → HAVE BIM, CAD\n"
                + "- Excel/Tableau/Power BI → HAVE Data Analysis, Spreadsheets\n"
                + "- Hands-on internship/project experience in a field → HAVE Analytical Skills, Problem Solving "
                + "for that field\n"
                + "Rules for MISSING:\n"
                + "- Only mark MISSING if the skill is genuinely not present and not implied\n"
                + "- A different domain tool does NOT cover: Photoshop ≠ Figma, Excel ≠ SQL\n\n"
                + "Respond ONLY in this exact two-line format:\n"
                + "HAVE: skill1, skill2, skill3\n"
                + "MISSING: skill4, skill5\n"
                + "(If nothing is missing, write: MISSING: none)";

            String body = buildRequestBody(prompt);
            String raw  = sendGroqPost(body);
            String text = extractContent(raw);

            if (text == null || text.isBlank()) {
                return naiveMatch(expandedUserSkills, requiredSkills);
            }

            return parseHaveMissingResponse(text, requiredSkills);

        } catch (Exception e) {
            System.out.println("[Analyzer] Semantic gap error: " + e.getMessage() + " — falling back to naive match");
            return naiveMatch(expandedUserSkills, requiredSkills);
        }
    }

    private SemanticGapResult parseHaveMissingResponse(String text, List<String> requiredSkills) {
        List<String> matching = new ArrayList<>();
        List<String> missing  = new ArrayList<>();

        for (String line : text.split("\n")) {
            line = line.trim();
            if (line.toUpperCase().startsWith("HAVE:")) {
                for (String s : line.substring(5).trim().split(",")) {
                    String skill = s.trim().toLowerCase();
                    if (!skill.isEmpty() && !skill.equals("none")) matching.add(skill);
                }
            } else if (line.toUpperCase().startsWith("MISSING:")) {
                for (String s : line.substring(8).trim().split(",")) {
                    String skill = s.trim().toLowerCase();
                    if (!skill.isEmpty() && !skill.equals("none")) missing.add(skill);
                }
            }
        }

        // Sanity check: for any required skill Groq didn't mention at all,
        // do a generous substring/levenshtein check against BOTH matching and missing
        // before blindly adding to missing.
        Set<String> accountedInHave    = new HashSet<>(matching);
        Set<String> accountedInMissing = new HashSet<>(missing);

        for (String req : requiredSkills) {
            String reqLower = req.toLowerCase();

            // Already covered in HAVE list?
            boolean inHave = accountedInHave.stream().anyMatch(a ->
                a.contains(reqLower) || reqLower.contains(a) || levenshteinSimilar(a, reqLower));
            if (inHave) continue;

            // Already covered in MISSING list?
            boolean inMissing = accountedInMissing.stream().anyMatch(a ->
                a.contains(reqLower) || reqLower.contains(a) || levenshteinSimilar(a, reqLower));
            if (inMissing) continue;

            // Groq didn't mention it at all — use hardcoded semantic superset rules
            // so we don't wrongly penalise things the candidate clearly knows
            if (isImpliedByKnownSkills(reqLower, accountedInHave)) {
                matching.add(reqLower);
            } else {
                missing.add(reqLower);
            }
        }

        // Deduplicate while preserving order
        matching = new ArrayList<>(new LinkedHashSet<>(matching));
        missing  = new ArrayList<>(new LinkedHashSet<>(missing));

        return new SemanticGapResult(matching, missing);
    }

    /**
     * Hardcoded semantic superset rules used as a safety net when Groq's
     * response didn't explicitly classify a required skill.
     * Prevents things like "react" being marked missing when "next.js" is in HAVE.
     */
    private boolean isImpliedByKnownSkills(String requiredSkill, Set<String> haveSkills) {
        // ── Mechanical / Civil / Electrical ──────────────────────────────
        // CAD implied by any specific CAD software
        if (requiredSkill.equals("cad") || requiredSkill.contains("computer-aided design")
                || requiredSkill.contains("computer aided design")) {
            return haveSkills.stream().anyMatch(h ->
                h.contains("solidworks") || h.contains("autocad") || h.contains("catia")
                || h.contains("creo") || h.contains("fusion 360") || h.contains("revit")
                || h.contains("cad") || h.contains("nx") || h.contains("inventor"));
        }
        // Mechanical Design implied by CAD tools or design experience
        if (requiredSkill.contains("mechanical design") || requiredSkill.contains("mechanical system")) {
            return haveSkills.stream().anyMatch(h ->
                h.contains("solidworks") || h.contains("autocad") || h.contains("catia")
                || h.contains("creo") || h.contains("cad") || h.contains("mechanical")
                || h.contains("design"));
        }
        // GD&T implied by CAD/mechanical design experience
        if (requiredSkill.contains("gd&t") || requiredSkill.contains("tolerancing")) {
            return haveSkills.stream().anyMatch(h ->
                h.contains("solidworks") || h.contains("autocad") || h.contains("catia")
                || h.contains("cad") || h.contains("mechanical design"));
        }
        // FEA / Simulation implied by ANSYS or similar
        if (requiredSkill.contains("fea") || requiredSkill.contains("finite element")
                || requiredSkill.contains("simulation")) {
            return haveSkills.stream().anyMatch(h ->
                h.contains("ansys") || h.contains("matlab") || h.contains("simulation")
                || h.contains("fea"));
        }
        // MATLAB implied by simulation/numerical analysis experience
        if (requiredSkill.equals("matlab")) {
            return haveSkills.stream().anyMatch(h ->
                h.contains("matlab") || h.contains("simulink") || h.contains("numerical"));
        }
        // BIM implied by Revit or architectural CAD
        if (requiredSkill.contains("bim")) {
            return haveSkills.stream().anyMatch(h ->
                h.contains("revit") || h.contains("autocad") || h.contains("bim"));
        }
        // Manufacturing/Production knowledge implied by hands-on mechanical experience
        if (requiredSkill.contains("manufacturing") || requiredSkill.contains("production")) {
            return haveSkills.stream().anyMatch(h ->
                h.contains("mechanical") || h.contains("cad") || h.contains("manufacturing")
                || h.contains("hand tools") || h.contains("assembly"));
        }

        // ── Design (UI/UX/Graphic) ────────────────────────────────────────
        // Wireframing/Prototyping implied by Figma/Adobe XD
        if (requiredSkill.contains("wireframe") || requiredSkill.contains("prototyp")) {
            return haveSkills.stream().anyMatch(h ->
                h.contains("figma") || h.contains("adobe xd") || h.contains("sketch")
                || h.contains("wireframe") || h.contains("prototyp"));
        }
        // Visual Design implied by Adobe tools
        if (requiredSkill.contains("visual design") || requiredSkill.contains("graphic design")) {
            return haveSkills.stream().anyMatch(h ->
                h.contains("photoshop") || h.contains("illustrator") || h.contains("indesign")
                || h.contains("figma") || h.contains("canva") || h.contains("design"));
        }

        // ── Business / Finance / Data ─────────────────────────────────────
        // Excel implied by any spreadsheet/data analysis tool
        if (requiredSkill.equals("excel") || requiredSkill.contains("spreadsheet")) {
            return haveSkills.stream().anyMatch(h ->
                h.contains("excel") || h.contains("tableau") || h.contains("power bi")
                || h.contains("data analysis") || h.contains("spreadsheet"));
        }
        // Financial Modeling implied by Excel + finance background
        if (requiredSkill.contains("financial modeling") || requiredSkill.contains("financial analysis")) {
            return haveSkills.stream().anyMatch(h ->
                h.contains("excel") || h.contains("finance") || h.contains("accounting")
                || h.contains("financial"));
        }
        // Communication / Soft skills implied by internships, volunteering, presentations
        if (requiredSkill.contains("communication") || requiredSkill.contains("presentation")) {
            return haveSkills.stream().anyMatch(h ->
                h.contains("communication") || h.contains("presentation")
                || h.contains("stakeholder") || h.contains("teamwork"));
        }
        // Analytical Skills implied by any technical/engineering background
        if (requiredSkill.contains("analytical") || requiredSkill.contains("problem solving")
                || requiredSkill.contains("problem-solving")) {
            return haveSkills.stream().anyMatch(h ->
                h.contains("analy") || h.contains("design") || h.contains("engineering")
                || h.contains("research") || h.contains("data"));
        }

        // ── Web fundamentals — implied by ANY frontend framework ──────────
        if (requiredSkill.equals("html") || requiredSkill.equals("css")
                || requiredSkill.equals("javascript") || requiredSkill.equals("js")) {
            return haveSkills.stream().anyMatch(h ->
                h.contains("react") || h.contains("vue") || h.contains("angular")
                || h.contains("next") || h.contains("svelte") || h.contains("typescript")
                || h.contains("tailwind") || h.contains("bootstrap")
                || h.contains("node") || h.contains("web"));
        }
        // React implied by Next.js or Gatsby
        if (requiredSkill.equals("react") || requiredSkill.equals("react.js")) {
            return haveSkills.stream().anyMatch(h ->
                h.contains("next") || h.contains("gatsby") || h.contains("remix"));
        }
        // Python implied by any ML/data library
        if (requiredSkill.equals("python")) {
            return haveSkills.stream().anyMatch(h ->
                h.contains("pytorch") || h.contains("tensorflow") || h.contains("keras")
                || h.contains("scikit") || h.contains("pandas") || h.contains("numpy")
                || h.contains("django") || h.contains("flask") || h.contains("fastapi")
                || h.contains("xgboost") || h.contains("lightgbm"));
        }
        // Machine Learning implied by specific ML frameworks
        if (requiredSkill.contains("machine learning") || requiredSkill.contains("ml")) {
            return haveSkills.stream().anyMatch(h ->
                h.contains("pytorch") || h.contains("tensorflow") || h.contains("keras")
                || h.contains("scikit") || h.contains("xgboost") || h.contains("lightgbm"));
        }
        // SQL implied by any relational DB or ORM
        if (requiredSkill.equals("sql") || requiredSkill.contains("database")) {
            return haveSkills.stream().anyMatch(h ->
                h.contains("postgresql") || h.contains("mysql") || h.contains("sqlite")
                || h.contains("sql") || h.contains("hibernate") || h.contains("prisma")
                || h.contains("sequelize") || h.contains("mongodb") || h.contains("database"));
        }
        // Git implied by GitHub, GitLab, CI/CD
        if (requiredSkill.equals("git") || requiredSkill.equals("version control")) {
            return haveSkills.stream().anyMatch(h ->
                h.contains("github") || h.contains("gitlab") || h.contains("bitbucket")
                || h.contains("ci/cd") || h.contains("git"));
        }
        // REST APIs implied by any backend framework or GraphQL
        if (requiredSkill.contains("rest") || requiredSkill.contains("api")) {
            return haveSkills.stream().anyMatch(h ->
                h.contains("express") || h.contains("django") || h.contains("flask")
                || h.contains("spring") || h.contains("fastapi") || h.contains("graphql")
                || h.contains("trpc") || h.contains("node") || h.contains("api"));
        }
        // Linux implied by Docker, AWS, DevOps
        if (requiredSkill.equals("linux") || requiredSkill.equals("bash")) {
            return haveSkills.stream().anyMatch(h ->
                h.contains("docker") || h.contains("kubernetes") || h.contains("aws")
                || h.contains("linux") || h.contains("bash") || h.contains("devops")
                || h.contains("cli"));
        }
        // TypeScript implied by having JavaScript + modern frameworks
        if (requiredSkill.equals("typescript")) {
            return haveSkills.stream().anyMatch(h ->
                h.contains("typescript") || h.contains("trpc") || h.contains("next"));
        }
        // Node.js implied by Express, tRPC, Next.js
        if (requiredSkill.contains("node")) {
            return haveSkills.stream().anyMatch(h ->
                h.contains("express") || h.contains("trpc") || h.contains("next")
                || h.contains("node"));
        }
        // Deep Learning implied by PyTorch/TensorFlow/Keras
        if (requiredSkill.contains("deep learning") || requiredSkill.contains("neural")) {
            return haveSkills.stream().anyMatch(h ->
                h.contains("pytorch") || h.contains("tensorflow") || h.contains("keras"));
        }
        // NumPy implied by pandas, scikit-learn, PyTorch
        if (requiredSkill.contains("numpy") || requiredSkill.contains("pandas")) {
            return haveSkills.stream().anyMatch(h ->
                h.contains("pytorch") || h.contains("tensorflow") || h.contains("scikit")
                || h.contains("numpy") || h.contains("pandas") || h.contains("data"));
        }
        // Java implied by Spring Boot
        if (requiredSkill.equals("java")) {
            return haveSkills.stream().anyMatch(h ->
                h.contains("spring") || h.contains("java") || h.contains("maven"));
        }
        // Docker implied by Kubernetes
        if (requiredSkill.equals("docker")) {
            return haveSkills.stream().anyMatch(h ->
                h.contains("kubernetes") || h.contains("docker") || h.contains("k8s"));
        }
        return false;
    }

    private SemanticGapResult naiveMatch(List<String> expandedSkills, List<String> requiredSkills) {
        List<String> matching = new ArrayList<>();
        List<String> missing  = new ArrayList<>();

        Set<String> userSet = expandedSkills.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        for (String req : requiredSkills) {
            String reqLower = req.toLowerCase();
            boolean found = userSet.stream().anyMatch(u ->
                u.contains(reqLower) || reqLower.contains(u) ||
                levenshteinSimilar(u, reqLower));
            if (found) matching.add(reqLower);
            else        missing.add(reqLower);
        }

        return new SemanticGapResult(matching, missing);
    }

    private boolean levenshteinSimilar(String a, String b) {
        if (Math.abs(a.length() - b.length()) > 4) return false;
        int len1 = a.length(), len2 = b.length();
        if (len1 == 0 || len2 == 0) return false;

        int[][] dp = new int[len1 + 1][len2 + 1];
        for (int i = 0; i <= len1; i++) dp[i][0] = i;
        for (int j = 0; j <= len2; j++) dp[0][j] = j;

        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(dp[i-1][j] + 1, dp[i][j-1] + 1),
                    dp[i-1][j-1] + cost
                );
            }
        }
        return dp[len1][len2] <= 2;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Build AnalysisResult — using correct setter names from your model
    // ─────────────────────────────────────────────────────────────────────────

    private AnalysisResult buildResult(User user, Role role,
                                        List<String> matching, List<String> missing) {
        AnalysisResult result = new AnalysisResult();

        result.setUserName(user.getName());
        result.setRoleName(role.getRoleName());           // FIX: getRoleName() not getName()
        result.setCustomRole(role.isCustomRole());
        result.setMatchingSkills(matching);
        result.setMissingSkills(missing);
        result.setRequiredSkills(role.getRequiredSkills());
        result.setTotalRequired(matching.size() + missing.size());

        int total      = matching.size() + missing.size();
        double readiness = total == 0 ? 0.0 : (double) matching.size() / total * 100.0;
        double gap       = 100.0 - readiness;

        // FIX: setReadinessPercentage() not setReadinessScore()
        result.setReadinessPercentage(Math.round(readiness * 10.0) / 10.0);
        result.setGapPercentage(Math.round(gap * 10.0) / 10.0);

        // Human-readable readiness message
        if (readiness >= 80) {
            result.setReadinessMessage("You're well prepared for this role!");
            result.setReadinessEmoji("🟢");
        } else if (readiness >= 50) {
            result.setReadinessMessage("You're on the right track — a few skills to bridge.");
            result.setReadinessEmoji("🟡");
        } else {
            result.setReadinessMessage("Significant skill gaps — but totally learnable!");
            result.setReadinessEmoji("🔴");
        }

        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HTTP helpers (self-contained — no dependency on ApiService HTTP methods)
    // ─────────────────────────────────────────────────────────────────────────

    private String buildRequestBody(String prompt) {
        String escaped = prompt
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
        return "{\"model\":\"" + model + "\","
                + "\"messages\":[{\"role\":\"user\",\"content\":\"" + escaped + "\"}],"
                + "\"max_tokens\":600,\"temperature\":0.0}";
    }

    private String sendGroqPost(String body) throws IOException {
        URL url = new URL(GROQ_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setConnectTimeout(TIMEOUT);
        conn.setReadTimeout(TIMEOUT);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        if (status != 200) {
            String err = readStream(conn.getErrorStream());
            throw new IOException("HTTP " + status + ": " + err);
        }
        return readStream(conn.getInputStream());
    }

    private String readStream(InputStream is) throws IOException {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private String extractContent(String json) {
        String key = "\"content\":";
        int idx = json.indexOf(key);
        if (idx == -1) return null;
        int openQ = json.indexOf('"', idx + key.length());
        if (openQ == -1) return null;

        StringBuilder sb = new StringBuilder();
        int i = openQ + 1;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char n = json.charAt(i + 1);
                if (n == '"')  { sb.append('"');  i += 2; continue; }
                if (n == 'n')  { sb.append('\n'); i += 2; continue; }
                if (n == '\\') { sb.append('\\'); i += 2; continue; }
            }
            if (c == '"') break;
            sb.append(c);
            i++;
        }
        return sb.toString().trim();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner helper
    // ─────────────────────────────────────────────────────────────────────────

    private static class SemanticGapResult {
        final List<String> matching;
        final List<String> missing;

        SemanticGapResult(List<String> matching, List<String> missing) {
            this.matching = matching;
            this.missing  = missing;
        }
    }
}