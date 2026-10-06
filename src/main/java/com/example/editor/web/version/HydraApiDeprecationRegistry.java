package com.example.editor.web.version;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;
/** Registro central y explícito de endpoints HTTP deprecados. */
@Component
public class HydraApiDeprecationRegistry {
    private final List<Entry> entries=List.of();
    /** Descripción: Busca la deprecación aplicable a una ruta exacta. Entradas: path: ruta. Salida: entrada o null. */ public Entry find(String path){return entries.stream().filter(e->e.path().equals(path)).findFirst().orElse(null);}
    /** Descripción: Lista deprecaciones declaradas para clientes generados. Entradas: ninguna. Salida: entradas estables. */ public List<Entry> entries(){return entries;}
    public record Entry(String path,String replacement,LocalDate sunset,String reason){}
}
