package com.example.editor.project.portability;

import static org.assertj.core.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import com.example.editor.project.ProjectException;
import com.example.editor.testing.fault.HydraFaultInjectionProperties;
import com.example.editor.testing.fault.HydraFaultInjectionService;
import tools.jackson.databind.json.JsonMapper;

/** Un paquete portable con manifiesto que no es JSON es una entrada del usuario malformada (400), nunca un error interno. */
class ProjectPortablePackageServiceTests {
    private final ProjectPortablePackageService service=new ProjectPortablePackageService(null,JsonMapper.builder().build(),
            new HydraFaultInjectionService(new HydraFaultInjectionProperties()));

    @Test void malformedManifestIsAnInvalidRequest() throws Exception {
        assertThatThrownBy(() -> service.importPortable("parent","name",packageWith("{ broken")))
                .isInstanceOf(ProjectException.class).extracting("reason").isEqualTo(ProjectException.Reason.INVALID_REQUEST);
    }

    @Test void manifestOfTheWrongShapeIsAnInvalidRequest() throws Exception {
        assertThatThrownBy(() -> service.importPortable("parent","name",packageWith("[1,2,3]")))
                .isInstanceOf(ProjectException.class).extracting("reason").isEqualTo(ProjectException.Reason.INVALID_REQUEST);
    }

    private static byte[] packageWith(String manifest) throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("hydra-portable-manifest.json")); zip.write(manifest.getBytes(java.nio.charset.StandardCharsets.UTF_8)); zip.closeEntry();
            zip.putNextEntry(new ZipEntry("project.zip")); zip.write(new byte[]{1,2,3}); zip.closeEntry();
        }
        return output.toByteArray();
    }
}
