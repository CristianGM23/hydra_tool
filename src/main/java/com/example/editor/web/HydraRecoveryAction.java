package com.example.editor.web;

/** Acción de recuperación declarativa asociada a un error HTTP de HYDRA. */
public record HydraRecoveryAction(String id,String label,String kind,boolean safe) {
    /** Descripción: Normaliza los datos de una acción de recuperación para que el cliente nunca reciba valores nulos. Entradas: id: identificador estable; label: texto orientado al usuario; kind: clase de acción; safe: indica si puede ofrecerse como acción directa sin mutación destructiva. Salida: deja el record normalizado. */
    public HydraRecoveryAction {
        id=id==null?"":id.trim(); label=label==null?"":label.trim(); kind=kind==null?"GUIDANCE":kind.trim();
    }
}
