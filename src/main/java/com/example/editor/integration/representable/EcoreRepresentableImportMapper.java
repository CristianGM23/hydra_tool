package com.example.editor.integration.representable;

import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Representability catalog for Ecore structural changes. */
@Component
public class EcoreRepresentableImportMapper extends AbstractCataloguedRepresentableMapper {
    /** Descripción: Inicializa una instancia de EcoreRepresentableImportMapper con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ public EcoreRepresentableImportMapper(){
        super("ECORE", Map.ofEntries(
                Map.entry("EPACKAGE", NeutralElementKind.PACKAGE),
                Map.entry("ECLASS", NeutralElementKind.CLASS),
                Map.entry("EINTERFACE", NeutralElementKind.INTERFACE),
                Map.entry("EENUM", NeutralElementKind.ENUM),
                Map.entry("EENUMLITERAL", NeutralElementKind.ENUM_LITERAL),
                Map.entry("EDATATYPE", NeutralElementKind.DATATYPE),
                Map.entry("EATTRIBUTE", NeutralElementKind.ATTRIBUTE),
                Map.entry("EREFERENCE", NeutralElementKind.REFERENCE),
                Map.entry("EOPERATION", NeutralElementKind.OPERATION),
                Map.entry("EPARAMETER", NeutralElementKind.PARAMETER)), Set.of());
    }
}
