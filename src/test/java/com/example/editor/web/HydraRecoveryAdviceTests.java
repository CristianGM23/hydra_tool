package com.example.editor.web;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class HydraRecoveryAdviceTests {
    @Test void conflictOffersOnlySafeRefreshAsDirectRecovery(){
        var advice=HydraRecoveryAdvice.from("CONFLICT");
        assertThat(advice.retryable()).isTrue();
        assertThat(advice.actions()).anyMatch(action->action.kind().equals("REFRESH")&&action.safe());
        assertThat(advice.actions()).noneMatch(action->action.kind().equals("RETRY")&&action.safe());
    }
    @Test void internalErrorsRequireDiagnosticsInsteadOfAutomaticRetry(){
        var advice=HydraRecoveryAdvice.from("INTERNAL_ERROR");
        assertThat(advice.retryable()).isFalse();
        assertThat(advice.actions()).containsExactly(new HydraRecoveryAction("open-diagnostics","Open diagnostics using the correlation ID before changing configuration or data.","OPEN_DIAGNOSTICS",true));
    }
}
