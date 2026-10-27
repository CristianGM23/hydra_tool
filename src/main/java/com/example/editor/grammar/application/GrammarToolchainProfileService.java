package com.example.editor.grammar.application;

import java.util.*;
import org.springframework.stereotype.Service;
import com.example.editor.grammar.core.*;

/** Server-owned, version-pinned toolchain catalog. No browser-supplied command line is accepted. */
@Service
public class GrammarToolchainProfileService {
    private final Map<String,GrammarToolchainProfile> profiles;

    /** Descripción: Inicializa una instancia de GrammarToolchainProfileService con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ public GrammarToolchainProfileService(){
        Map<String,GrammarToolchainProfile> map=new LinkedHashMap<>();
        // Generated Langium projects run Vitest 5, whose engines field is ^22.12.0 || ^24.0.0 || >=26.0.0: Node 22.12 is the minimum.
        add(map,new GrammarToolchainProfile("langium-4.3-browser",GrammarTechnology.LANGIUM,"4.3.0","4.3.0","","22.12-24.19","npm","TypeScript",false,8_000,60_000,512L*1024*1024,GrammarExecutionMode.HYBRID,
                Map.of("languageServer","true","browserWorker","true","migration","explicit")));
        // langium-cli 4.4 adds the $id its config schema needs: earlier CLIs fail `langium generate` on Node >= 24.20, whose WHATWG URL parser
        // rejects jsonschema's relative refs against an opaque base. 4.3 stays selectable for existing grammars; new grammars default to 4.4.
        add(map,new GrammarToolchainProfile("langium-4.4-browser",GrammarTechnology.LANGIUM,"4.4.0","4.4.0","","22.12+","npm","TypeScript",false,8_000,60_000,512L*1024*1024,GrammarExecutionMode.HYBRID,
                Map.of("languageServer","true","browserWorker","true","migration","explicit","recommendedNodeVersion","26.12.1")));
        add(map,new GrammarToolchainProfile("xtext-2.44-lsp",GrammarTechnology.XTEXT,"2.44.0","2.44.0","21+","","maven","Java",false,10_000,120_000,1024L*1024*1024,GrammarExecutionMode.HYBRID,
                Map.of("languageServer","true","ecoreBridge","true","xtextWeb","false","migration","explicit")));
        add(map,new GrammarToolchainProfile("antlr-4.13.2-java",GrammarTechnology.ANTLR,"4.13.2","4.13.2","17+","","maven","Java",false,8_000,60_000,512L*1024*1024,GrammarExecutionMode.HYBRID,
                Map.of("visitor","true","listener","true","semanticMapping","explicit")));
        add(map,new GrammarToolchainProfile("antlr-4.13.2-typescript",GrammarTechnology.ANTLR,"4.13.2","4.13.2","","20+","npm","TypeScript",false,8_000,60_000,512L*1024*1024,GrammarExecutionMode.HYBRID,
                Map.of("visitor","true","listener","true","semanticMapping","explicit","browserParser","true")));
        profiles=Map.copyOf(map);
    }

    /** Descripción: Ejecuta la operación «add» correspondiente a la responsabilidad de GrammarToolchainProfileService. Entradas: map: dato de entrada necesario para la operación; profile: archivo o contenido que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void add(Map<String,GrammarToolchainProfile> map, GrammarToolchainProfile profile){
        if(map.put(profile.id(),profile)!=null) throw new IllegalStateException("Duplicate grammar toolchain profile: "+profile.id());
    }
    /** Descripción: Obtiene la información correspondiente a «list» dentro de GrammarToolchainProfileService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<GrammarToolchainProfile> list(){return profiles.values().stream().sorted(Comparator.comparing(GrammarToolchainProfile::id)).toList();}
    /** Descripción: Ejecuta la operación «compatible» correspondiente a la responsabilidad de GrammarToolchainProfileService. Entradas: technology: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public List<GrammarToolchainProfile> compatible(GrammarTechnology technology){return list().stream().filter(profile->profile.technology()==technology).toList();}
    /** Descripción: Valida las condiciones necesarias para «require» y rechaza entradas no válidas. Entradas: id: identificador que delimita el recurso objetivo; technology: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public GrammarToolchainProfile require(String id,GrammarTechnology technology){
        String requested=id==null?"":id.trim();
        GrammarToolchainProfile profile=requested.isBlank()?defaultProfile(technology):profiles.get(requested);
        if(profile==null) throw new IllegalArgumentException("Unknown grammar toolchain profile: "+requested);
        if(profile.technology()!=technology) throw new IllegalArgumentException("Toolchain profile "+profile.id()+" is not compatible with "+technology);
        return profile;
    }
    /** Descripción: Obtiene la información correspondiente a «find» dentro de GrammarToolchainProfileService. Entradas: id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<GrammarToolchainProfile> find(String id){return Optional.ofNullable(profiles.get(id));}
    /** Descripción: Ejecuta la operación «defaultProfile» correspondiente a la responsabilidad de GrammarToolchainProfileService. Entradas: technology: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private GrammarToolchainProfile defaultProfile(GrammarTechnology technology){
        String preferred=switch(technology){
            case LANGIUM -> "langium-4.4-browser";
            case XTEXT -> "xtext-2.44-lsp";
            case ANTLR -> "antlr-4.13.2-java";
        };
        return profiles.get(preferred);
    }
}
