package com.example.editor.integration.verification;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExternalToolProcessRunnerTests {
    private final ExternalToolProcessRunner runner=new ExternalToolProcessRunner(new ExternalToolCircuitBreaker());
    @Test void runsAvailableProcess(){var r=runner.run("ok","Run Python",Path.of("."),List.of(python(),"-c","print('HYDRA')"),0,10,Map.of());assertEquals(TechnologyValidationStepStatus.PASSED,r.status());assertTrue(r.stdout().contains("HYDRA"));}
    @Test void reportsNonZeroExit(){var r=runner.run("bad","Fail",Path.of("."),List.of(python(),"-c","raise SystemExit(7)"),0,10,Map.of());assertEquals(TechnologyValidationStepStatus.FAILED,r.status());assertEquals(7,r.exitCode());}
    @Test void reportsUnavailableExecutable(){var r=runner.run("missing","Missing",Path.of("."),List.of("hydra-no-such-tool-xyz"),0,2,Map.of());assertEquals(TechnologyValidationStepStatus.TOOL_UNAVAILABLE,r.status());}
    @Test void timesOutLongProcess(){var r=runner.run("timeout","Timeout",Path.of("."),List.of(python(),"-c","import time;time.sleep(3)"),0,1,Map.of());assertEquals(TechnologyValidationStepStatus.TIMED_OUT,r.status());}
    @Test void opensCircuitAfterRepeatedFailures(){var breaker=new ExternalToolCircuitBreaker();breaker.failure("x");breaker.failure("x");assertTrue(breaker.allow("x"));breaker.failure("x");assertFalse(breaker.allow("x"));breaker.success("x");assertTrue(breaker.allow("x"));}
    private String python(){return System.getProperty("os.name","").toLowerCase().contains("win")?"python.exe":"python3";}
}
