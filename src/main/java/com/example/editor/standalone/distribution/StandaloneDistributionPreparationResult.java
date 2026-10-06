package com.example.editor.standalone.distribution;

/** Result of materializing and validating one distribution profile. */
public record StandaloneDistributionPreparationResult(
        StandaloneDistributionMaterializationResult materialization,
        StandaloneDistributionValidationReport validation) {
    public StandaloneDistributionPreparationResult {
        if(materialization == null){throw new IllegalArgumentException("materialization is required");}
        if(validation == null){throw new IllegalArgumentException("validation is required");}
    }
}
