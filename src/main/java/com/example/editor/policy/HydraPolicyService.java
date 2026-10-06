package com.example.editor.policy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.example.editor.provenance.HydraDistributionProvenanceReport;
import com.example.editor.provenance.HydraDistributionProvenanceService;
import com.example.editor.persistence.support.HydraPersistenceSupport;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;

/** AY declarative policies, revocations and explainable re-evaluation. */
@Service
public class HydraPolicyService {
    private final HydraDistributionProvenanceService provenance;
    private final HydraPolicyProperties properties;
    private final ObjectMapper mapper;
    private final ObjectWriter writer;

    /** Descripción: Inicializa una instancia de HydraPolicyService con sus dependencias y datos de entrada. Entradas: provenance: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public HydraPolicyService(HydraDistributionProvenanceService provenance,
            HydraPolicyProperties properties, ObjectMapper mapper) {
        this.provenance=provenance; this.properties=properties; this.mapper=mapper;
        this.writer=mapper.writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    public synchronized HydraPolicyRule saveRule(HydraPolicyRule rule) {
        if(rule==null||rule.kind()==null) throw new IllegalArgumentException("Policy rule and kind are required");
        HydraPolicyRule safe=new HydraPolicyRule(HydraPersistenceSupport.safeId(rule.id(),"ruleId"),rule.kind(),
                HydraPersistenceSupport.text(rule.key()),HydraPersistenceSupport.text(rule.value()),rule.severity(),rule.enabled(),rule.annotations());
        write(rulePath(safe.id()),safe); HydraPersistenceSupport.trim(rulesRoot(),properties.getMaximumRules()); return safe;
    }

    public synchronized HydraRevocationEntry revoke(HydraRevocationEntry entry) {
        if(entry==null) throw new IllegalArgumentException("Revocation is required");
        HydraRevocationEntry safe=new HydraRevocationEntry(HydraPersistenceSupport.safeId(entry.id(),"revocationId"),
                HydraPersistenceSupport.text(entry.subjectType()),HydraPersistenceSupport.text(entry.subjectId()),
                HydraPersistenceSupport.text(entry.subjectSha256()),HydraPersistenceSupport.text(entry.reason()),
                entry.revokedAt(),entry.expiresAt(),entry.active(),entry.annotations());
        if(safe.subjectId().isBlank()&&safe.subjectSha256().isBlank()) throw new IllegalArgumentException("Revocation requires a subject ID or checksum");
        write(revocationPath(safe.id()),safe); HydraPersistenceSupport.trim(revocationsRoot(),properties.getMaximumRevocations()); return safe;
    }

    /** Descripción: Ejecuta la operación «rules» correspondiente a la responsabilidad de HydraPolicyService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<HydraPolicyRule> rules() {
        return readAll(rulesRoot(),HydraPolicyRule.class).stream().sorted(Comparator.comparing(HydraPolicyRule::id)).toList();
    }

    /** Descripción: Ejecuta la operación «revocations» correspondiente a la responsabilidad de HydraPolicyService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<HydraRevocationEntry> revocations() {
        return readAll(revocationsRoot(),HydraRevocationEntry.class).stream().sorted(Comparator.comparing(HydraRevocationEntry::revokedAt).reversed()).toList();
    }

    /** Descripción: Ejecuta la operación «evaluate» correspondiente a la responsabilidad de HydraPolicyService. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public HydraPolicyEvaluationReport evaluate(HydraPolicyEvaluationRequest request) {
        return evaluate(request,rules(),revocations());
    }

    /** Evaluates one AV report with an explicit immutable rule/revocation set used by BD simulations. */
    /** Descripción: Ejecuta la operación «evaluate» correspondiente a la responsabilidad de HydraPolicyService. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; selectedRules: dato de entrada necesario para la operación; selectedRevocations: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public HydraPolicyEvaluationReport evaluate(HydraPolicyEvaluationRequest request,
            List<HydraPolicyRule> selectedRules, List<HydraRevocationEntry> selectedRevocations) {
        if(request==null) throw new IllegalArgumentException("Evaluation request is required");
        HydraDistributionProvenanceReport report=provenance.find(HydraPersistenceSupport.safeId(request.provenanceReportId(),"provenanceReportId"));
        List<HydraPolicyRule> effectiveRules=selectedRules==null?List.of():List.copyOf(selectedRules);
        List<HydraRevocationEntry> effectiveRevocations=selectedRevocations==null?List.of():List.copyOf(selectedRevocations);
        List<HydraPolicyDiagnostic> diagnostics=new ArrayList<>(); List<String> revoked=new ArrayList<>(); Instant now=Instant.now();
        for(HydraRevocationEntry item:effectiveRevocations) if(item.effectiveAt(now)&&matches(item,report)) {
            revoked.add(item.subjectId().isBlank()?item.subjectSha256():item.subjectId());
            diagnostics.add(new HydraPolicyDiagnostic(item.id(),HydraPolicySeverity.ERROR,item.subjectId(),"Revoked: "+item.reason()));
        }
        for(HydraPolicyRule rule:effectiveRules) if(rule.enabled()) evaluateRule(rule,report,diagnostics);
        List<HydraPolicyDiagnostic> visible=request.includeWarnings()?diagnostics:diagnostics.stream().filter(item->item.severity()==HydraPolicySeverity.ERROR).toList();
        boolean allowed=diagnostics.stream().noneMatch(item->item.severity()==HydraPolicySeverity.ERROR);
        String fingerprint=HydraPersistenceSupport.fingerprint(writer,Map.of("rules",effectiveRules,"revocations",effectiveRevocations));
        return new HydraPolicyEvaluationReport("policy-"+report.attestationSha256().substring(0,16),report.reportId(),allowed,visible,revoked,fingerprint,now);
    }

    /** Descripción: Ejecuta la operación «evaluateRule» correspondiente a la responsabilidad de HydraPolicyService. Entradas: rule: dato de entrada necesario para la operación; report: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void evaluateRule(HydraPolicyRule rule,HydraDistributionProvenanceReport report,List<HydraPolicyDiagnostic> diagnostics) {
        boolean violation=switch(rule.kind()) {
            case REQUIRE_COMPONENT_TYPE -> report.components().stream().noneMatch(item->item.type().equals(rule.value()));
            case FORBID_LICENSE -> report.components().stream().anyMatch(item->item.license().equalsIgnoreCase(rule.value()));
            case REQUIRE_CHANNEL -> report.channel()==null||!report.channel().name().equalsIgnoreCase(rule.value());
            case REQUIRE_ANNOTATION -> !report.annotations().containsKey(rule.key())||(!rule.value().isBlank()&&!rule.value().equals(report.annotations().get(rule.key())));
            case MAX_DIAGNOSTICS -> report.diagnostics().size()>parseInt(rule.value(),0);
        };
        if(violation) diagnostics.add(new HydraPolicyDiagnostic(rule.id(),rule.severity(),report.productId(),"Policy "+rule.kind()+" was not satisfied"));
    }

    /** Descripción: Evalúa la condición «matches» sin modificar el estado externo. Entradas: item: dato de entrada necesario para la operación; report: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean matches(HydraRevocationEntry item,HydraDistributionProvenanceReport report) {
        if(!item.subjectSha256().isBlank()&&item.subjectSha256().equals(report.attestationSha256())) return true;
        if(item.subjectId().equals(report.reportId())||item.subjectId().equals(report.productId())) return true;
        return report.components().stream().anyMatch(component->item.subjectId().equals(component.componentId())||item.subjectSha256().equals(component.sha256()));
    }

    /** Descripción: Ejecuta la operación «parseInt» correspondiente a la responsabilidad de HydraPolicyService. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int parseInt(String value,int fallback) { try{return Integer.parseInt(value);}catch(Exception exception){return fallback;} }
    /** Descripción: Obtiene la información correspondiente a «readAll» dentro de HydraPolicyService. Entradas: root: ruta o ubicación utilizada por la operación; type: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private <T> List<T> readAll(Path root,Class<T> type) { List<T> result=new ArrayList<>(); for(Path path:HydraPersistenceSupport.jsonFiles(root)) try{result.add(mapper.readValue(com.example.editor.io.BoundedIo.readInternal(path),type));}catch(Exception ignored){com.example.editor.observability.HydraBestEffortDiagnostics.record("com/example/editor/policy/HydraPolicyService.java",ignored);} return result; }
    /** Descripción: Actualiza el estado gestionado por HydraPolicyService mediante la operación «write». Entradas: path: ruta o ubicación utilizada por la operación; value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void write(Path path,Object value) { try{HydraPersistenceSupport.atomicWrite(path,writer.writeValueAsBytes(value));}catch(Exception exception){throw new IllegalStateException("Could not persist policy evidence",exception);} }
    /** Descripción: Ejecuta la operación «rulesRoot» correspondiente a la responsabilidad de HydraPolicyService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private Path rulesRoot(){return properties.getStorageRoot().resolve("rules");}
    /** Descripción: Ejecuta la operación «revocationsRoot» correspondiente a la responsabilidad de HydraPolicyService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private Path revocationsRoot(){return properties.getStorageRoot().resolve("revocations");}
    /** Descripción: Ejecuta la operación «rulePath» correspondiente a la responsabilidad de HydraPolicyService. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Path rulePath(String id){return rulesRoot().resolve(id+".json");}
    /** Descripción: Ejecuta la operación «revocationPath» correspondiente a la responsabilidad de HydraPolicyService. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private Path revocationPath(String id){return revocationsRoot().resolve(id+".json");}
}
