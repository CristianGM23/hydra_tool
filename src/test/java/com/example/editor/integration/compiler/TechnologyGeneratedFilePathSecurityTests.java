package com.example.editor.integration.compiler;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.editor.integration.InjectionFileOwnership;

class TechnologyGeneratedFilePathSecurityTests {
    @Test void rejectsNonCanonicalOrMachineAbsoluteGeneratedPaths(){
        for(String path:List.of("../x","a/..","./x","a/./x","/x","C:/x","C:\\x","//server/x","a//x")){
            assertThrows(TechnologyCompilationException.class,()->file(path),path);
        }
    }
    @Test void preservesCanonicalNestedPath(){assertEquals("generated/model/file.json",file("generated/model/file.json").relativePath());}
    private TechnologyGeneratedFile file(String path){return new TechnologyGeneratedFile(path,"text/plain","x".getBytes(StandardCharsets.UTF_8),InjectionFileOwnership.GENERATED,true,List.of(),Map.of());}
}
