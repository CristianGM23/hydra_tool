CapabilityPackageArchiveHttpLayerSelectionIntegrationTests - stage mapping
- portability_author_export.a (exact layers): exportingOnlyMetamodelAndValidationsProducesAnArchiveWithExactlyThoseLayers (L147 ZIP entries, literal bytes and manifest; L161 content hash recomputed).
- portability_author_export.a (difference + reproducibility): addingTheEditorLayerChangesTheArchive... (L177 different content hash with the editor layer, L179 cache hit with identical bytes).
- portability_author_export.a (restore): importingTheArchiveRestoresExactlyTheChosenLayers... (L197 only the chosen layers restored; L213 tampered archive rejected).
- portability_author_export.a (rejections): anInvalidPackageIsRejectedAndNothingIsCached (L223 missing required layer document, L237 nothing cached).
- Note: the HTTP endpoint receives a composed package; layer selection over stored documents does not exist, so it is not claimed.
