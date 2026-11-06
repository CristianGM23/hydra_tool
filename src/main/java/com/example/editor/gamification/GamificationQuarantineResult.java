package com.example.editor.gamification;

/**
 * Result of the explicit quarantine (POST …/{metamodelId}/gamification/recovery/quarantine). replayed is true when the request completed
 * an interrupted quarantine or repeated one that had already completed; the documents that were readable are returned so that a clean
 * configuration can be created from them.
 */
public record GamificationQuarantineResult(String metamodelId, boolean replayed, GamificationQuarantineRecord quarantine,
        GamificationDefinitionModel recoveredDefinitions, GamificationBindingModel recoveredBindings, String nextStep) { }
