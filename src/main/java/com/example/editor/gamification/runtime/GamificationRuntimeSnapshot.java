package com.example.editor.gamification.runtime;

import com.example.editor.gamification.GamificationLedger;
import com.example.editor.gamification.GamificationRuntimeState;

public record GamificationRuntimeSnapshot(GamificationRuntimeState state, String stateEtag,
        GamificationLedger ledger, String ledgerEtag) { }
