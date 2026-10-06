package com.example.editor.contracts.metamodel;
import com.example.editor.metamodel.core.MetaModel;
/** Puerto interno mínimo para consultar contextos de metamodelo sin depender de la capa application concreta. */
public interface MetamodelQueryPort {
    /** Descripción: Resuelve un metamodelo o contexto semántico por identificador. Entradas: id: identificador. Salida: metamodelo neutral. */ MetaModel requireMetamodel(String id);
}
