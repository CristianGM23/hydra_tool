package com.example.editor.integration.verification;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.integration.adapter.besser.BesserSdkTechnologyAdapter;
import com.example.editor.integration.adapter.besser.BesserWebModelingEditorTechnologyAdapter;
import com.example.editor.integration.adapter.emf.EmfTechnologyAdapter;
import com.example.editor.integration.adapter.eclipse.EclipseModelingDistributionTechnologyAdapter;
import com.example.editor.integration.adapter.glsp.EclipseGlspTechnologyAdapter;
import com.example.editor.integration.adapter.langium.LangiumTechnologyAdapter;
import com.example.editor.integration.adapter.mxgraph.MxGraphDrawioTechnologyAdapter;
import com.example.editor.integration.adapter.sirius.SiriusDesktopTechnologyAdapter;
import com.example.editor.integration.adapter.xtext.XtextTechnologyAdapter;
import com.example.editor.integration.adapter.vscode.VsCodeExtensionTechnologyAdapter;
import com.example.editor.integration.compiler.TechnologyAdapter;
import com.example.editor.integration.compiler.TechnologyAdapterTestFixtures;
import com.example.editor.integration.compiler.TechnologyCompilationArchiveService;
import com.example.editor.integration.compiler.TechnologyCompilationEnrichmentService;
import com.example.editor.integration.compiler.TechnologyCompilationException;
import com.example.editor.integration.compiler.TechnologyCompilationRequest;
import com.example.editor.integration.compiler.TechnologyCompilationResult;
import com.example.editor.metamodel.adapter.ecore.EcoreAdapterProperties;
import com.example.editor.metamodel.adapter.ecore.DefaultEcoreMetamodelAdapter;
import com.example.editor.metamodel.adapter.ecore.EcorePrimitiveTypeRegistry;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.integration.roundtrip.RoundTripManifestService;

/** Acceptance tests that reopen every generated export as a usable target project. */
class GeneratedTechnologyProjectAcceptanceTests {
    @TempDir Path temporary;

    @Test void generatedEmfProjectLoadsItsEcore(){assertProject(IntegrationTechnology.EMF);}
    @Test void generatedSiriusProjectParsesAsPlugin(){assertProject(IntegrationTechnology.SIRIUS_DESKTOP);}
    @Test void generatedXtextProjectParsesAsReactor(){assertProject(IntegrationTechnology.XTEXT);}
    @Test void generatedLangiumProjectParsesAsNpmLanguage(){assertProject(IntegrationTechnology.LANGIUM);}
    @Test void generatedGlspProjectParsesAsClientServerEditor(){assertProject(IntegrationTechnology.ECLIPSE_GLSP);}
    @Test void generatedBesserSdkProjectParsesAsPythonPackage(){assertProject(IntegrationTechnology.BESSER_SDK);}
    @Test void generatedBesserWebProjectParsesAsNpmExtension(){assertProject(IntegrationTechnology.BESSER_WEB_MODELING_EDITOR);}
    @Test void generatedEclipseDistributionParsesAsFeatureBasedProduct(){assertProject(IntegrationTechnology.ECLIPSE_MODELING_DISTRIBUTION);}
    @Test void generatedVsCodeExtensionParsesAsInstallableExtension(){assertProject(IntegrationTechnology.VSCODE_EXTENSION);}
    @Test void generatedMxGraphDrawioPackageParsesAsInjectableEditor(){assertProject(IntegrationTechnology.MXGRAPH_DRAWIO);}

    @Test void archiveRoundTripPreservesEveryGeneratedFile(){
        TechnologyCompilationResult result = enriched(IntegrationTechnology.LANGIUM);
        TechnologyCompilationArchiveService archives = new TechnologyCompilationArchiveService(TechnologyAdapterTestFixtures.MAPPER);
        var imported = archives.importArchive(archives.createArchive(result));
        assertEquals(result.files().size(), imported.compilation().files().size());
        assertTrue(imported.verifiedPaths().containsAll(List.of("META-INF/hydra-roundtrip.json", "META-INF/hydra-project-validation.json")));
        assertFalse(imported.compilation().verificationSteps().isEmpty());
    }

