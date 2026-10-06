package com.example.editor.gamification;
public record GamificationSaveResponse(GamificationDefinitionModel definitions, String definitionEtag,
        GamificationBindingModel bindings, String bindingEtag, GamificationCheckReport checkReport) { }
