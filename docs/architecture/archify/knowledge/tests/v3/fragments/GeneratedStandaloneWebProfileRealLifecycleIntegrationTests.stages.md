GeneratedStandaloneWebProfileRealLifecycleIntegrationTests - stage mapping
- generated_projects_lifecycle.ge: theProductionStandaloneWebProfileBuildsAndVerifiesAGeneratedEditor (javac real, backend/out/HydraStandaloneServer.class on disk).
- generated_projects_lifecycle.va: same test (java --check exit 0) + validationFailsWhenTheGeneratedFrontendIsMissing (real exit code 1).
- generated_projects_lifecycle.op: same test (OPEN PASSED on the real project directory).
- generated_projects_lifecycle.la: INDIRECT only (theLaunchStagePlansTheCompiledServerWithoutAShellWrapper is a dry run; the server is not launched here).
