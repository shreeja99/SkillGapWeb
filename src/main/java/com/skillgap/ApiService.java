package com.skillgap.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * SERVICE: ApiService
 * OOP: Abstraction — hides all HTTP + JSON parsing logic
 * Calls Groq API to extract skills from resume text / custom roles.
 * Calls JSearch API to fetch real job postings and extract required skills.
 * Uses Java's built-in HttpURLConnection — no extra HTTP library needed.
 */
@Service
public class ApiService {

    @Value("${groq.api.key:YOUR_GROQ_API_KEY_HERE}")
    private String apiKey;

    @Value("${groq.model:llama-3.1-8b-instant}")
    private String model;

    @Value("${jsearch.api.key:YOUR_RAPIDAPI_KEY_HERE}")
    private String jsearchApiKey;

    private static final String GROQ_URL     = "https://api.groq.com/openai/v1/chat/completions";
    private static final String JSEARCH_URL  = "https://jsearch.p.rapidapi.com/search";
    private static final int    TIMEOUT      = 15000;

    // ─────────────────────────────────────────────────────────────────────────
    // JSearch: fetch real job postings and extract common required skills
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fetches real job postings for a role from JSearch API,
     * then asks Groq to extract the most commonly required skills.
     *
     * @param roleName  Role to search jobs for
     * @return          List of real required skills from actual job postings
     */
    public List<String> getSkillsFromJSearch(String roleName) {
        if (jsearchApiKey == null || jsearchApiKey.equals("YOUR_RAPIDAPI_KEY_HERE")) {
            System.out.println("[ApiService] No JSearch key — falling back to Groq.");
            return getSkillsForRole(roleName);
        }

        try {
            // Step 1: Fetch job postings from JSearch
            String jobDescriptions = fetchJobDescriptions(roleName);

            if (jobDescriptions == null || jobDescriptions.isBlank()) {
                System.out.println("[ApiService] JSearch returned no jobs — falling back to Groq.");
                return getSkillsForRole(roleName);
            }

            System.out.println("[ApiService] JSearch fetched job data for: " + roleName);

            // Step 2: Send job descriptions to Groq to extract common skills
            String prompt = "Below are real job descriptions for the role of '" + roleName + "'. "
                    + "Extract the TOP 8 most commonly required skills across all these job postings. "
                    + "Return ONLY short resume-style skill keywords. "
                    + "Examples: 'Python', 'SQL', 'Excel', 'Git', 'Spring Boot', 'QuickBooks'. "
                    + "No long phrases, no parentheses, no explanations. "
                    + "Respond ONLY with a numbered list.\n1. Skill\n2. Skill\n\n"
                    + "Job Descriptions:\n" + jobDescriptions;

            String body = buildRequestBody(prompt);
            String raw  = sendGroqPost(body);
            String text = extractContent(raw);

            if (text == null || text.isBlank()) return getSkillsForRole(roleName);

            List<String> skills = parseSkills(text);
            System.out.println("[ApiService] Extracted " + skills.size() + " real skills from JSearch jobs.");
            return skills;

        } catch (Exception e) {
            System.out.println("[ApiService] JSearch ERROR: " + e.getMessage() + " — falling back to Groq.");
            return getSkillsForRole(roleName);
        }
    }

    /**
     * Calls JSearch API and concatenates job descriptions from top 5 results.
     */
    private String fetchJobDescriptions(String roleName) throws IOException {
        String encoded = URLEncoder.encode(roleName, StandardCharsets.UTF_8);
        String urlStr  = JSEARCH_URL + "?query=" + encoded + "&num_pages=1&date_posted=all";

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("x-rapidapi-host", "jsearch.p.rapidapi.com");
        conn.setRequestProperty("x-rapidapi-key", jsearchApiKey);
        conn.setConnectTimeout(TIMEOUT);
        conn.setReadTimeout(TIMEOUT);

        int status = conn.getResponseCode();
        if (status != 200) {
            String err = readStream(conn.getErrorStream());
            throw new IOException("JSearch HTTP " + status + ": " + err);
        }

        String json = readStream(conn.getInputStream());
        return extractJobDescriptions(json);
    }

