package com.example.editor.metamodel.importing.updatesite;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class UpdateSiteTestFixtures {
    private UpdateSiteTestFixtures() { }

    static byte[] zip(Map<String, byte[]> entries) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try(ZipOutputStream zip = new ZipOutputStream(output)) {
                for(Map.Entry<String, byte[]> entry : entries.entrySet()) {
                    zip.putNextEntry(new ZipEntry(entry.getKey()));
                    zip.write(entry.getValue());
                    zip.closeEntry();
                }
            }
            return output.toByteArray();
        } catch(IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static byte[] jar(String symbolicName, String version, String requireBundle, Map<String, byte[]> entries) {
        try {
            Manifest manifest = new Manifest();
            Attributes attributes = manifest.getMainAttributes();
            attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
            attributes.putValue("Bundle-ManifestVersion", "2");
            attributes.putValue("Bundle-SymbolicName", symbolicName);
            attributes.putValue("Bundle-Version", version);
            attributes.putValue("Bundle-Name", symbolicName);
            if(requireBundle != null && !requireBundle.isBlank()) attributes.putValue("Require-Bundle", requireBundle);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try(JarOutputStream jar = new JarOutputStream(output, manifest)) {
                for(Map.Entry<String, byte[]> entry : entries.entrySet()) {
                    jar.putNextEntry(new JarEntry(entry.getKey()));
                    jar.write(entry.getValue());
                    jar.closeEntry();
                }
            }
            return output.toByteArray();
        } catch(IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static Map<String, byte[]> entries(Object... values) {
        Map<String, byte[]> result = new LinkedHashMap<>();
        for(int index = 0; index < values.length; index += 2) {
            Object value = values[index + 1];
            result.put(String.valueOf(values[index]), value instanceof byte[] bytes
                    ? bytes : String.valueOf(value).getBytes(StandardCharsets.UTF_8));
        }
        return result;
    }

    static String contentXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <repository name="Library Update Site" type="org.eclipse.equinox.internal.p2.metadata.repository.LocalMetadataRepository" version="1">
                  <units size="2">
                    <unit id="com.example.library" version="1.2.0">
                      <properties size="1"><property name="org.eclipse.equinox.p2.name" value="Library model"/></properties>
                    </unit>
                    <unit id="com.example.library.feature.feature.group" version="1.2.0">
                      <properties size="1"><property name="org.eclipse.equinox.p2.name" value="Library Feature"/></properties>
                      <requires size="1"><required namespace="org.eclipse.equinox.p2.iu" name="com.example.library" range="[1.2.0,1.3.0)"/></requires>
                    </unit>
                  </units>
                </repository>
                """;
    }

    static String artifactsXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <repository name="Library Update Site" type="org.eclipse.equinox.internal.p2.artifact.repository.simple.SimpleArtifactRepository" version="1">
                  <artifacts size="1"><artifact classifier="osgi.bundle" id="com.example.library" version="1.2.0"/></artifacts>
                </repository>
                """;
    }

    static String ecore() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <ecore:EPackage xmi:version="2.0" xmlns:xmi="http://www.omg.org/XMI" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:ecore="http://www.eclipse.org/emf/2002/Ecore" name="library" nsURI="https://example.com/library" nsPrefix="library">
                  <eClassifiers xsi:type="ecore:EClass" name="Book">
                    <eStructuralFeatures xsi:type="ecore:EAttribute" name="title" eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EString"/>
                    <eStructuralFeatures xsi:type="ecore:EReference" name="authors" upperBound="-1" eType="#//Author"/>
                    <eOperations name="label"><eType xsi:type="ecore:EDataType" href="http://www.eclipse.org/emf/2002/Ecore#//EString"/></eOperations>
                  </eClassifiers>
                  <eClassifiers xsi:type="ecore:EClass" name="Author"/>
                </ecore:EPackage>
                """;
    }
}
