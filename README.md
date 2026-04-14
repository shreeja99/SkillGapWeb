# Smart Skill Gap Analyzer — Web Version

## Stack
- **Backend**: Java 17 + Spring Boot 3.2
- **Frontend**: Plain HTML/CSS/JS (served by Spring Boot)
- **API**: Groq (free tier) for custom roles
- **Data**: roles.json (no database)

## Project Structure

```
SkillGapWeb/
├── pom.xml
└── src/main/
    ├── java/com/skillgap/
    │   ├── SkillGapApplication.java       ← Entry point
    │   ├── model/
    │   │   ├── User.java
    │   │   ├── Role.java
    │   │   └── AnalysisResult.java
    │   ├── service/
    │   │   ├── DataLoader.java            ← Loads roles.json
    │   │   ├── ApiService.java            ← Groq API calls
    │   │   └── SkillGapAnalyzerService.java ← Gap logic
    │   └── controller/
    │       └── SkillGapController.java    ← REST endpoints
    └── resources/
        ├── application.properties
        ├── roles.json
        └── static/
            └── index.html                 ← Frontend UI
```

## REST Endpoints

| Method | URL           | Description                        |
|--------|---------------|------------------------------------|
| GET    | /api/roles    | Returns list of predefined roles   |
| POST   | /api/analyze  | Runs skill gap analysis            |

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

### 2. Set Groq API Key (optional)
Edit `src/main/resources/application.properties`:
```
groq.api.key=gsk_your_key_here
```
Get a free key at: https://console.groq.com

### 3. Run
```bash
cd SkillGapWeb
mvn spring-boot:run
```

### 4. Open browser
```
http://localhost:8080
```

## OOP Concepts

| Concept        | Where Used                                      |
|----------------|-------------------------------------------------|
| Encapsulation  | User, Role, AnalysisResult (private + getters)  |
| Abstraction    | DataLoader, ApiService, SkillGapAnalyzerService |
| Inheritance    | Extendable — CustomRole extends Role            |
| Polymorphism   | ComparisonStrategy interface (optional)         |