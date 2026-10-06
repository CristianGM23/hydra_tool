package com.example.editor.integration.injection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.integration.InjectionFileChangeKind;
import com.example.editor.integration.InjectionFileOwnership;
import com.example.editor.integration.InjectionPlanStatus;
import com.example.editor.integration.InjectionPolicy;
import com.example.editor.integration.IntegrationProfileModel;
import com.example.editor.integration.compiler.TechnologyAdapterTestFixtures;
import com.example.editor.integration.compiler.TechnologyCompilationException;
import com.example.editor.integration.compiler.TechnologyCompilationResult;
import com.example.editor.integration.compiler.TechnologyGeneratedFile;
import tools.jackson.databind.json.JsonMapper;

class SafeTechnologyInjectionServiceTests {
    @TempDir Path temporary;
    private TechnologyInjectionProperties properties;
    private SafeTechnologyInjectionService service;

    @BeforeEach void setUp(){properties=new TechnologyInjectionProperties();properties.setAllowedRoot(temporary);service=new SafeTechnologyInjectionService(properties,JsonMapper.builder().findAndAddModules().build());}
    @Test void previewsAddedFilesWithoutWriting() throws Exception {var preview=service.preview(profile(false,true),result("one"),"project");assertThat(preview.plan().status()).isEqualTo(InjectionPlanStatus.READY);assertThat(preview.plan().fileChanges()).singleElement().extracting(value->value.changeKind()).isEqualTo(InjectionFileChangeKind.ADD);assertThat(Files.exists(temporary.resolve("project/generated-integration/people-emf/generated.txt"))).isFalse();}
    @Test void appliesGeneratedFilesAndPersistsManifest() throws Exception {var applied=service.apply(profile(false,true),result("one"),"project");assertThat(Files.readString(temporary.resolve("project/generated-integration/people-emf/generated.txt"))).isEqualTo("one");assertThat(Files.exists(temporary.resolve("project/.hydra/injections/manifests/"+applied.manifest().id()+".json"))).isTrue();}
    @Test void rollbacksAddedFiles() throws Exception {var applied=service.apply(profile(false,true),result("one"),"project");var rollback=service.rollback("project",applied.manifest().id());assertThat(rollback.deletedFiles()).containsExactly("generated-integration/people-emf/generated.txt");assertThat(Files.exists(temporary.resolve("project/generated-integration/people-emf/generated.txt"))).isFalse();}
    @Test void detectsManualFileConflicts() throws Exception {Path target=temporary.resolve("project/generated-integration/people-emf/generated.txt");Files.createDirectories(target.getParent());Files.writeString(target,"manual");var preview=service.preview(profile(false,true),result("generated"),"project");assertThat(preview.plan().status()).isEqualTo(InjectionPlanStatus.BLOCKED);assertThat(preview.manualConflictFiles()).containsExactly("generated-integration/people-emf/generated.txt");}
    @Test void updatesAnUnmodifiedManagedFileWhenExplicitlyAllowed() throws Exception {service.apply(profile(false,true),result("one"),"project");var preview=service.preview(profile(true,true),result("two"),"project");assertThat(preview.plan().status()).isEqualTo(InjectionPlanStatus.READY);assertThat(preview.plan().fileChanges()).singleElement().extracting(value->value.changeKind()).isEqualTo(InjectionFileChangeKind.MODIFY);service.apply(profile(true,true),result("two"),"project");assertThat(Files.readString(temporary.resolve("project/generated-integration/people-emf/generated.txt"))).isEqualTo("two");}
    @Test void detectsManualChangesToManagedFiles() throws Exception {service.apply(profile(false,true),result("one"),"project");Files.writeString(temporary.resolve("project/generated-integration/people-emf/generated.txt"),"manual");var preview=service.preview(profile(true,true),result("two"),"project");assertThat(preview.plan().status()).isEqualTo(InjectionPlanStatus.BLOCKED);assertThat(preview.plan().diagnostics()).extracting(value->value.code()).contains("MANUAL_CODE_CONFLICT");}
    @Test void allowsExplicitPatchingWhenCustomCodePreservationIsDisabled() throws Exception {Path target=temporary.resolve("project/generated-integration/people-emf/generated.txt");Files.createDirectories(target.getParent());Files.writeString(target,"manual");var preview=service.preview(profile(true,false),result("generated"),"project");assertThat(preview.plan().status()).isEqualTo(InjectionPlanStatus.READY);assertThat(preview.plan().fileChanges()).singleElement().extracting(value->value.ownership()).isEqualTo(InjectionFileOwnership.PATCHED);}
    @Test void identifiesUnchangedFiles() throws Exception {service.apply(profile(false,true),result("same"),"project");var preview=service.preview(profile(true,true),result("same"),"project");assertThat(preview.unchangedFiles()).containsExactly("generated-integration/people-emf/generated.txt");assertThat(preview.plan().fileChanges()).isEmpty();}
    @Test void rejectsUnsafeTargetDirectory(){assertThatThrownBy(()->service.preview(profile(false,true),result("one"),"../outside")).isInstanceOf(IllegalArgumentException.class);}
    @Test void enforcesFileCountLimit(){properties.setMaximumFiles(0);assertThatThrownBy(()->service.preview(profile(false,true),result("one"),"project")).isInstanceOf(TechnologyCompilationException.class).hasMessageContaining("count");}
    @Test void enforcesPerFileSizeLimit(){properties.setMaximumFileBytes(1);assertThatThrownBy(()->service.preview(profile(false,true),result("one"),"project")).hasMessageContaining("file exceeds");}
    @Test void rejectsOperationsWhenInjectionIsDisabled(){properties.setEnabled(false);assertThatThrownBy(()->service.preview(profile(false,true),result("one"),"project")).hasMessageContaining("disabled");}

    private IntegrationProfileModel profile(boolean allowGenerated,boolean preserveCustom){var source=TechnologyAdapterTestFixtures.emfRequest().profile();InjectionPolicy policy=new InjectionPolicy(source.injectionPolicy().strategy(),source.injectionPolicy().generatedRoot(),source.injectionPolicy().moduleName(),true,true,preserveCustom,!preserveCustom,allowGenerated,true);return new IntegrationProfileModel(source.schemaVersion(),source.id(),source.metamodelId(),source.name(),source.target(),source.deliveryMode(),source.features(),source.artifacts(),source.mappings(),policy,source.requiredCapabilities(),source.annotations());}
    private TechnologyCompilationResult result(String value){var file=new TechnologyGeneratedFile("generated-integration/people-emf/generated.txt","text/plain",value.getBytes(StandardCharsets.UTF_8),InjectionFileOwnership.GENERATED,true,List.of("people"),Map.of());return new TechnologyCompilationResult(com.example.editor.integration.IntegrationTechnology.EMF,List.of(file),List.of(),List.of("install"),List.of("rollback"),List.of(),List.of(),Map.of("generatorVersion","test"));}
}
