package com.example.editor.security.process;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HydraExternalProcessPolicyTests {
    @TempDir Path temp;

    @Test void rejectsExecutablesOutsideTheAllowlist(){
        var policy=policy();
        assertThrows(IllegalArgumentException.class,()->policy.validateCommand(List.of("sh","-c","echo unsafe")));
    }

    @Test void rejectsControlCharactersInsteadOfDelegatingToAShell(){
        var policy=policy();
        assertThrows(IllegalArgumentException.class,()->policy.validateCommand(List.of("git","status\nrm -rf .")));
    }

    @Test void keepsOnlyAllowlistedEnvironmentKeys(){
        var policy=policy();
        var builder=new ProcessBuilder("git","status");
        assertThrows(IllegalArgumentException.class,()->policy.configureMinimalEnvironment(builder,Map.of("SECRET_TOKEN","nope")));
        policy.configureMinimalEnvironment(builder,Map.of("CI","true"));
        assertEquals("true",builder.environment().get("CI"));
        assertFalse(builder.environment().containsKey("SECRET_TOKEN"));
    }

    @Test void rejectsSymbolicLinksInsideIsolatedWorkspaces() throws Exception {
        var policy=policy();
        Path target=Files.createDirectory(temp.resolve("target"));
        Path link=temp.resolve("link");
        try{Files.createSymbolicLink(link,target);}catch(UnsupportedOperationException|java.nio.file.FileSystemException exception){return;}
        assertThrows(IllegalArgumentException.class,()->policy.rejectSymbolicPath(link));
    }

    @Test void enforcesWorkspaceByteQuota() throws Exception {
        var properties=new HydraExternalProcessSecurityProperties();
        properties.setMaximumTemporaryWorkspaceBytes(1_048_576L);
        var policy=new HydraExternalProcessPolicy(properties);
        Files.write(temp.resolve("too-large.bin"),new byte[1_048_577]);
        assertThrows(HydraExternalProcessPolicy.WorkspaceQuotaViolation.class,()->policy.enforceWorkspaceQuota(temp));
    }

    private HydraExternalProcessPolicy policy(){return new HydraExternalProcessPolicy(new HydraExternalProcessSecurityProperties());}
}
