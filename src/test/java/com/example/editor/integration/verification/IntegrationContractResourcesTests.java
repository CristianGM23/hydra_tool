package com.example.editor.integration.verification;

import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class IntegrationContractResourcesTests {
    private final JsonMapper mapper=JsonMapper.builder().findAndAddModules().build();
    @Test void roundTripSchemaAndExampleArePublished(){assertJson("schemas/integration/round-trip-manifest-1.0.schema.json");assertJson("examples/integration/round-trip-manifest.example.json");}
    @Test void validationReportSchemaAndExampleArePublished(){assertJson("schemas/integration/technology-project-validation-report-1.0.schema.json");assertJson("examples/integration/technology-project-validation-report.example.json");}
    @Test void projectionImportSchemaIsPublished(){assertJson("schemas/integration/canonical-projection-import-result-1.0.schema.json");}
    private void assertJson(String path){try(InputStream input=getClass().getClassLoader().getResourceAsStream(path)){assertNotNull(input,path);assertNotNull(mapper.readTree(input),path);}catch(Exception e){fail(path,e);}}
}
