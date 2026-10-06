package com.example.editor.integration.roundtrip;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Conserva regiones manuales explícitas durante la regeneración de archivos de texto. */
@Component
public class ManualRegionPreserver {
    private static final Pattern REGION = Pattern.compile("(?s)(?<open>(?://|#|<!--)\\s*<hydra:manual\\s+id=\\\"(?<id>[A-Za-z0-9._:-]+)\\\"[^>]*>(?:\\s*-->)?)(?<body>.*?)(?<close>(?://|#|<!--)\\s*</hydra:manual>(?:\\s*-->)?)");

    /** Descripción: Actualiza el estado gestionado por ManualRegionPreserver mediante la operación «merge». Entradas: existing: dato de entrada necesario para la operación; generated: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public byte[] merge(byte[] existing, byte[] generated){
        String oldText = new String(existing == null ? new byte[0] : existing, StandardCharsets.UTF_8);
        String newText = new String(generated == null ? new byte[0] : generated, StandardCharsets.UTF_8);
        Map<String, String> bodies = bodies(oldText);
        if(bodies.isEmpty()){return generated == null ? new byte[0] : generated.clone();}
        Matcher matcher = REGION.matcher(newText);
        StringBuffer result = new StringBuffer();
        while(matcher.find()){
            String body = bodies.get(matcher.group("id"));
            if(body == null){matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group()));}
            else{matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group("open") + body + matcher.group("close")));}
        }
        matcher.appendTail(result);
        return result.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Descripción: Ejecuta la operación «bodies» correspondiente a la responsabilidad de ManualRegionPreserver. Entradas: text: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public Map<String, String> bodies(String text){
        Map<String, String> result = new LinkedHashMap<>(); Matcher matcher = REGION.matcher(text == null ? "" : text);
        while(matcher.find()){result.put(matcher.group("id"), matcher.group("body"));}
        return Map.copyOf(result);
    }
}
