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
 * Calls Groq API to dynamically fetch skills for custom roles.
 * Uses Java's built-in HttpURLConnection — no extra HTTP library needed.
 */
@Service
public class ApiService {

    @Value("${groq.api.key:YOUR_GROQ_API_KEY_HERE}")
    private String apiKey;

    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL = "llama-3.3-70b-versatile";
    private static final int    TIMEOUT  = 15000;

    /**
     * Public method: returns required skills for a given role name.
     * Calls Groq API, parses numbered list response.
     *
     * @param roleName  Custom role (not found in JSON)
     * @return          Normalized skill list, or fallback on failure
     */
    public List<String> getSkillsForRole(String roleName) {
        if (apiKey == null || apiKey.equals("YOUR_GROQ_API_KEY_HERE")) {
            System.out.println("[ApiService] No API key — returning fallback skills.");
            return getFallbackSkills(roleName);
        }

        try {
            String prompt = "List exactly 8 important technical skills required for the role of '"
                    + roleName + "'. Respond ONLY with a numbered list. No explanations. "
                    + "Format:\n1. Skill\n2. Skill";

            String body = buildRequestBody(prompt);
            String raw  = sendPost(body);
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

    // ---- Private helpers ----

    private String buildRequestBody(String prompt) {
        String escaped = prompt
                .replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");

        return "{\"model\":\"" + MODEL + "\","
                + "\"messages\":[{\"role\":\"user\",\"content\":\"" + escaped + "\"}],"
                + "\"max_tokens\":300,\"temperature\":0.3}";
    }

    private String sendPost(String body) throws IOException {
        URL url = new URL(API_URL);
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

    /** Extracts the "content" value from Groq's JSON response */
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

    /** Parses numbered/bulleted list into a clean skill list */
    private List<String> parseSkills(String text) {
        List<String> skills = new ArrayList<>();
        for (String line : text.split("\n")) {
            String s = line.trim()
                    .replaceAll("^\\d+[.)\\-]\\s*", "")
                    .replaceAll("^[-•*]\\s*", "")
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