package com.example.editor.metamodel.importing.updatesite;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateSiteMetamodelRecoveryServiceTests {
    private final UpdateSiteSchemaMetamodelDeriver schemaDeriver = new UpdateSiteSchemaMetamodelDeriver(JsonMapper.builder().build());
    private final UpdateSiteMetamodelRecoveryService service = new UpdateSiteMetamodelRecoveryService(null, schemaDeriver,
            new ClassFileStructureReader());

    @Test
    void ordersExactRecoveredDerivedAndInferredCandidatesWithoutPromotingConfidence() {
        Map<String, byte[]> files = UpdateSiteTestFixtures.entries(
                "plugins/model.jar!/model/library.ecore", UpdateSiteTestFixtures.ecore(),
                "plugins/model.jar!/model/library.genmodel", "<genmodel:GenModel xmlns:genmodel=\"http://www.eclipse.org/emf/2002/GenModel\"><genPackages><genClasses ecoreClass=\"library.ecore#//Book\"/></genPackages></genmodel:GenModel>",
                "plugins/model.jar!/plugin.xml", "<plugin><extension point=\"org.eclipse.emf.ecore.generated_package\"><package class=\"com.example.LibraryPackage\"/></extension></plugin>",
                "plugins/model.jar!/com/example/LibraryPackage.class", new byte[]{1, 2, 3},
                "plugins/model.jar!/com/example/Book.class", new byte[]{1, 2, 3},
                "plugins/model.jar!/schema/library.schema.json", "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"title\":\"Library\",\"properties\":{\"books\":{\"type\":\"array\",\"items\":{\"$ref\":\"#/$defs/Book\"}}},\"$defs\":{\"Book\":{\"type\":\"object\",\"properties\":{\"title\":{\"type\":\"string\"}}}}}");
        UpdateSiteRepositoryContent repository = new UpdateSiteRepositoryContent(files, "fingerprint", 1024);
        UpdateSiteArtifactDescriptor[] artifacts = files.entrySet().stream().map(entry -> new UpdateSiteArtifactDescriptor(
                entry.getKey(), UpdateSiteArtifactKinds.fromPath(entry.getKey()), "com.example.model", entry.getValue().length,
                UpdateSiteHashing.sha256(entry.getValue()))).toArray(UpdateSiteArtifactDescriptor[]::new);
        UpdateSiteRepositorySummary summary = new UpdateSiteRepositorySummary("site", "fingerprint", true, true,
                files.size(), 1024, List.of(), List.of(new UpdateSiteBundleDescriptor("com.example.model", "1", "model",
                UpdateSiteBundleKind.MODEL_PLUGIN, "plugins/model.jar", List.of(), List.of(), List.of(artifacts), Map.of())),
                List.of(), List.of(), List.of(), List.of());

        List<UpdateSiteRecoveryCandidate> candidates = service.discover(repository, summary);

        assertThat(candidates).isSortedAccordingTo(java.util.Comparator.comparingInt(UpdateSiteRecoveryCandidate::score).reversed()
                .thenComparing(UpdateSiteRecoveryCandidate::sourcePath));
        assertThat(candidates).anySatisfy(candidate -> {
            assertThat(candidate.sourcePath()).endsWith("library.ecore");
            assertThat(candidate.level()).isEqualTo(1);
            assertThat(candidate.confidence()).isEqualTo(UpdateSiteConfidence.EXACT);
        });
        assertThat(candidates).anyMatch(candidate -> candidate.method() == UpdateSiteRecoveryMethod.GENMODEL
                && candidate.confidence() == UpdateSiteConfidence.RECOVERED);
        assertThat(candidates).anyMatch(candidate -> candidate.format().equals("JSON_SCHEMA")
                && candidate.confidence() == UpdateSiteConfidence.DERIVED);
        assertThat(candidates).anyMatch(candidate -> candidate.method() == UpdateSiteRecoveryMethod.CODE_INFERENCE
                && candidate.confidence() == UpdateSiteConfidence.INFERRED);
    }

    @Test
    void derivesJsonSchemaAndJsonDataWithoutCallingTheNeutralJsonImporter() {
        byte[] schema = "{\"title\":\"Library\",\"properties\":{\"name\":{\"type\":\"string\"},\"books\":{\"type\":\"array\",\"items\":{\"type\":\"object\"}}}}"
                .getBytes(StandardCharsets.UTF_8);
        UpdateSiteRecoveryCandidate schemaCandidate = candidate("schema/library.json", "JSON_SCHEMA", UpdateSiteConfidence.DERIVED);
        UpdateSiteRepositoryContent schemaRepository = new UpdateSiteRepositoryContent(Map.of("schema/library.json", schema), "a", schema.length);

        var schemaModel = service.recover(schemaCandidate, schemaRepository, "library-schema");

        assertThat(schemaModel.classes()).extracting(value -> value.name()).contains("Library", "Books");
        assertThat(schemaModel.classes().stream().filter(value -> value.name().equals("Library")).findFirst().orElseThrow().attributes())
                .extracting(value -> value.name()).contains("name");

        byte[] data = "{\"title\":\"Example\",\"authors\":[{\"name\":\"Ada\"}]}".getBytes(StandardCharsets.UTF_8);
        UpdateSiteRecoveryCandidate dataCandidate = candidate("data/library.json", "JSON_DATA", UpdateSiteConfidence.DERIVED);
        UpdateSiteRepositoryContent dataRepository = new UpdateSiteRepositoryContent(Map.of("data/library.json", data), "b", data.length);

        var dataModel = service.recover(dataCandidate, dataRepository, "library-data");

        assertThat(dataModel.classes()).extracting(value -> value.name()).contains("Library", "Authors");
        assertThat(dataModel.classes().stream().filter(value -> value.name().equals("Library")).findFirst().orElseThrow().attributes())
                .extracting(value -> value.name()).contains("title");
        assertThat(dataModel.relations()).extracting(value -> value.name()).contains("authors");
    }

    @Test
    void recoversPackageIdentityFromFragmentManifestAndBuildProperties() {
        Map<String, byte[]> files = UpdateSiteTestFixtures.entries(
                "plugins/model.jar!/fragment.xml", "<fragment><extension point=\"org.eclipse.emf.ecore.generated_package\"><package uri=\"urn:library\" class=\"com.example.library.LibraryPackage\"/></extension></fragment>",
                "plugins/model.jar!/META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nExport-Package: com.example.library\n",
                "plugins/model.jar!/build.properties", "bin.includes = model/library.ecore,plugin.xml\nsource.. = src/\n");
        UpdateSiteRepositoryContent repository = new UpdateSiteRepositoryContent(files, "f", 100);
        List<UpdateSiteArtifactDescriptor> artifacts = files.entrySet().stream().map(entry -> new UpdateSiteArtifactDescriptor(
                entry.getKey(), UpdateSiteArtifactKinds.fromPath(entry.getKey()), "model", entry.getValue().length,
                UpdateSiteHashing.sha256(entry.getValue()))).toList();
        UpdateSiteRepositorySummary summary = new UpdateSiteRepositorySummary("site", "f", false, false, files.size(), 100,
                List.of(), List.of(new UpdateSiteBundleDescriptor("model", "1", "model", UpdateSiteBundleKind.MODEL_PLUGIN,
                "plugins/model.jar", List.of(), List.of(), artifacts, Map.of())), List.of(), List.of(), List.of(), List.of());

        List<UpdateSiteRecoveryCandidate> candidates = service.discover(repository, summary);

        assertThat(candidates).filteredOn(candidate -> candidate.method() == UpdateSiteRecoveryMethod.PLUGIN_REGISTRY)
                .extracting(UpdateSiteRecoveryCandidate::sourcePath)
                .contains("plugins/model.jar!/fragment.xml", "plugins/model.jar!/META-INF/MANIFEST.MF",
                        "plugins/model.jar!/build.properties");
    }

    private UpdateSiteRecoveryCandidate candidate(String path, String format, UpdateSiteConfidence confidence) {
        return new UpdateSiteRecoveryCandidate("candidate", 1, UpdateSiteRecoveryMethod.DIRECT_METAMODEL, confidence,
                path, "", format, 80, true, "test", List.of());
    }
}
