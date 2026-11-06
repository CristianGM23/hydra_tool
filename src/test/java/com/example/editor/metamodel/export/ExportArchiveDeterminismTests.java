package com.example.editor.metamodel.export;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OBS-DET-BUNDLE-ARCHIVE-TIMES: los ZIP de metamodelo (Java, TypeScript, JavaScript, Go, Python y el bundle que los contiene) deben ser
 * idénticos byte a byte entre dos exportaciones de la misma entrada, también cuando las separa más que la resolución de 2 s de la hora DOS.
 */
class ExportArchiveDeterminismTests {

    private static final long REPRODUCIBLE_ENTRY_TIME = reproducibleEntryTime();

    @Test
    void languageArchivesAndBundleAreByteIdenticalAcrossTheTwoSecondZipWindow() throws Exception {
        NeutralMetamodelExportContext context = CanonicalExportTestFixtures.context();
        Map<String, byte[]> first = exportAll(context);
        Thread.sleep(2_100L);
        Map<String, byte[]> second = exportAll(context);

        for(String format : first.keySet()) {
            assertThat(second.get(format)).as(format + " export bytes").isEqualTo(first.get(format));
        }
    }

    @Test
    void everyArchiveEntryCarriesTheReproducibleTimeAndKeepsItsContent() throws Exception {
        NeutralMetamodelExportContext context = CanonicalExportTestFixtures.context();

        for(Map.Entry<String, byte[]> archive : exportAll(context).entrySet()) {
            Map<String, byte[]> entries = read(archive.getValue());
            assertThat(entries).as(archive.getKey() + " entries").isNotEmpty();
            assertThat(entries.values()).as(archive.getKey() + " content").allSatisfy(content -> assertThat(content).isNotEmpty());
            try(ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive.getValue()))) {
                for(ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    assertThat(entry.getTime()).as(archive.getKey() + " time of " + entry.getName()).isEqualTo(REPRODUCIBLE_ENTRY_TIME);
                }
            }
        }
    }

    private static Map<String, byte[]> exportAll(NeutralMetamodelExportContext context) {
        List<MetamodelBinaryExporter> exporters = List.of(new JavaMetamodelExporter(), new TypeScriptMetamodelExporter(), new JavaScriptMetamodelExporter(),
                new GoMetamodelExporter(), new PythonMetamodelExporter());
        Map<String, byte[]> result = new LinkedHashMap<>();
        exporters.forEach(exporter -> result.put(exporter.format().id(), exporter.export(context)));
        MetamodelExportService service = new MetamodelExportService(id -> context, null, List.of(), exporters);
        result.put("bundle", service.exportBundle("company", exporters.stream().map(exporter -> exporter.format().id()).toList(), MetamodelExportOptions.defaults()).content());
        return result;
    }

    private static Map<String, byte[]> read(byte[] archive) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try(ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            for(ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) entries.put(entry.getName(), zip.readAllBytes());
        }
        return entries;
    }

    private static long reproducibleEntryTime() {
        ZipEntry probe = new ZipEntry("probe");
        probe.setTime(0L);
        return probe.getTime();
    }
}
