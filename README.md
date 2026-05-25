# Smart Skill Gap Analyzer — Web Version

A web app that analyzes your resume against real job postings and tells you exactly which skills you're missing to land your target role.

## Features

- 📄 **Resume Upload** — Upload your PDF resume; skills are extracted automatically
- 🔍 **OCR Support** — Works with both text-based and image/scanned PDFs
- 🌐 **Real Job Data** — Fetches live job postings via JSearch API to get actual required skills
- 🤖 **AI Powered** — Uses Groq (Llama 3.1) to extract and match skills intelligently
- 📊 **Skill Gap Report** — Shows matching skills, missing skills, and readiness percentage

## Stack

- **Backend**: Java 17 + Spring Boot 3.2
- **Frontend**: Plain HTML/CSS/JS (served by Spring Boot)
- **AI**: Groq API (llama-3.1-8b-instant) — free tier
- **Job Data**: JSearch API via RapidAPI — free tier
- **PDF Parsing**: Apache PDFBox 3.x + Tesseract OCR (fallback)
- **Data**: roles.json (no database)

## Project Structure

```
SkillGapWeb/
├── pom.xml
└── src/main/
    ├── java/com/skillgap/
    │   ├── SkillGapApplication.java
    │   ├── model/
    │   │   ├── User.java
    │   │   ├── Role.java
    │   │   └── AnalysisResult.java
    │   ├── service/
    │   │   ├── DataLoader.java              ← Loads roles.json
    │   │   ├── ApiService.java              ← Groq + JSearch API calls
    │   │   ├── ResumeParserService.java     ← PDFBox + Tesseract OCR
    │   │   └── SkillGapAnalyzerService.java ← Gap analysis logic
    │   └── controller/
    │       └── SkillGapController.java      ← REST endpoints
    └── resources/
        ├── application.properties
        ├── roles.json
        └── static/
            └── index.html
```

## REST Endpoints

| Method | URL                  | Description                          |
|--------|----------------------|--------------------------------------|
| GET    | /api/roles           | Returns list of predefined roles     |
| POST   | /api/parse-resume    | Extracts skills from uploaded PDF    |
| POST   | /api/analyze         | Runs skill gap analysis              |

### POST /api/analyze — Request body:
```json
{
  "name":   "Ravi",
  "skills": "java, spring boot, sql, git",
  "role":   "java developer"
}
```

## Setup & Run

### 1. Prerequisites
- Java 17+
- Maven 3.8+
- Tesseract OCR — for image-based PDF support
  - Windows: download from https://github.com/UB-Mannheim/tesseract/wiki
  - Mac: `brew install tesseract`
  - Linux: `sudo apt install tesseract-ocr`

### 2. Get API Keys
| Key | Where | Cost |
|-----|-------|------|
| Groq API key | https://console.groq.com | Free |
| RapidAPI key (JSearch) | https://rapidapi.com/letscrape-6bRBa3QguO5/api/jsearch | Free (200 req/month) |

### 3. Configure application.properties
```properties
groq.api.key=gsk_your_key_here
groq.model=llama-3.1-8b-instant
jsearch.api.key=your_rapidapi_key_here
```

### 4. Run
```bash
cd SkillGapWeb
mvn spring-boot:run
```

### 5. Open browser
```
http://localhost:8080
```

## How It Works

```
User uploads resume (PDF)
        ↓
PDFBox extracts text → if empty → Tesseract OCR kicks in
        ↓
Groq AI extracts skills from resume text
        ↓
User selects target role
        ↓
JSearch fetches 5 real job postings for that role
        ↓
Groq extracts top 8 required skills from job descriptions
        ↓
Gap analysis → matching skills, missing skills, readiness %
```

## OOP Concepts

| Concept       | Where Used                                      |
|---------------|-------------------------------------------------|
| Encapsulation | User, Role, AnalysisResult (private + getters)  |
| Abstraction   | DataLoader, ApiService, ResumeParserService     |
| Inheritance   | Extendable — CustomRole can extend Role         |
| Polymorphism  | ComparisonStrategy interface (optional)         |