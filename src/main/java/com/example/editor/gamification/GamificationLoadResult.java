package com.example.editor.gamification;
public record GamificationLoadResult(GamificationDefinitionModel definitions, String definitionEtag,
        GamificationBindingModel bindings, String bindingEtag, GamificationCheckReport checkReport) { }
