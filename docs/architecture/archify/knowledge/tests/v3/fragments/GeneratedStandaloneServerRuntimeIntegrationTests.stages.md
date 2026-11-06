GeneratedStandaloneServerRuntimeIntegrationTests - stage mapping (merges groups generated-editor-server-runs and standalone-editor-generate-compile-run)
- x_generate_certify_distribute.gen / x_language_to_published_editor.gen (generate + compile): L95 javac exit 0 on the generated server; L122 the generated server announces it is listening.
- Same stages (serve): theGeneratedServerCompilesStartsAndServesTheGeneratedEditor (L181 entry page bytes = generated file, L192 configuration bytes = generated descriptor).
- x_language_to_published_editor.gen (persistence): modelsArePersistedWithOptimisticConcurrency... (L210 ETag = recomputed SHA-256, L222 update, L225 stale ETag 412, L240 delete with current ETag 204).
- x_language_to_published_editor.gen (limit): aModelLargerThanTheGeneratedLimitIsRejectedWithoutWriting (L251 413 and no file on disk).
- Not claimed: start scripts, container targets, frontend code, textual editors.