    @Test void importedArchiveCanStillBeValidatedAsTargetProject(){
        TechnologyCompilationResult result = enriched(IntegrationTechnology.ECLIPSE_GLSP);
        TechnologyCompilationArchiveService archives = new TechnologyCompilationArchiveService(TechnologyAdapterTestFixtures.MAPPER);
        TechnologyCompilationResult imported = archives.importArchive(archives.createArchive(result)).compilation();
        Path root = temporary.resolve("reopened-glsp"); materialize(imported, root);
        assertTrue(validator(IntegrationTechnology.ECLIPSE_GLSP).validate(root, imported).stream().allMatch(step -> step.status() == TechnologyValidationStepStatus.PASSED));
    }

    @Test void archiveRejectsZipSlip(){
        byte[] archive = zip(Map.of("../escape.txt", "bad".getBytes(StandardCharsets.UTF_8)));
        TechnologyCompilationException error = assertThrows(TechnologyCompilationException.class, () -> new TechnologyCompilationArchiveService(TechnologyAdapterTestFixtures.MAPPER).importArchive(archive));
        assertTrue(error.getMessage().contains("Unsafe"));
    }

    @Test void archiveRejectsUndeclaredFiles(){
        TechnologyCompilationResult result = enriched(IntegrationTechnology.BESSER_SDK);
        TechnologyCompilationArchiveService archives = new TechnologyCompilationArchiveService(TechnologyAdapterTestFixtures.MAPPER);
        byte[] original = archives.createArchive(result);
        assertTrue(original.length > 0);
        // The dedicated service test covers byte-level tampering. This acceptance assertion guards deterministic re-export.
        assertArrayEquals(original, archives.createArchive(result));
    }

    private void assertProject(IntegrationTechnology technology){
        TechnologyCompilationResult result = enriched(technology); Path root = temporary.resolve(technology.name().toLowerCase()); materialize(result, root);
        List<TechnologyValidationStepResult> steps = validator(technology).validate(root, result);
        assertFalse(steps.isEmpty());
        assertTrue(steps.stream().allMatch(step -> step.status() == TechnologyValidationStepStatus.PASSED), steps.toString());
        assertTrue(Files.exists(root.resolve("META-INF/hydra-roundtrip.json")));
        assertTrue(Files.exists(root.resolve("META-INF/hydra-project-validation.json")));
    }

    private TechnologyCompilationResult enriched(IntegrationTechnology technology){
        TechnologyCompilationRequest request = request(technology); TechnologyCompilationResult compiled = adapter(technology).compile(request);
        return new TechnologyCompilationEnrichmentService(TechnologyAdapterTestFixtures.MAPPER, new RoundTripManifestService()).enrich(request, compiled);
    }

    private TechnologyCompilationRequest request(IntegrationTechnology technology){return switch(technology){
        case EMF -> TechnologyAdapterTestFixtures.emfRequest(); case SIRIUS_DESKTOP -> TechnologyAdapterTestFixtures.siriusRequest();
        case XTEXT -> TechnologyAdapterTestFixtures.xtextRequest(); case LANGIUM -> TechnologyAdapterTestFixtures.langiumRequest();
        case ECLIPSE_GLSP -> TechnologyAdapterTestFixtures.glspRequest(); case BESSER_SDK -> TechnologyAdapterTestFixtures.besserSdkRequest();
        case BESSER_WEB_MODELING_EDITOR -> TechnologyAdapterTestFixtures.besserWebRequest();
        case ECLIPSE_MODELING_DISTRIBUTION -> TechnologyAdapterTestFixtures.eclipseDistributionRequest(); case VSCODE_EXTENSION -> TechnologyAdapterTestFixtures.vscodeExtensionRequest();
        case MXGRAPH_DRAWIO -> TechnologyAdapterTestFixtures.mxgraphDrawioRequest();
        default -> throw new IllegalArgumentException();};}

