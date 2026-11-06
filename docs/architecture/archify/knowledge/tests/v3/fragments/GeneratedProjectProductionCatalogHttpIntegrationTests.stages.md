GeneratedProjectProductionCatalogHttpIntegrationTests - stage mapping (renamed from the planned ...SpringIntegrationTests: it uses the real production catalog with a standalone MockMvc, not a Spring context)
- generated_projects_lifecycle.rg: registeringALangiumProjectPersistsTheRegistry... (L113-L115 registry JSON on disk, L117-L119 invalid profile/paths = 400 without change, L124 survives a restart); theProductionCatalogOffersTheFiveProfiles... (L90 profile list).
- generated_projects_lifecycle.op: theOpenStageResolvesTheProjectDirectoryWithoutRunningAnyCommand (L139 PASSED with the path computed in the test, L144 directory untouched).
- generated_projects_lifecycle.op (other profiles / missing directory): openWorksForTheOtherProductionProfiles... (L156 xtext, glsp, besser-sdk PASSED; L163 missing directory FAILED).
- Not claimed: GENERATE/VALIDATE/LAUNCH of any production profile; standalone-web lifecycle (finding #1, characterized elsewhere).