    /**
     * Parses JSearch JSON response and extracts up to 5 job descriptions.
     * Uses simple string parsing — no external JSON library needed.
     */
    private String extractJobDescriptions(String json) {
        StringBuilder descriptions = new StringBuilder();
        int count = 0;
        int searchFrom = 0;

        while (count < 5) {
            // Find "job_description": in JSON
            int idx = json.indexOf("\"job_description\":", searchFrom);
            if (idx == -1) break;

            int openQ = json.indexOf('"', idx + 18);
            if (openQ == -1) break;

            // Extract the description value
            StringBuilder desc = new StringBuilder();
            int i = openQ + 1;
            while (i < json.length()) {
                char c = json.charAt(i);
                if (c == '\\' && i + 1 < json.length()) {
                    char n = json.charAt(i + 1);
                    if (n == '"')  { desc.append('"');  i += 2; continue; }
                    if (n == 'n')  { desc.append('\n'); i += 2; continue; }
                    if (n == '\\') { desc.append('\\'); i += 2; continue; }
                    i += 2; continue;
                }
                if (c == '"') break;
                desc.append(c);
                i++;
            }

            String d = desc.toString().trim();
            if (!d.isEmpty()) {
                // Keep only first 500 chars per job to stay within token limits
                descriptions.append("--- Job ").append(count + 1).append(" ---\n");
                descriptions.append(d, 0, Math.min(500, d.length())).append("\n\n");
                count++;
            }

            searchFrom = i + 1;
        }

        System.out.println("[ApiService] Parsed " + count + " job descriptions from JSearch.");
        return descriptions.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Groq: fallback skill generation for custom roles
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fallback: asks Groq to generate skills for a role when JSearch fails.
     */
    public List<String> getSkillsForRole(String roleName) {
        if (apiKey == null || apiKey.equals("YOUR_GROQ_API_KEY_HERE")) {
            System.out.println("[ApiService] No API key — returning fallback skills.");
            return getFallbackSkills(roleName);
        }

        try {
            String prompt = "List exactly 8 skills required for the role of '" + roleName + "'. "
                    + "Return ONLY short skill keywords that people actually write in their resumes. "
                    + "Examples of good format: 'QuickBooks', 'Excel', 'Tax Preparation', 'SQL', 'Python', 'Git'. "
                    + "No long phrases, no explanations, no parentheses, no categories. "
                    + "Respond ONLY with a numbered list.\n1. Skill\n2. Skill";

            String body = buildRequestBody(prompt);
            String raw  = sendGroqPost(body);
            String text = extractContent(raw);

            if (text == null || text.isBlank()) return getFallbackSkills(roleName);

            List<String> skills = parseSkills(text);
            System.out.println("[ApiService] Fetched " + skills.size() + " skills for: " + roleName);
            return skills;

        } catch (Exception e) {
            System.out.println("[ApiService] ERROR: " + e.getMessage());
            return getFallbackSkills(roleName);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Groq: extract skills from resume text
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Extracts technical skills from raw resume text using Groq.
     */
    public List<String> extractSkillsFromResumeText(String resumeText) {
        if (apiKey == null || apiKey.equals("YOUR_GROQ_API_KEY_HERE")) {
            System.out.println("[ApiService] No API key — cannot extract skills from resume.");
            return new ArrayList<>();
        }

        try {
            String prompt = "From the following resume text, extract ONLY the technical skills, "
                    + "programming languages, frameworks, tools, and technologies. "
                    + "Return short keyword-style skills exactly as someone would write them in a resume. "
                    + "Examples of good format: 'Python', 'Excel', 'QuickBooks', 'SQL', 'Git', 'Spring Boot'. "
                    + "No long descriptions, no parentheses, no extra text. "
                    + "Respond ONLY with a numbered list.\n1. Skill\n2. Skill\n\nResume Text:\n" + resumeText;

            String body = buildRequestBody(prompt);
            String raw  = sendGroqPost(body);
            String text = extractContent(raw);

            if (text == null || text.isBlank()) return new ArrayList<>();

            List<String> skills = parseSkills(text);
            System.out.println("[ApiService] Extracted " + skills.size() + " skills from resume.");
            return skills;

        } catch (Exception e) {
            System.out.println("[ApiService] Resume extraction ERROR: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Shared HTTP + parsing helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String buildRequestBody(String prompt) {
        String escaped = prompt
                .replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");

        return "{\"model\":\"" + model + "\","
                + "\"messages\":[{\"role\":\"user\",\"content\":\"" + escaped + "\"}],"
                + "\"max_tokens\":300,\"temperature\":0.0}";
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
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
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
                if (n == '"') { sb.append('"'); i += 2; continue; }
                if (n == 'n') { sb.append('\n'); i += 2; continue; }
                if (n == '\\') { sb.append('\\'); i += 2; continue; }
            }
            if (c == '"') break;
            sb.append(c);
            i++;
        }
        return sb.toString().trim();
    }

    private List<String> parseSkills(String text) {
        List<String> skills = new ArrayList<>();
        for (String line : text.split("\n")) {
            String s = line.trim()
                    .replaceAll("^\\d+[.)\\-]\\s*", "")
                    .replaceAll("^[-•*]\\s*", "")
                    .replaceAll("\\s*\\(.*?\\)", "")
                    .trim().toLowerCase();
            if (!s.isEmpty() && s.length() <= 60
                    && !s.startsWith("here") && !s.startsWith("these")) {
                skills.add(s);
            }
        }
        return skills;
    }

    private List<String> getFallbackSkills(String roleName) {
        String r = roleName.toLowerCase();
        if (r.contains("python") || r.contains("django"))
            return Arrays.asList("python", "django/flask", "rest api", "sql", "git", "html/css", "postgresql", "unit testing");
        if (r.contains("react") || r.contains("vue") || r.contains("angular"))
            return Arrays.asList("javascript", "react/vue/angular", "html", "css", "git", "rest api", "typescript", "webpack");
        return Arrays.asList("programming fundamentals", "problem solving", "algorithms",
                "data structures", "git", "communication", "system design", "testing");
    }
}