package com.skillgap.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * SERVICE: ApiService
 * OOP: Abstraction — hides all HTTP + JSON parsing logic
 *
 * KEY FIXES in this version:
 * 1. Semantic skill expansion — if someone lists "PyTorch", we infer they know
 *    "Python", "Machine Learning", "NumPy", etc. before running gap analysis.
 * 2. Role suggestions use the FULL resume context (skills + experience + projects)
 *    not just the first 3 skill tokens.
 * 3. Domain-aware analysis — handles ML/AI, design (Figma/Adobe), business/finance,
 *    marketing, product, and engineering profiles correctly.
 * 4. Real job titles + descriptions now come from APIFY (LinkedIn scraper)
 *    instead of JSearch, which was unreliable (often 0 results).
 * 5. Readiness scoring asks Groq to do semantic matching, so "pytorch" is not
 *    flagged as missing when the role asks for "machine learning".
 */
@Service
public class ApiService {

    @Autowired
    private ApifyService apifyService;

    @Value("${groq.api.key:YOUR_GROQ_API_KEY_HERE}")
    private String apiKey;

    @Value("${groq.model:llama-3.1-8b-instant}")
    private String model;

    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final int    TIMEOUT  = 15000;

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC: Semantic skill expansion
    // Called before gap analysis to infer implied skills from what the user has.
    // e.g. "pytorch" → implies python, numpy, machine learning, deep learning
    // e.g. "react"   → implies javascript, html, css, npm
    // e.g. "figma"   → implies ui design, wireframing, prototyping
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Given a comma-separated list of skills the user has, returns an EXPANDED
     * list that includes both the originals AND all skills that are obviously
     * implied / prerequisite (anyone using PyTorch knows Python; anyone using
     * React knows JS/HTML/CSS; etc.).
     *
     * This prevents absurd results like "you need to learn Python" for someone
     * who listed PyTorch, TensorFlow, and scikit-learn on their resume.
     */
    public List<String> expandSkillsSemanticaly(String userSkillsCsv) {
        if (apiKey == null || apiKey.equals("YOUR_GROQ_API_KEY_HERE")) {
            return parseSkills(userSkillsCsv.replace(",", "\n"));
        }

        try {
            String prompt =
                "A candidate lists these skills on their resume: " + userSkillsCsv + "\n\n"
                + "Your job: infer ALL skills this person OBVIOUSLY already knows, even if not explicitly listed.\n"
                + "Rules:\n"
                + "- If they list PyTorch or TensorFlow → they know: Python, Machine Learning, Deep Learning, NumPy, Data Analysis\n"
                + "- If they list scikit-learn → they know: Python, Machine Learning, Statistics, Data Analysis\n"
                + "- If they list React/Vue/Angular → they know: JavaScript, HTML, CSS, npm, REST APIs\n"
                + "- If they list Next.js → they know: React, JavaScript, HTML, CSS, Node.js\n"
                + "- If they list Spring Boot → they know: Java, REST APIs, Maven/Gradle, OOP\n"
                + "- If they list Django/Flask → they know: Python, REST APIs, SQL\n"
                + "- If they list Docker → they know: Linux, CLI/Bash, DevOps basics\n"
                + "- If they list Kubernetes → they know: Docker, Linux, DevOps, YAML\n"
                + "- If they list Figma → they know: UI Design, Wireframing, Prototyping, UX basics\n"
                + "- If they list Adobe XD/Illustrator/Photoshop → they know: Design, Visual Design\n"
                + "- If they list AWS/GCP/Azure → they know: Cloud Computing, Linux, DevOps basics\n"
                + "- If they list MongoDB → they know: NoSQL, JSON/BSON, Database Design\n"
                + "- If they list PostgreSQL/MySQL → they know: SQL, Database Design, Relational DBs\n"
                + "- If they list GraphQL → they know: APIs, JSON, REST concepts\n"
                + "- If they list CI/CD/GitHub Actions → they know: Git, DevOps, Bash/Shell\n"
                + "- If they list tRPC → they know: TypeScript, Node.js, APIs\n"
                + "- If they list XGBoost/LightGBM → they know: Python, Machine Learning, Data Analysis, Statistics\n"
                + "- If they list Excel (advanced) → they know: Data Analysis, Spreadsheets, basic Statistics\n"
                + "- If they list Tableau/Power BI → they know: Data Visualization, Business Intelligence, Excel\n"
                + "- If they list SolidWorks/AutoCAD/CATIA → they know: CAD, Mechanical Design, Technical Drawing\n"
                + "- If they list MATLAB → they know: Numerical Analysis, Simulation, Mathematics\n"
                + "- If they list ANSYS → they know: FEA, Simulation, Mechanical Design\n"
                + "- If they list Revit → they know: BIM, CAD, Civil/Architectural Design\n"
                + "- If they list Financial Modeling → they know: Excel, Accounting basics\n"
                + "- Apply similar inference for any other technologies, tools, or methods — across "
                + "ANY field including mechanical, civil, electrical, design, business, marketing, healthcare.\n\n"
                + "Return a COMBINED numbered list: original skills + all inferred skills.\n"
                + "Keep each item SHORT (1-3 words). No duplicates. No explanations.\n"
                + "1. Skill\n2. Skill\n...";

            String body = buildRequestBody(prompt);
            String raw  = sendGroqPost(body);
            String text = extractContent(raw);

            if (text == null || text.isBlank()) {
                return parseSkills(userSkillsCsv.replace(",", "\n"));
            }

            List<String> expanded = parseSkills(text);
            System.out.println("[ApiService] Expanded skills from "
                + userSkillsCsv.split(",").length + " → " + expanded.size());
            return expanded;

        } catch (Exception e) {
            System.out.println("[ApiService] Skill expansion ERROR: " + e.getMessage());
            return parseSkills(userSkillsCsv.replace(",", "\n"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Apify: fetch real job postings (LinkedIn) and extract common required skills
    // Replaces the old JSearch-based getSkillsFromJSearch().
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fetches REAL job postings for a role from Apify's LinkedIn jobs scraper,
     * then asks Groq to extract the most commonly required skills.
     * Falls back to getSkillsForRole() (pure Groq) if Apify returns nothing.
     */
    public List<String> getSkillsFromRealJobs(String roleName) {
        try {
            List<ApifyService.JobPosting> jobs = apifyService.fetchJobs(roleName, "India", 10);

            if (jobs.isEmpty()) {
                System.out.println("[ApiService] Apify returned no jobs for '" + roleName + "' — falling back to Groq.");
                return getSkillsForRole(roleName);
            }

            String jobDescriptions = apifyService.buildDescriptionsBlob(jobs, 5, 400);

            if (jobDescriptions == null || jobDescriptions.isBlank()) {
                System.out.println("[ApiService] Apify jobs had no descriptions — falling back to Groq.");
                return getSkillsForRole(roleName);
            }

            System.out.println("[ApiService] Apify fetched " + jobs.size() + " real job postings for: " + roleName);

            String prompt =
                "Below are real job descriptions for the role of '" + roleName + "', scraped from LinkedIn.\n"
                + "Extract the TOP 8 most commonly required skills across all these job postings.\n"
                + "IMPORTANT RULES:\n"
                + "- Return ONLY short, specific skill keywords (1-3 words each)\n"
                + "- Use the MOST SPECIFIC form: prefer 'PyTorch' over 'Machine Learning' if the jobs ask for PyTorch\n"
                + "- Use the PRIMARY skill, not prerequisites: if jobs ask for React, don't separately list HTML/CSS\n"
                + "- Group related skills: 'Python' covers NumPy/pandas basics; 'React' covers JSX/hooks\n"
                + "- Match the actual 2025-2026 job market terminology used on LinkedIn/Naukri\n"
                + "- Examples of good format: 'Python', 'SQL', 'React', 'Git', 'Spring Boot', 'Figma'\n"
                + "- No long phrases, no parentheses, no explanations\n"
                + "Respond ONLY with a numbered list.\n1. Skill\n2. Skill\n\n"
                + "Job Descriptions:\n" + jobDescriptions;

            String body = buildRequestBody(prompt);
            String raw  = sendGroqPost(body);
            String text = extractContent(raw);

            if (text == null || text.isBlank()) return getSkillsForRole(roleName);

            System.out.println("[ApiService] RAW Groq skill-extraction response for '" + roleName + "':\n" + text);

            List<String> skills = parseSkills(text);

            // Sanity cap: a real "top 8 skills" extraction should never return more
            // than ~12 items. If it does, something went wrong (truncated/garbled
            // response, prompt echo, etc.) — fall back to pure Groq role skills.
            if (skills.size() > 12) {
                System.out.println("[ApiService] Extraction returned " + skills.size()
                        + " items (too many, likely garbled) — falling back to Groq role skills.");
                return getSkillsForRole(roleName);
            }

            // Hard cap to top 8 even if slightly over
            if (skills.size() > 8) {
                skills = skills.subList(0, 8);
            }

            System.out.println("[ApiService] Extracted " + skills.size()
                    + " real skills from Apify/LinkedIn jobs: " + skills);
            return skills;

        } catch (Exception e) {
            System.out.println("[ApiService] Apify skill extraction ERROR: " + e.getMessage() + " — falling back to Groq.");
            return getSkillsForRole(roleName);
        }
    }

    /**
     * Backward-compatible alias — old controller code may still call this name.
     */
    public List<String> getSkillsFromJSearch(String roleName) {
        return getSkillsFromRealJobs(roleName);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Groq: fallback skill generation for custom roles
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fallback: asks Groq to generate skills for a role when Apify fails.
     * Domain-aware: detects design, business, ML, web, mobile etc.
     */
    public List<String> getSkillsForRole(String roleName) {
        if (apiKey == null || apiKey.equals("YOUR_GROQ_API_KEY_HERE")) {
            System.out.println("[ApiService] No API key — returning fallback skills.");
            return getFallbackSkills(roleName);
        }

        try {
            String prompt =
                "List exactly 8 skills required for the role of '" + roleName + "' in the 2025-2026 job market "
                + "(Naukri.com / LinkedIn India focus). This role could be in ANY field — engineering "
                + "(mechanical/civil/electrical/software), design, business, marketing, healthcare, etc.\n"
                + "IMPORTANT:\n"
                + "- Return ONLY short skill keywords (1-3 words) that people write in their resumes\n"
                + "- Use SPECIFIC tools/software/methods relevant to THIS role's actual domain, not generic categories:\n"
                + "  Software roles: specific languages/frameworks (e.g. 'React', 'Python', 'Spring Boot')\n"
                + "  Mechanical/Civil/Electrical roles: specific tools (e.g. 'AutoCAD', 'SolidWorks', 'MATLAB', "
                + "'CAD', 'Revit', 'ANSYS')\n"
                + "  Design roles: specific tools (e.g. 'Figma', 'Adobe XD')\n"
                + "  Business/Finance roles: specific tools (e.g. 'Excel', 'SAP', 'Financial Modeling')\n"
                + "  Marketing roles: specific tools (e.g. 'SEO', 'Google Analytics', 'Canva')\n"
                + "- No long phrases, no parentheses, no explanations\n"
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
    // Groq: extract skills from resume text (full semantic extraction)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Extracts technical skills from raw resume text using Groq.
     * Now also infers skills from project descriptions and experience sections,
     * not just the "Skills" section of the resume.
     */
    public List<String> extractSkillsFromResumeText(String resumeText) {
        if (apiKey == null || apiKey.equals("YOUR_GROQ_API_KEY_HERE")) {
            System.out.println("[ApiService] No API key — cannot extract skills from resume.");
            return new ArrayList<>();
        }

        try {
            String prompt =
                "Analyze this resume thoroughly and extract ALL professional/technical skills this person has, "
                + "in ANY field — engineering (mechanical, civil, electrical, software), design, business, "
                + "marketing, healthcare, etc. — whatever this resume is actually about.\n"
                + "Look at:\n"
                + "1. The explicit Skills section\n"
                + "2. Tools/technologies/methods mentioned in project descriptions and internships\n"
                + "3. Tools/technologies/methods mentioned in work experience\n"
                + "4. Infer obvious prerequisites (e.g. PyTorch → Python; SolidWorks/CAD → Mechanical Design)\n\n"
                + "Return ONLY short keyword-style skills (1-3 words each), exactly as someone writes in a resume.\n"
                + "Examples across different fields: 'Python', 'React', 'AutoCAD', 'SolidWorks', 'MATLAB', "
                + "'Mechanical Design', 'Figma', 'Excel', 'Financial Modeling', 'SEO', 'Project Management'\n"
                + "No long descriptions, no explanations, no categories.\n"
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
    // Role suggestion: uses FULL resume context + Apify real titles (LinkedIn)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Suggests 3 roles that fit the candidate's profile.
     *
     * - Uses the full skills CSV (not just first 3 tokens) for the Apify search query
     * - Builds a smart search query that captures the candidate's DOMAIN
     * - Prompt includes domain context so ML people get ML roles, designers get design roles
     * - Filters out overly senior titles for fresher/entry-level profiles
     * - Suggests REAL, currently-live job titles scraped from LinkedIn via Apify —
     *   no hardcoded role list
     *
     * @param skillsCsv  Comma-separated skills string (can be long)
     * @param experienceSummary  Optional: 1-2 line summary of experience (pass "" if unavailable)
     */
    public List<String> suggestRolesForSkills(String skillsCsv, String experienceSummary) {
        if (apiKey == null || apiKey.equals("YOUR_GROQ_API_KEY_HERE")) {
            return new ArrayList<>();
        }
        try {
            // Ask Groq to determine domain + search query directly from skills/experience —
            // works for ANY field (mechanical, civil, design, business, etc.), no hardcoded list
            String[] domainAndQuery = detectDomainAndQuery(skillsCsv, experienceSummary);
            String domain      = domainAndQuery[0];
            String searchQuery = domainAndQuery[1];

            System.out.println("[ApiService] Role suggestion domain: " + domain);
            System.out.println("[ApiService] Apify/LinkedIn search query: " + searchQuery);

            List<String> realJobTitles = fetchJobTitlesFromApify(searchQuery);

            String experienceContext = (experienceSummary != null && !experienceSummary.isBlank())
                ? "Candidate experience/background: " + experienceSummary + "\n"
                : "";

            if (!realJobTitles.isEmpty()) {
                String titlesText = String.join(", ", realJobTitles);
                String prompt =
                    "Here are real job titles CURRENTLY LIVE on LinkedIn (India): "
                    + titlesText + "\n\n"
                    + "Candidate's skills: " + skillsCsv + "\n"
                    + experienceContext
                    + "Candidate domain: " + domain + "\n\n"
                    + "Select the 3 MOST SUITABLE job titles from the list above for this candidate.\n"
                    + "Rules:\n"
                    + "- Pick titles that match the candidate's ACTUAL domain (" + domain + ") — "
                    + "do not pick unrelated-domain titles even if they appear in the list\n"
                    + "- Prefer entry-level / fresher / junior / graduate-trainee titles for candidates "
                    + "without years of experience\n"
                    + "- Only pick from the provided list — do not invent new titles\n"
                    + "- Return ONLY a clean numbered list, no explanations\n"
                    + "1. Job Title\n2. Job Title\n3. Job Title";

                String body = buildRequestBody(prompt);
                String raw  = sendGroqPost(body);
                String text = extractContent(raw);
                if (text != null && !text.isBlank()) {
                    List<String> result = parseSkills(text);
                    if (!result.isEmpty()) {
                        System.out.println("[ApiService] Apify/LinkedIn-based role suggestions: " + result);
                        return result;
                    }
                }
            }

            // Fallback: ask Groq directly with domain context (Apify returned nothing)
            String fallbackPrompt =
                "Candidate skills: " + skillsCsv + "\n"
                + experienceContext
                + "Candidate domain: " + domain + "\n\n"
                + "Suggest 3 realistic, currently in-demand job titles for the Indian job market (2025-2026) "
                + "on Naukri.com / LinkedIn / Internshala — for the candidate's ACTUAL domain "
                + "(could be mechanical, civil, electrical, software, design, business, marketing, "
                + "healthcare, finance, HR, content, sales, or anything else based on their skills).\n"
                + "Rules:\n"
                + "- Match the candidate's DOMAIN exactly — do not suggest software/tech roles for "
                + "non-technical or non-software candidates, and vice versa\n"
                + "- Use REAL, SPECIFIC titles people actually post on Naukri/LinkedIn (e.g. "
                + "'Graduate Mechanical Engineer', 'CAD Design Engineer', 'Site Engineer', "
                + "'UI/UX Designer', 'Business Analyst', 'Digital Marketing Executive', "
                + "'Machine Learning Engineer', 'HR Executive')\n"
                + "- Prefer entry-level friendly titles (Fresher, Junior, Associate, Trainee, Graduate Engineer Trainee) "
                + "if no significant work experience shown\n"
                + "- Return ONLY a numbered list, no extra text\n"
                + "1. Job Title\n2. Job Title\n3. Job Title";

            String body = buildRequestBody(fallbackPrompt);
            String raw  = sendGroqPost(body);
            String text = extractContent(raw);
            List<String> result = parseSkills(text);
            System.out.println("[ApiService] Fallback role suggestions: " + result);
            return result != null ? result : new ArrayList<>();

        } catch (Exception e) {
            System.out.println("[ApiService] Role suggestion ERROR: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Overload for backward compatibility with the old single-argument call.
     * The controller can pass just skills if experience summary is unavailable.
     */
    public List<String> suggestRolesForSkills(String skillsCsv) {
        return suggestRolesForSkills(skillsCsv, "");
    }

    /**
     * Asks Groq to determine the candidate's career domain AND a good
     * LinkedIn/Naukri search query string, directly from their skills +
     * experience — no hardcoded domain/keyword list.
     *
     * This works for ANY field: software, mechanical, civil, electrical,
     * design, business, marketing, healthcare, finance, etc. — whatever
     * Groq can infer from the resume content.
     *
     * Returns a String[2]: { domain, searchQuery }
     * Falls back to { "General", "<first skill> fresher" } on any failure.
     */
    private String[] detectDomainAndQuery(String skillsCsv, String experienceSummary) {
        String fallbackDomain = "General";
        String firstSkill = skillsCsv.split(",")[0].trim();
        String[] fallback = { fallbackDomain, (firstSkill.isEmpty() ? "fresher jobs" : firstSkill + " fresher") };

        if (apiKey == null || apiKey.equals("YOUR_GROQ_API_KEY_HERE")) {
            return fallback;
        }

        try {
            String experienceContext = (experienceSummary != null && !experienceSummary.isBlank())
                ? "Experience/background: " + experienceSummary + "\n"
                : "";

            String prompt =
                "A candidate has these skills: " + skillsCsv + "\n"
                + experienceContext + "\n"
                + "Your job:\n"
                + "1. Identify the candidate's career DOMAIN/FIELD. This can be ANYTHING — "
                + "software engineering, mechanical engineering, civil engineering, electrical engineering, "
                + "chemical engineering, UI/UX design, graphic design, business/finance, marketing, "
                + "human resources, healthcare, data science, content writing, sales, education, etc.\n"
                + "Look at the ACTUAL skills given — e.g. 'Computer-Aided Design', 'MATLAB', 'Mechanical Systems' "
                + "means Mechanical Engineering, NOT software. 'Figma', 'Wireframing' means Design, NOT engineering. "
                + "'Excel', 'Financial Modeling' means Finance/Business.\n\n"
                + "2. Based on that domain, write ONE short LinkedIn/Naukri job search query (2-4 words) "
                + "for an ENTRY-LEVEL / FRESHER role suited to this candidate. "
                + "Use REAL job titles people search for on LinkedIn/Naukri/Internshala in India, e.g.:\n"
                + "  'mechanical design engineer', 'graduate mechanical engineer', 'CAD engineer fresher', "
                + "'civil site engineer', 'electrical engineer fresher', 'machine learning engineer', "
                + "'ui ux designer fresher', 'business analyst fresher', 'digital marketing executive', "
                + "'content writer fresher', 'hr executive fresher', 'data analyst fresher'\n\n"
                + "Respond ONLY in this exact two-line format, no other text:\n"
                + "DOMAIN: <domain name>\n"
                + "QUERY: <search query>";

            String body = buildRequestBody(prompt);
            String raw  = sendGroqPost(body);
            String text = extractContent(raw);

            if (text == null || text.isBlank()) return fallback;

            String domain = null;
            String query  = null;

            for (String line : text.split("\n")) {
                line = line.trim();
                if (line.toUpperCase().startsWith("DOMAIN:")) {
                    domain = line.substring(7).trim();
                } else if (line.toUpperCase().startsWith("QUERY:")) {
                    query = line.substring(6).trim().toLowerCase()
                            .replaceAll("[\"'.]", "");
                }
            }

            if (domain == null || domain.isBlank() || query == null || query.isBlank()) {
                System.out.println("[ApiService] Domain/query detection returned incomplete data — using fallback.");
                return fallback;
            }

            return new String[]{ domain, query };

        } catch (Exception e) {
            System.out.println("[ApiService] Domain/query detection ERROR: " + e.getMessage() + " — using fallback.");
            return fallback;
        }
    }

    /**
     * Fetches real, currently-live job titles from LinkedIn via Apify.
     * Filters out overly senior titles and internships (handled separately).
     */
    private List<String> fetchJobTitlesFromApify(String searchQuery) {
        try {
            List<ApifyService.JobPosting> jobs = apifyService.fetchJobs(searchQuery, "India", 15);

            if (jobs.isEmpty()) {
                System.out.println("[ApiService] Apify found 0 job postings for: " + searchQuery);
                return new ArrayList<>();
            }

            // Seniority keywords to filter out for likely-fresher candidates
            Set<String> seniorFilters = new HashSet<>(Arrays.asList(
                "senior", "lead", "principal", "director", "head of", "vp ", "chief",
                "manager", "architect", "staff "
            ));

            List<String> titles = new ArrayList<>();
            for (ApifyService.JobPosting job : jobs) {
                if (job.title == null || job.title.isBlank()) continue;
                String title = job.title.trim();
                String titleLower = title.toLowerCase();

                if (title.length() < 70
                        && !titles.contains(title)
                        && !titleLower.contains("intern")
                        && seniorFilters.stream().noneMatch(titleLower::contains)) {
                    titles.add(title);
                }
                if (titles.size() >= 15) break;
            }

            System.out.println("[ApiService] Apify found " + titles.size()
                    + " real LinkedIn job titles for: " + searchQuery);
            if (!titles.isEmpty()) {
                System.out.println("[ApiService] Sample titles: "
                        + titles.subList(0, Math.min(5, titles.size())));
            }

            return titles;

        } catch (Exception e) {
            System.out.println("[ApiService] Apify title fetch ERROR: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Shared HTTP + parsing helpers (Groq only — Apify HTTP lives in ApifyService)
    // ─────────────────────────────────────────────────────────────────────────

    private String buildRequestBody(String prompt) {
        String escaped = prompt
                .replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");

        return "{\"model\":\"" + model + "\","
                + "\"messages\":[{\"role\":\"user\",\"content\":\"" + escaped + "\"}],"
                + "\"max_tokens\":400,\"temperature\":0.0}";
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

    private List<String> parseSkills(String text) {
        List<String> skills = new ArrayList<>();
        for (String line : text.split("\n")) {
            String s = line.trim()
                    .replaceAll("^\\d+[.)\\-]\\s*", "")
                    .replaceAll("^[-•*]\\s*", "")
                    .replaceAll("\\s*\\(.*?\\)", "")
                    .trim().toLowerCase();
            if (!s.isEmpty() && s.length() <= 60
                    && !s.startsWith("here") && !s.startsWith("these")
                    && !s.startsWith("note") && !s.startsWith("skills")) {
                skills.add(s);
            }
        }
        return skills;
    }

    private List<String> getFallbackSkills(String roleName) {
        String r = roleName.toLowerCase();
        if (r.contains("ml") || r.contains("machine learning") || r.contains("data scien"))
            return Arrays.asList("python", "scikit-learn", "pandas", "numpy",
                    "sql", "git", "data visualization", "statistics");
        if (r.contains("python") || r.contains("django") || r.contains("flask"))
            return Arrays.asList("python", "django/flask", "rest api", "sql",
                    "git", "docker", "postgresql", "unit testing");
        if (r.contains("react") || r.contains("vue") || r.contains("angular") || r.contains("frontend"))
            return Arrays.asList("react", "javascript", "html", "css",
                    "git", "rest api", "typescript", "responsive design");
        if (r.contains("figma") || r.contains("ui") || r.contains("ux") || r.contains("design"))
            return Arrays.asList("figma", "adobe xd", "user research", "wireframing",
                    "prototyping", "typography", "color theory", "usability testing");
        if (r.contains("devops") || r.contains("cloud") || r.contains("sre"))
            return Arrays.asList("docker", "kubernetes", "ci/cd", "linux",
                    "aws/gcp/azure", "terraform", "bash", "monitoring");
        if (r.contains("android") || r.contains("flutter") || r.contains("mobile"))
            return Arrays.asList("flutter/kotlin", "rest api", "sqlite", "git",
                    "android sdk", "ui development", "firebase", "play store publishing");
        if (r.contains("data analyst") || r.contains("business analyst"))
            return Arrays.asList("excel", "sql", "tableau/power bi", "python",
                    "statistics", "data visualization", "communication", "powerpoint");
        if (r.contains("marketing"))
            return Arrays.asList("seo", "google analytics", "social media", "canva",
                    "content writing", "email marketing", "excel", "copywriting");
        if (r.contains("product manager") || r.contains("product owner"))
            return Arrays.asList("product roadmap", "jira", "agile/scrum", "user research",
                    "data analysis", "stakeholder management", "figma basics", "sql");
        return Arrays.asList("programming fundamentals", "problem solving", "algorithms",
                "data structures", "git", "communication", "system design", "testing");
    }
}