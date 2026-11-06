- model_editor_visual_import.as: externalModeAnalysesTheUploadedMetamodelWithoutWritingAnything (lines 136-142 EXTERNAL association, expected classes, no files on disk).
- model_editor_visual_import.as (rejection): aMissingMetamodelOrAnUnknownModeIsRejectedWithoutWriting (lines 151-154, both 400).
- model_editor_visual_import.as (confirm): confirmingWithoutValidationDetailsStoresTheMetamodelOnly (lines 168-175) and confirmingWithValidationDetailsStoresTheNativeRules (lines 187-194).
- model_editor_visual_import.as (existing): existingModeUsesTheStoredMetamodelAndRejectsAnUnknownId (lines 205-211).
Note: the EMBEDDED (ZIP) mode is not exercised because the Sirius endpoint needs a .odesign file as the visual part; see the limits.
- model_editor_visual_import.d1 (confirm): the preview writes nothing (line 143) while confirming stores the metamodel (lines 170-175) and, with the imported validations, three rules in a document with a stable id (lines 187-194); real HTTP endpoint and on-disk oracle. The later saving of the editor is not covered here.
