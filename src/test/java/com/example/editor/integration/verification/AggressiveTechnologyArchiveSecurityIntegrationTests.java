package com.example.editor.integration.verification;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import com.example.editor.integration.compiler.*;

/** New adversarial integration checks for the generated-software archive boundary. */
class AggressiveTechnologyArchiveSecurityIntegrationTests {
    @Test void importerRejectsEveryNonCanonicalPathShape() {
        TechnologyCompilationArchiveService service=new TechnologyCompilationArchiveService(TechnologyAdapterTestFixtures.MAPPER);
        for(String path:List.of("../escape.txt","a/..","./a.txt","a/./b.txt","/absolute.txt","C:/temp/a.txt","C:\\temp\\a.txt","//server/share/a.txt","a//b.txt")){
            TechnologyCompilationException error=assertThrows(TechnologyCompilationException.class,()->service.importArchive(zip(path,"payload")) ,path);
            assertTrue(error.getMessage().contains("Unsafe"),path+" -> "+error.getMessage());
        }
    }

    @Test void archiveDetectsPayloadMutationEvenWhenTheZipStructureStillParses() throws Exception {
        TechnologyCompilationArchiveService service=new TechnologyCompilationArchiveService(TechnologyAdapterTestFixtures.MAPPER);
        TechnologyCompilationResult source=new com.example.editor.integration.adapter.langium.LangiumTechnologyAdapter(TechnologyAdapterTestFixtures.MAPPER).compile(TechnologyAdapterTestFixtures.langiumRequest());
        byte[] archive=service.createArchive(source); Map<String,byte[]> entries=unzip(archive); String victim=entries.keySet().stream().filter(k->!k.startsWith("META-INF/")).findFirst().orElseThrow(); entries.get(victim)[0]^=1;
        TechnologyCompilationException error=assertThrows(TechnologyCompilationException.class,()->service.importArchive(zip(entries)));
        assertTrue(error.getMessage().contains("checksum mismatch"));
    }

    @Test void archiveRejectsDuplicateDeclaredFilesInsteadOfAcceptingLastWriterWins() throws Exception {
        TechnologyCompilationArchiveService service=new TechnologyCompilationArchiveService(TechnologyAdapterTestFixtures.MAPPER);
        TechnologyCompilationResult source=new com.example.editor.integration.adapter.langium.LangiumTechnologyAdapter(TechnologyAdapterTestFixtures.MAPPER).compile(TechnologyAdapterTestFixtures.langiumRequest());
        byte[] archive=service.createArchive(source); Map<String,byte[]> entries=unzip(archive); String duplicate=entries.keySet().stream().filter(k->!k.startsWith("META-INF/")).findFirst().orElseThrow();
        String alias=duplicate.contains("/")?duplicate.replace('/', '\\'):"folder\\..\\"+duplicate;
        ByteArrayOutputStream out=new ByteArrayOutputStream(); try(ZipOutputStream z=new ZipOutputStream(out)){for(var e:entries.entrySet()){put(z,e.getKey(),e.getValue());} put(z,alias,entries.get(duplicate));}
        TechnologyCompilationException error=assertThrows(TechnologyCompilationException.class,()->service.importArchive(out.toByteArray()));
        assertTrue(error.getMessage().contains("Duplicate") || error.getMessage().contains("Unsafe"));
    }

    private static byte[] zip(String path,String value){return zip(Map.of(path,value.getBytes(StandardCharsets.UTF_8)));}
    private static byte[] zip(Map<String,byte[]> entries){try{ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(out)){for(var e:entries.entrySet())put(z,e.getKey(),e.getValue());}return out.toByteArray();}catch(IOException e){throw new UncheckedIOException(e);}}
    private static void put(ZipOutputStream z,String path,byte[] bytes)throws IOException{ZipEntry e=new ZipEntry(path);e.setTime(0);z.putNextEntry(e);z.write(bytes);z.closeEntry();}
    private static Map<String,byte[]> unzip(byte[] bytes)throws IOException{Map<String,byte[]> result=new LinkedHashMap<>();try(ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(bytes))){ZipEntry e;while((e=z.getNextEntry())!=null)result.put(e.getName(),z.readAllBytes());}return result;}
}
