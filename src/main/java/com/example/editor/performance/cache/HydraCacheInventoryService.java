package com.example.editor.performance.cache;
import java.util.List;
import org.springframework.stereotype.Service;
import com.example.editor.grammar.application.GrammarAnalysisCache;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteAnalysisCache;
import com.example.editor.validation.runtime.ValidationPlanCache;
/** Inventario transversal de cachés para hacer explícitos límites, ocupación e invalidación. */
@Service
public class HydraCacheInventoryService {
    private final ValidationPlanCache validation; private final GrammarAnalysisCache grammar; private final UpdateSiteAnalysisCache updateSites;
    /** Descripción: Inicializa el inventario a partir de las cachés gestionadas. Entradas: validation, grammar, updateSites. Salida: servicio listo. */ public HydraCacheInventoryService(ValidationPlanCache validation,GrammarAnalysisCache grammar,UpdateSiteAnalysisCache updateSites){this.validation=validation;this.grammar=grammar;this.updateSites=updateSites;}
    /** Descripción: Devuelve políticas y métricas actuales de cachés críticas. Entradas: ninguna. Salida: inventario determinista. */ public List<Descriptor> snapshot(){
        var gs=grammar.statistics(); var us=updateSites.metrics();
        return List.of(
            new Descriptor("validation-plan",validation.size(),validation.maxEntries(),"fingerprint","invalidateMetamodel","FIFO-bounded",-1,-1,-1),
            new Descriptor("grammar-analysis",gs.entries(),gs.maximumEntries(),"grammar+provider+toolchain+source+settings","invalidateGrammar","LRU+TTL",gs.hits(),gs.misses(),gs.evictions()),
            new Descriptor("update-site-analysis",us.entries(),us.maximumEntries(),"analysis fingerprint","TTL/clear","LRU+TTL",us.hits(),us.misses(),-1));
    }
    public record Descriptor(String id,int entries,int maximumEntries,String keyPolicy,String invalidationPolicy,String evictionPolicy,long hits,long misses,long evictions){}
}
