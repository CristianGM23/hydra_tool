package com.example.editor.model;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;

import com.example.editor.emf.LoadedMetamodel;

/** Estado mutable de una única sesión de modelo. El acceso se serializa por sesión. */
final class ModelSession {
    final String id;
    final String metamodelId;
    final String fileName;
    final LoadedMetamodel loadedMetamodel;
    EObject rootObject;
    final Map<String, EObject> objects = new LinkedHashMap<>();
    final Map<EObject, String> objectIds = new IdentityHashMap<>();
    long nextElementNumber = 1;
    boolean dirty = true;

    /** Descripción: Inicializa una instancia de ModelSession con sus dependencias y datos de entrada. Entradas: id: identificador que delimita el recurso objetivo; metamodelId: identificador que delimita el recurso objetivo; fileName: archivo o contenido que debe procesarse; loadedMetamodel: dato de entrada necesario para la operación; rootObject: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ ModelSession(String id, String metamodelId, String fileName, LoadedMetamodel loadedMetamodel, EObject rootObject) {
        this.id = id;
        this.metamodelId = metamodelId;
        this.fileName = fileName;
        this.loadedMetamodel = loadedMetamodel;
        this.rootObject = rootObject;
    }
}
