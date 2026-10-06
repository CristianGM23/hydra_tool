package com.example.editor.metamodel.validation;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Impide añadir nuevos códigos del validador sin una explicación específica para el usuario. */
class MetamodelValidationAdvisorCoverageTests {
    private static final Pattern CODE = Pattern.compile("\\\"([A-Z][A-Z0-9_]+)\\\"");
    private static final Pattern CASE = Pattern.compile("case\\s+\\\"([A-Z][A-Z0-9_]+)\\\"");

    @Test void everyNeutralMetamodelValidatorCodeHasSpecificUserLevelAdvice() throws Exception {
        String validator = Files.readString(Path.of("src/main/java/com/example/editor/metamodel/validation/NeutralMetamodelValidator.java"), StandardCharsets.UTF_8);
        String advisor = Files.readString(Path.of("src/main/java/com/example/editor/metamodel/validation/MetamodelValidationAdvisor.java"), StandardCharsets.UTF_8);
        Set<String> validatorCodes = matches(CODE, validator); Set<String> advisorCodes = matches(CASE, advisor);
        assertThat(validatorCodes).hasSizeGreaterThan(40);
        assertThat(advisorCodes).containsAll(validatorCodes);
        assertThat(validatorCodes).containsAll(advisorCodes);
    }

    private Set<String> matches(Pattern pattern, String source){
        Set<String> result = new HashSet<>(); var matcher = pattern.matcher(source); while(matcher.find()){result.add(matcher.group(1));} return Set.copyOf(result);
    }
}
