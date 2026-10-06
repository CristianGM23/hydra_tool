package com.example.editor.standalone.publication;

import java.util.ArrayList;
import java.util.List;

/** Small dependency-free semantic version comparator used by update resolution. */
final class HydraSemanticVersion implements Comparable<HydraSemanticVersion> {
    private final List<Integer> numbers;
    private final String suffix;

    /** Descripción: Inicializa una instancia de HydraSemanticVersion con sus dependencias y datos de entrada. Entradas: numbers: dato de entrada necesario para la operación; suffix: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private HydraSemanticVersion(List<Integer> numbers, String suffix){
        this.numbers = numbers; this.suffix = suffix;
    }

    /** Descripción: Ejecuta la operación «parse» correspondiente a la responsabilidad de HydraSemanticVersion. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ static HydraSemanticVersion parse(String value){
        String token = value == null ? "" : value.trim();
        String[] split = token.split("-", 2);
        String[] parts = split[0].split("\\.");
        List<Integer> values = new ArrayList<>();
        for(String part : parts){
            try{values.add(Integer.parseInt(part.replaceAll("[^0-9]", "")));}
            catch(NumberFormatException exception){values.add(0);}
        }
        while(values.size() < 3){values.add(0);}
        return new HydraSemanticVersion(List.copyOf(values), split.length > 1 ? split[1] : "");
    }

    /** Descripción: Ejecuta la operación «compareTo» correspondiente a la responsabilidad de HydraSemanticVersion. Entradas: other: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public int compareTo(HydraSemanticVersion other){
        int maximum = Math.max(numbers.size(), other.numbers.size());
        for(int index = 0; index < maximum; index++){
            int left = index < numbers.size() ? numbers.get(index) : 0;
            int right = index < other.numbers.size() ? other.numbers.get(index) : 0;
            int result = Integer.compare(left, right);
            if(result != 0){return result;}
        }
        if(suffix.isBlank() && !other.suffix.isBlank()){return 1;}
        if(!suffix.isBlank() && other.suffix.isBlank()){return -1;}
        return suffix.compareTo(other.suffix);
    }
}
