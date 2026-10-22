- x_recover_external_project.pj: theRecoveryResultSurvivesClosingAndReopeningTheProject (lines 138-140 same project reopened, 173-174 recovery folder and preserved original on disk).
- x_recover_external_project.pj (edit): theRecoveredMetamodelCanBeEditedAndSavedAfterReopening (line 190 expected classes, 211 added class on disk, 217-218 stale ETag refused with bytes unchanged) and theEditedMetamodelSurvivesAnotherCloseAndOpen (line 231).
- x_recover_external_project.integ is NOT claimed: the suggested profile is not asserted anywhere.
Note: the metamodel lives in the canonical store, not inside the project folder; reopening is evidenced by session and project files.
