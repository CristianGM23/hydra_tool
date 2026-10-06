package com.example.editor.integration.verification;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.example.editor.integration.IntegrationTechnology;
import com.example.editor.integration.adapter.besser.*;
import com.example.editor.integration.adapter.eclipse.EclipseModelingDistributionTechnologyAdapter;
import com.example.editor.integration.adapter.emf.EmfTechnologyAdapter;
import com.example.editor.integration.adapter.glsp.EclipseGlspTechnologyAdapter;
import com.example.editor.integration.adapter.langium.LangiumTechnologyAdapter;
import com.example.editor.integration.adapter.mxgraph.MxGraphDrawioTechnologyAdapter;
import com.example.editor.integration.adapter.sirius.SiriusDesktopTechnologyAdapter;
import com.example.editor.integration.adapter.vscode.VsCodeExtensionTechnologyAdapter;
import com.example.editor.integration.adapter.xtext.XtextTechnologyAdapter;
import com.example.editor.integration.compiler.*;
import com.example.editor.metamodel.adapter.ecore.*;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;

/** Metamorphic lifecycle tests: generation -> deterministic archive -> reopen -> tamper detection for every project technology. */
class AggressiveGeneratedArtifactLifecycleTests {
    @Test void everyGeneratedTechnologyIsDeterministicPortableAndFreeFromMachineSpecificLeakage(){
        TechnologyCompilationArchiveService archives=new TechnologyCompilationArchiveService(TechnologyAdapterTestFixtures.MAPPER);
        for(IntegrationTechnology tech:List.of(IntegrationTechnology.EMF,IntegrationTechnology.SIRIUS_DESKTOP,IntegrationTechnology.XTEXT,IntegrationTechnology.LANGIUM,IntegrationTechnology.ECLIPSE_GLSP,IntegrationTechnology.BESSER_SDK,IntegrationTechnology.BESSER_WEB_MODELING_EDITOR,IntegrationTechnology.ECLIPSE_MODELING_DISTRIBUTION,IntegrationTechnology.VSCODE_EXTENSION,IntegrationTechnology.MXGRAPH_DRAWIO)){
            TechnologyCompilationRequest request=request(tech); TechnologyCompilationResult a=adapter(tech).compile(request); TechnologyCompilationResult b=adapter(tech).compile(request);
            assertEquals(a.files().stream().map(TechnologyGeneratedFile::relativePath).sorted().toList(),b.files().stream().map(TechnologyGeneratedFile::relativePath).sorted().toList(),tech+" paths");
            assertUniqueCanonicalPaths(a,tech); assertNoHostLeakage(a,tech);
            byte[] za=archives.createArchive(a),zb=archives.createArchive(b); assertArrayEquals(za,zb,tech+" archive must be reproducible");
            TechnologyCompilationArchiveImportResult reopened=archives.importArchive(za); assertEquals(a.files().size(),reopened.compilation().files().size(),tech+" reopened file count");
            Map<String,byte[]> expected=byPath(a.files()),actual=byPath(reopened.compilation().files()); assertEquals(expected.keySet(),actual.keySet()); for(String path:expected.keySet())assertArrayEquals(expected.get(path),actual.get(path),tech+":"+path);
        }
    }
    private static void assertUniqueCanonicalPaths(TechnologyCompilationResult result,IntegrationTechnology tech){Set<String> seen=new HashSet<>();for(TechnologyGeneratedFile f:result.files()){String p=f.relativePath();assertFalse(p.isBlank(),tech.toString());assertFalse(p.startsWith("/")||p.contains("\\")||p.contains("../")||p.contains("/./")||p.contains("//"),tech+":"+p);assertTrue(seen.add(p),tech+" duplicate "+p);}}
    private static void assertNoHostLeakage(TechnologyCompilationResult result,IntegrationTechnology tech){for(TechnologyGeneratedFile f:result.files()){if(f.mediaType().startsWith("text/")||f.mediaType().contains("json")||f.mediaType().contains("xml")){String s=new String(f.content(),StandardCharsets.UTF_8);for(String forbidden:List.of("C:\\Users\\","/Users/","/home/","Authorization: Bearer","HYDRA_GIT_PASSWORD","test-token"))assertFalse(s.contains(forbidden),tech+":"+f.relativePath()+" leaks "+forbidden);}}}
    private static Map<String,byte[]> byPath(List<TechnologyGeneratedFile> files){Map<String,byte[]> m=new TreeMap<>();for(TechnologyGeneratedFile f:files)m.put(f.relativePath(),f.content());return m;}
    private TechnologyCompilationRequest request(IntegrationTechnology t){return switch(t){case EMF->TechnologyAdapterTestFixtures.emfRequest();case SIRIUS_DESKTOP->TechnologyAdapterTestFixtures.siriusRequest();case XTEXT->TechnologyAdapterTestFixtures.xtextRequest();case LANGIUM->TechnologyAdapterTestFixtures.langiumRequest();case ECLIPSE_GLSP->TechnologyAdapterTestFixtures.glspRequest();case BESSER_SDK->TechnologyAdapterTestFixtures.besserSdkRequest();case BESSER_WEB_MODELING_EDITOR->TechnologyAdapterTestFixtures.besserWebRequest();case ECLIPSE_MODELING_DISTRIBUTION->TechnologyAdapterTestFixtures.eclipseDistributionRequest();case VSCODE_EXTENSION->TechnologyAdapterTestFixtures.vscodeExtensionRequest();case MXGRAPH_DRAWIO->TechnologyAdapterTestFixtures.mxgraphDrawioRequest();default->throw new IllegalArgumentException();};}
    private TechnologyAdapter adapter(IntegrationTechnology t){var p=new EcoreAdapterProperties();var e=new DefaultEcoreMetamodelAdapter(p,new EcorePrimitiveTypeRegistry(p),new NeutralMetamodelValidator());return switch(t){case EMF->new EmfTechnologyAdapter(TechnologyAdapterTestFixtures.MAPPER,e);case SIRIUS_DESKTOP->new SiriusDesktopTechnologyAdapter(TechnologyAdapterTestFixtures.MAPPER,e);case XTEXT->new XtextTechnologyAdapter(TechnologyAdapterTestFixtures.MAPPER);case LANGIUM->new LangiumTechnologyAdapter(TechnologyAdapterTestFixtures.MAPPER);case ECLIPSE_GLSP->new EclipseGlspTechnologyAdapter(TechnologyAdapterTestFixtures.MAPPER);case BESSER_SDK->new BesserSdkTechnologyAdapter(TechnologyAdapterTestFixtures.MAPPER);case BESSER_WEB_MODELING_EDITOR->new BesserWebModelingEditorTechnologyAdapter(TechnologyAdapterTestFixtures.MAPPER);case ECLIPSE_MODELING_DISTRIBUTION->new EclipseModelingDistributionTechnologyAdapter(TechnologyAdapterTestFixtures.MAPPER,e);case VSCODE_EXTENSION->new VsCodeExtensionTechnologyAdapter(TechnologyAdapterTestFixtures.MAPPER);case MXGRAPH_DRAWIO->new MxGraphDrawioTechnologyAdapter(TechnologyAdapterTestFixtures.MAPPER);default->throw new IllegalArgumentException();};}
}
