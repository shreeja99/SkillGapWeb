package com.skillgap.service;

import com.skillgap.model.AnalysisResult;
import com.skillgap.model.Role;
import com.skillgap.model.User;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * SERVICE: SkillGapAnalyzerService
 * OOP: Abstraction + Single Responsibility
 * Core logic: compares User skills vs Role skills → AnalysisResult
 * No I/O, no API calls — pure calculation.
 */
@Service
public class SkillGapAnalyzerService {

    /**
     * Compares user skills with role's required skills.
     * Calculates gap % and readiness %.
     * Returns a fully populated AnalysisResult.
     */
    public AnalysisResult analyze(User user, Role role) {
        AnalysisResult result = new AnalysisResult();
        result.setUserName(user.getName());
        result.setRoleName(role.getRoleName());
        result.setCustomRole(role.isCustomRole());
        result.setRequiredSkills(role.getRequiredSkills());

        List<String> userSkills     = user.getCurrentSkills();
        List<String> requiredSkills = role.getRequiredSkills();

        if (requiredSkills.isEmpty()) {
            result.setTotalRequired(0);
            result.setGapPercentage(0);
            result.setReadinessPercentage(0);
            result.setReadinessMessage("No required skills data found.");
            result.setReadinessEmoji("❓");
            return result;
        }

        List<String> matching = new ArrayList<>();
        List<String> missing  = new ArrayList<>();

        for (String required : requiredSkills) {
            if (skillMatches(userSkills, required)) matching.add(required);
            else                                    missing.add(required);
        }

        int total    = requiredSkills.size();
        double gap   = Math.round(((double) missing.size()  / total) * 10000.0) / 100.0;
        double ready = Math.round(((double) matching.size() / total) * 10000.0) / 100.0;

        result.setMatchingSkills(matching);
        result.setMissingSkills(missing);
        result.setTotalRequired(total);
        result.setGapPercentage(gap);
        result.setReadinessPercentage(ready);
        result.setReadinessMessage(getReadinessMessage(ready));
        result.setReadinessEmoji(getReadinessEmoji(ready));

        return result;
    }

    /**
     * Checks if a required skill is in the user's skill list.
     * Uses partial matching to handle slight variations.
     */
    private boolean skillMatches(List<String> userSkills, String required) {
        for (String userSkill : userSkills) {
            if (userSkill.equals(required)) return true;
            if (userSkill.length() >= 4 && required.length() >= 4) {
                if (userSkill.contains(required) || required.contains(userSkill)) return true;
            }
        }
        return false;
    }

    private String getReadinessMessage(double r) {
        if (r == 100)  return "Perfect match! You meet every requirement.";
        if (r >= 80)   return "Excellent — you're almost there!";
        if (r >= 60)   return "Good progress — a few more skills to go.";
        if (r >= 40)   return "Keep learning — you're building momentum.";
        if (r >= 20)   return "Early stage — great foundation to grow from.";
        return               "Just starting — every expert began here!";
    }

    private String getReadinessEmoji(double r) {
        if (r == 100) return "🏆";
        if (r >= 80)  return "🌟";
        if (r >= 60)  return "💪";
        if (r >= 40)  return "📚";
        if (r >= 20)  return "🚀";
        return              "🌱";
    }
}