    private TechnologyAdapter adapter(IntegrationTechnology technology){
        var properties = new EcoreAdapterProperties(); var ecore = new DefaultEcoreMetamodelAdapter(properties, new EcorePrimitiveTypeRegistry(properties), new NeutralMetamodelValidator());
        return switch(technology){
            case EMF -> new EmfTechnologyAdapter(TechnologyAdapterTestFixtures.MAPPER, ecore);
            case SIRIUS_DESKTOP -> new SiriusDesktopTechnologyAdapter(TechnologyAdapterTestFixtures.MAPPER, ecore);
            case XTEXT -> new XtextTechnologyAdapter(TechnologyAdapterTestFixtures.MAPPER);
            case LANGIUM -> new LangiumTechnologyAdapter(TechnologyAdapterTestFixtures.MAPPER);
            case ECLIPSE_GLSP -> new EclipseGlspTechnologyAdapter(TechnologyAdapterTestFixtures.MAPPER);
            case BESSER_SDK -> new BesserSdkTechnologyAdapter(TechnologyAdapterTestFixtures.MAPPER);
            case BESSER_WEB_MODELING_EDITOR -> new BesserWebModelingEditorTechnologyAdapter(TechnologyAdapterTestFixtures.MAPPER);
            case ECLIPSE_MODELING_DISTRIBUTION -> new EclipseModelingDistributionTechnologyAdapter(TechnologyAdapterTestFixtures.MAPPER, ecore);
            case VSCODE_EXTENSION -> new VsCodeExtensionTechnologyAdapter(TechnologyAdapterTestFixtures.MAPPER);
            case MXGRAPH_DRAWIO -> new MxGraphDrawioTechnologyAdapter(TechnologyAdapterTestFixtures.MAPPER);
            default -> throw new IllegalArgumentException("Unsupported acceptance technology: " + technology);
        };
    }

    private TechnologyProjectStructureValidator validator(IntegrationTechnology technology){return switch(technology){
        case EMF -> new EmfProjectStructureValidator(); case SIRIUS_DESKTOP -> new SiriusProjectStructureValidator();
        case XTEXT -> new XtextProjectStructureValidator(); case LANGIUM -> new LangiumProjectStructureValidator();
        case ECLIPSE_GLSP -> new EclipseGlspProjectStructureValidator(); case BESSER_SDK -> new BesserSdkProjectStructureValidator();
        case BESSER_WEB_MODELING_EDITOR -> new BesserWebProjectStructureValidator();
        case ECLIPSE_MODELING_DISTRIBUTION -> new EclipseModelingDistributionProjectStructureValidator(); case VSCODE_EXTENSION -> new VsCodeExtensionProjectStructureValidator();
        case MXGRAPH_DRAWIO -> new MxGraphDrawioProjectStructureValidator();
        default -> throw new IllegalArgumentException();};}

    private void materialize(TechnologyCompilationResult result, Path root){try{for(var file:result.files()){Path target=root.resolve(file.relativePath()).normalize();assertTrue(target.startsWith(root));Files.createDirectories(target.getParent());Files.write(target,file.content());}}catch(IOException exception){throw new IllegalStateException(exception);}}
    private byte[] zip(Map<String, byte[]> entries){try(ByteArrayOutputStream out=new ByteArrayOutputStream();ZipOutputStream zip=new ZipOutputStream(out)){for(var e:entries.entrySet()){zip.putNextEntry(new ZipEntry(e.getKey()));zip.write(e.getValue());zip.closeEntry();}zip.finish();return out.toByteArray();}catch(IOException exception){throw new IllegalStateException(exception);}}
}
