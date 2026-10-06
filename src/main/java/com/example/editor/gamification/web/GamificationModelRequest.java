package com.example.editor.gamification.web;
import com.example.editor.gamification.GamificationBindingModel;
import com.example.editor.gamification.GamificationDefinitionModel;
public record GamificationModelRequest(GamificationDefinitionModel definitions, GamificationBindingModel bindings) { }
