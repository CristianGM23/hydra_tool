package com.example.editor.gamification;
public record GamificationSummary(String metamodelId, String definitionModelId, int ruleCount, int levelCount,
        int achievementCount, String definitionEtag, String bindingEtag) { }
