package com.skillgap.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * SERVICE: ApifyService
 * OOP: Abstraction — hides Apify REST API + JSON parsing.
 *
 * Replaces JSearch as the source of REAL job postings (LinkedIn).
 *
 * Uses Apify's "run-sync-get-dataset-items" endpoint, which runs the actor
 * and returns the dataset items directly in one HTTP call (synchronous —
 * may take 20-60s for LinkedIn scrapers due to proxies/rate limits).
 *
 * Actor used by default: scraperx/linkedin-search-jobs-scraper
 * Input schema for this actor:
 *   {
 *     "proxyConfiguration": { "useApifyProxy": true },
 *     "startUrls": ["<job title>, <location>"]
 *   }
 * (You can swap actorId via application.properties if needed — note that
 * different LinkedIn scraper actors use different input schemas, so if you
 * switch actors you may need to adjust fetchJobs() accordingly.)
 */
@Service
public class ApifyService {

    @Value("${apify.api.token:YOUR_APIFY_TOKEN_HERE}")
    private String apifyToken;

    @Value("${apify.actor.id:harvestapi~linkedin-job-search}")
    private String actorId;

    private static final int TIMEOUT = 90000; // LinkedIn scrapers are slow — generous timeout

    /**
     * Fetches real LinkedIn job postings for a given job title / search query.
     * Returns a list of JobPosting objects (title, company, description, link).
     *
     * @param jobTitle search query, e.g. "Machine Learning Engineer"
     * @param location e.g. "India"
     * @param rows     how many job postings to fetch (recommend 10-15)
     */
    public List<JobPosting> fetchJobs(String jobTitle, String location, int rows) {
        if (apifyToken == null || apifyToken.equals("YOUR_APIFY_TOKEN_HERE")) {
            System.out.println("[ApifyService] No Apify token configured — returning empty list.");
            return new ArrayList<>();
        }

        try {
            String encodedActorId = actorId.replace("/", "~");
            String url = "https://api.apify.com/v2/acts/" + encodedActorId
                    + "/run-sync-get-dataset-items?token=" + apifyToken;

            // harvestapi/linkedin-job-search schema:
            // { "jobTitles": ["..."], "locations": ["..."], "maxItems": N }
            String body = "{"
                    + "\"jobTitles\":[\"" + escapeJson(jobTitle) + "\"],"
                    + "\"locations\":[\"" + escapeJson(location) + "\"],"
                    + "\"maxItems\":" + rows
                    + "}";

            System.out.println("[ApifyService] Calling Apify actor '" + actorId
                    + "' jobTitles=[" + jobTitle + "] locations=[" + location + "] maxItems=" + rows);

            String response = sendPost(url, body);
            List<JobPosting> jobs = parseJobPostings(response);

            System.out.println("[ApifyService] Apify returned " + jobs.size() + " job postings.");
            return jobs;

        } catch (Exception e) {
            System.out.println("[ApifyService] ERROR: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Convenience overload — defaults to India, 10 results.
     */
    public List<JobPosting> fetchJobs(String jobTitle) {
        return fetchJobs(jobTitle, "India", 10);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Concatenate descriptions from fetched jobs (for skill extraction prompts)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Given a list of job postings, builds a single text blob of descriptions
     * (capped per job) suitable for feeding into a Groq prompt — mirrors the
     * old ApiService.extractJobDescriptions() output format.
     */
    public String buildDescriptionsBlob(List<JobPosting> jobs, int maxJobs, int maxCharsPerJob) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (JobPosting job : jobs) {
            if (count >= maxJobs) break;
            if (job.description == null || job.description.isBlank()) continue;

            sb.append("--- Job ").append(count + 1)
              .append(" (").append(job.title).append(" @ ").append(job.company).append(") ---\n");
            String d = job.description.trim();
            sb.append(d, 0, Math.min(maxCharsPerJob, d.length())).append("\n\n");
            count++;
        }
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HTTP + JSON helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String sendPost(String urlStr, String body) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(TIMEOUT);
        conn.setReadTimeout(TIMEOUT);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        if (status != 200 && status != 201) {
            String err = readStream(conn.getErrorStream());
            throw new IOException("Apify HTTP " + status + ": " + err);
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

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", "");
    }

    /**
     * Parses Apify dataset JSON array into JobPosting objects.
     * Apify LinkedIn scraper actors typically return fields like:
     *   "title", "companyName", "description", "link" / "jobUrl", "location"
     *
     * This parser is field-name-tolerant: it checks several common
     * key names per field, since different actors name fields slightly
     * differently.
     */
    private List<JobPosting> parseJobPostings(String json) {
        List<JobPosting> jobs = new ArrayList<>();
        if (json == null || json.isBlank()) return jobs;

        // The response is a JSON array of objects: [ {...}, {...}, ... ]
        // We split on top-level object boundaries using a simple brace-depth scanner,
        // since we avoid external JSON library dependencies (matches existing code style).
        List<String> objects = splitTopLevelObjects(json);

        for (String obj : objects) {
            JobPosting job = new JobPosting();
            // HarvestAPI (harvestapi/linkedin-job-search) field names:
            //   title, linkedinUrl, descriptionText, companyName,
            //   location: { linkedinText, postalAddress, ... }
            job.title       = extractField(obj, "title", "jobTitle", "position");
            job.company      = extractField(obj, "companyName", "company", "companyTitle");
            job.location     = extractField(obj, "location", "jobLocation", "place");
            job.description  = extractField(obj, "descriptionText", "description", "jobDescription", "descriptionHtml");
            job.link         = extractField(obj, "linkedinUrl", "link", "jobUrl", "url", "applyUrl");

            if (job.title != null && !job.title.isBlank()) {
                jobs.add(job);
            }
        }

        return jobs;
    }

    /**
     * Splits a JSON array string "[ {...}, {...} ]" into individual
     * top-level object strings "{...}" using brace-depth tracking
     * (handles nested objects/arrays and escaped quotes).
     */
    private List<String> splitTopLevelObjects(String json) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        int start = -1;
        boolean inString = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (inString) {
                if (c == '\\') { i++; continue; } // skip escaped char
                if (c == '"') inString = false;
                continue;
            }

            if (c == '"') { inString = true; continue; }

            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start != -1) {
                    result.add(json.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return result;
    }

    /**
     * Extracts a string value for the first matching key found among
     * the given candidate field names. Handles escaped characters.
     */
    private String extractField(String obj, String... candidateKeys) {
        for (String key : candidateKeys) {
            String searchKey = "\"" + key + "\"";
            int idx = obj.indexOf(searchKey);
            if (idx == -1) continue;

            int colon = obj.indexOf(':', idx + searchKey.length());
            if (colon == -1) continue;

            // Skip whitespace after colon
            int i = colon + 1;
            while (i < obj.length() && Character.isWhitespace(obj.charAt(i))) i++;

            if (i >= obj.length()) continue;

            // Value is null
            if (obj.startsWith("null", i)) continue;

            // Value must be a string for our purposes
            if (obj.charAt(i) != '"') continue;

            StringBuilder sb = new StringBuilder();
            int j = i + 1;
            while (j < obj.length()) {
                char c = obj.charAt(j);
                if (c == '\\' && j + 1 < obj.length()) {
                    char n = obj.charAt(j + 1);
                    if (n == '"')  { sb.append('"');  j += 2; continue; }
                    if (n == 'n')  { sb.append('\n'); j += 2; continue; }
                    if (n == '\\') { sb.append('\\'); j += 2; continue; }
                    if (n == 't')  { sb.append('\t'); j += 2; continue; }
                    j += 2; continue;
                }
                if (c == '"') break;
                sb.append(c);
                j++;
            }
            String value = sb.toString().trim();
            if (!value.isEmpty()) return value;
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data holder
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Simple POJO representing a real job posting fetched from Apify/LinkedIn.
     */
    public static class JobPosting {
        public String title;
        public String company;
        public String location;
        public String description;
        public String link;

        @Override
        public String toString() {
            return title + " @ " + company + " (" + location + ")";
        }
    }
}