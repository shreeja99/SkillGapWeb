package com.skillgap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ENTRY POINT: SkillGapApplication
 * Spring Boot bootstraps the whole application from here.
 * Starts embedded Tomcat on port 8080.
 * Serves the frontend from src/main/resources/static/
 */
@SpringBootApplication
public class SkillGapApplication {
    public static void main(String[] args) {
        SpringApplication.run(SkillGapApplication.class, args);
        System.out.println("\n✅ Smart Skill Gap Analyzer is running!");
        System.out.println("   Open: http://localhost:8080\n");
    }
}