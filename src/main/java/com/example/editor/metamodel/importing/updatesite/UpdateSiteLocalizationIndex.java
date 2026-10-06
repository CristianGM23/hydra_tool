package com.example.editor.metamodel.importing.updatesite;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Resolves Eclipse %localization keys from nearby .properties resources without executing bundles. */
@Component
public class UpdateSiteLocalizationIndex {
    /** Descripción: Crea o prepara el resultado de «build» dentro de Index. Entradas: repository: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ Index build(UpdateSiteRepositoryContent repository) {
        List<Resource> resources=new ArrayList<>();
        for(UpdateSiteRepositoryArtifact artifact:repository.artifacts().stream().filter(item->UpdateSiteArtifactKinds.fromPath(item.path())==UpdateSiteArtifactKind.PROPERTIES).sorted(Comparator.comparing(UpdateSiteRepositoryArtifact::path)).toList()){
            byte[] bytes=repository.find(artifact.path()).orElse(new byte[0]);resources.add(new Resource(scope(artifact.path()),properties(bytes)));
        }
        return new Index(List.copyOf(resources));
    }

    /** Descripción: Ejecuta la operación «properties» correspondiente a la responsabilidad de Index. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String,String> properties(byte[] content){Map<String,String> values=new LinkedHashMap<>();String text=new String(content,StandardCharsets.ISO_8859_1);for(String raw:text.split("\\R")){String line=raw.trim();if(line.isEmpty()||line.startsWith("#")||line.startsWith("!"))continue;int split=separator(line);if(split<1)continue;String key=line.substring(0,split).trim();String value=line.substring(split+1).trim();if(!key.isBlank())values.putIfAbsent(key,unescape(value));}return Map.copyOf(values);}
    /** Descripción: Ejecuta la operación «separator» correspondiente a la responsabilidad de Index. Entradas: line: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int separator(String line){int equal=line.indexOf('=');int colon=line.indexOf(':');if(equal<0)return colon;if(colon<0)return equal;return Math.min(equal,colon);}
    /** Descripción: Ejecuta la operación «scope» correspondiente a la responsabilidad de Index. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String scope(String path){String value=path.replace('\\','/');int nested=value.indexOf("!/");if(nested>=0)return value.substring(0,nested+2);int slash=value.lastIndexOf('/');return slash<0?"":value.substring(0,slash+1);}
    /** Descripción: Ejecuta la operación «unescape» correspondiente a la responsabilidad de Index. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String unescape(String value){return value.replace("\\n"," ").replace("\\t"," ").replace("\\:",":").replace("\\=","=");}

    record Resource(String scope,Map<String,String> values){}
    record Index(List<Resource> resources){
        /** Descripción: Obtiene la información correspondiente a «resolve» dentro de Index. Entradas: sourcePath: ruta o ubicación utilizada por la operación; value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ String resolve(String sourcePath,String value){if(value==null||!value.startsWith("%")||value.length()<2)return value==null?"":value;String key=value.substring(1);return resources.stream().filter(resource->sourcePath.startsWith(resource.scope())).sorted(Comparator.comparingInt((Resource resource)->resource.scope().length()).reversed()).map(resource->resource.values().get(key)).filter(java.util.Objects::nonNull).findFirst().orElse(value);}
    }
}
