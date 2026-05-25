package com.skillgap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
public class SkillGapApplication {
    public static void main(String[] args) {
        SpringApplication.run(SkillGapApplication.class, args);
        System.out.println("\n✅ Smart Skill Gap Analyzer is running!");
        System.out.println("   Open: http://localhost:8080\n");
    }
}