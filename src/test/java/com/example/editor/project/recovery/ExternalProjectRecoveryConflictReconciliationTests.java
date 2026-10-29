package com.example.editor.project.recovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

import com.example.editor.project.LocalProjectProperties;

/**
 * Evidencia FUERTE real, sin mocks, de la reconciliación de evidencia en conflicto de
 * {@link ExternalProjectRecoveryService}: cubre directamente la etapa "d1" (¿Hay conflictos
 * bloqueantes?) y "a5" (Reconciliar conflictos) de recovery_lifecycle. Usa el mismo servicio
 * real, sin colaboradores simulados, que {@link ExternalProjectRecoveryServiceTests}, pero
 * centrado en el caso de dos fuentes semanticas que compiten por el mismo elemento objetivo.
 */
class ExternalProjectRecoveryConflictReconciliationTests {

    @Test
    void tiedExactCandidatesForTheSameTargetBlockAutomaticSelectionAndCrossReferenceEachOther() throws Exception {
        // Dos ficheros .ecore reales, independientes, declaran la MISMA clase "Person" con la
        // misma confianza EXACTA: ningun candidato puede ganar automaticamente al otro.
        byte[] archive = zip(
                "sources/moduleA/people.ecore",
                "<ecore:EPackage xmlns:ecore=\"http://www.eclipse.org/emf/2002/Ecore\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"><eClassifiers xsi:type=\"ecore:EClass\" name=\"Person\"/></ecore:EPackage>",
                "sources/moduleB/people-duplicate.ecore",
                "<ecore:EPackage xmlns:ecore=\"http://www.eclipse.org/emf/2002/Ecore\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"><eClassifiers xsi:type=\"ecore:EClass\" name=\"Person\"/></ecore:EPackage>");

        ExternalProjectRecoveryReport report = service().analyze("conflict.zip", new ByteArrayInputStream(archive), archive.length);

        List<ExternalProjectMappingEvidence> personCandidates = report.mappingEvidence().stream()
                .filter(item -> item.targetId().equals("METACLASS:Person")).toList();
        assertThat(personCandidates).hasSize(2);
        // Ninguno de los dos puede seleccionarse automaticamente: es un conflicto real que
        // requiere decision humana, tal como describe la etapa "d1".
        assertThat(personCandidates).allSatisfy(item -> assertThat(item.autoSelectable()).isFalse());
        // Cada candidato referencia al otro como alternativa reconciliada, tal como describe "a5"
        // ("eliges que evidencia conservar y construyes la vista previa reconciliada").
        assertThat(personCandidates.get(0).alternatives()).containsExactly(personCandidates.get(1).id());
        assertThat(personCandidates.get(1).alternatives()).containsExactly(personCandidates.get(0).id());
        // La propia lista de diagnosticos generales del informe advierte de que no todo es
        // seleccionable automaticamente, consistente con los dos candidatos en conflicto.
        assertThat(report.diagnostics()).anyMatch(message -> message.contains("No mapping is safe enough for automatic acceptance"));
    }

    @Test
    void aClearlyDominantExactCandidateIsAutoSelectableOverAWeakerRecoveredAlternative() throws Exception {
        // Una declaracion EClass exacta ("Widget") compite con una regla de gramatica Xtext
        // recuperada ("Widget") para el MISMO elemento objetivo, pero con una diferencia de
        // confianza real y suficiente (100 frente a 86, diferencia >= 12): aqui no hace falta
        // intervencion humana, HYDRA puede reconciliar automaticamente a favor del exacto.
        byte[] archive = zip(
                "sources/model/widget.ecore",
                "<ecore:EPackage xmlns:ecore=\"http://www.eclipse.org/emf/2002/Ecore\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"><eClassifiers xsi:type=\"ecore:EClass\" name=\"Widget\"/></ecore:EPackage>",
                "sources/language/widget.xtext",
                "grammar Widgets with org.eclipse.xtext.common.Terminals\nWidget: name=ID;");

        ExternalProjectRecoveryReport report = service().analyze("dominant.zip", new ByteArrayInputStream(archive), archive.length);

        List<ExternalProjectMappingEvidence> widgetCandidates = report.mappingEvidence().stream()
                .filter(item -> item.targetId().equals("METACLASS:Widget"))
                .sorted((a, b) -> Integer.compare(b.score(), a.score())).toList();
        assertThat(widgetCandidates).hasSize(2);
        ExternalProjectMappingEvidence winner = widgetCandidates.get(0);
        ExternalProjectMappingEvidence runnerUp = widgetCandidates.get(1);
        assertThat(winner.sourcePath()).endsWith("widget.ecore");
        assertThat(winner.score()).isEqualTo(100);
        assertThat(runnerUp.score()).isEqualTo(86);
        // Diferencia real de 14 puntos (>= 12): HYDRA reconcilia automaticamente, sin bloquear.
        assertThat(winner.autoSelectable()).isTrue();
        assertThat(runnerUp.autoSelectable()).isFalse();
        assertThat(winner.alternatives()).containsExactly(runnerUp.id());
    }

    private ExternalProjectRecoveryService service() {
        LocalProjectProperties properties = new LocalProjectProperties();
        properties.setMaxNestedArchiveDepth(4);
        properties.setMaxArchiveCompressionRatio(500);
        return new ExternalProjectRecoveryService(new ExternalProjectArchiveWalker(properties));
    }

    private byte[] zip(String... entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try(ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for(int index = 0; index < entries.length; index += 2) {
                zip.putNextEntry(new ZipEntry(entries[index]));
                zip.write(entries[index + 1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
