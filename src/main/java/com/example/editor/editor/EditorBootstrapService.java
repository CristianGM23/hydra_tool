package com.example.editor.editor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.editor.editor.EditorBootstrapResponse.PaletteEntry;
import com.example.editor.metamodel.MetamodelDescriptor.ClassDescriptor;
import com.example.editor.metamodel.MetamodelDescriptor.PackageDescriptor;
import com.example.editor.metamodel.registry.MetamodelRegistryService;
import com.example.editor.metamodel.registry.RegisteredMetamodel;

/**
 * Prepara la información que necesita el frontend para abrir un editor.
 *
 * La paleta contiene únicamente clases concretas que pueden instanciarse.
 */
@Service
public class EditorBootstrapService {

    private final MetamodelRegistryService registryService;

    /**
     * Descripción:
     * Crea el servicio utilizando el registro de metamodelos.
     *
     * Entrada:
     * @param registryService administra los metamodelos registrados.
     *
     * Salida:
     * Crea una instancia de EditorBootstrapService.
     */
    /** Descripción: Inicializa una instancia de EditorBootstrapService con sus dependencias y datos de entrada. Entradas: registryService: servicio colaborador utilizado por la operación. Salida: deja la instancia inicializada y lista para su uso. */ public EditorBootstrapService(MetamodelRegistryService registryService) {
        this.registryService = registryService;
    }

    /**
     * Descripción:
     * Construye la información inicial del editor para un metamodelo.
     *
     * Entrada:
     * @param metamodelId identifica el metamodelo solicitado.
     *
     * Salida:
     * @return la descripción, el archivo y la paleta del editor.
     */
    /** Descripción: Crea o prepara el resultado de «create» dentro de EditorBootstrapService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public EditorBootstrapResponse create(String metamodelId) {
        RegisteredMetamodel registeredMetamodel = registryService.find(metamodelId);
        List<PaletteEntry> palette = new ArrayList<>();

        for (PackageDescriptor packageDescriptor : registeredMetamodel.descriptor().packages()) {
            addPackageEntries(packageDescriptor, palette);
        }

        palette.sort(Comparator.comparing(PaletteEntry::className).thenComparing(PaletteEntry::classId));
        return new EditorBootstrapResponse(registeredMetamodel.id(), registeredMetamodel.fileName(), List.copyOf(palette), registeredMetamodel.descriptor());
    }

    /**
     * Descripción:
     * Añade recursivamente las clases instanciables de un paquete.
     *
     * Entrada:
     * @param packageDescriptor contiene las clases y los subpaquetes.
     * @param palette recibe los elementos encontrados.
     *
     * Salida:
     * No devuelve ningún valor.
     */
    /** Descripción: Ejecuta la operación «addPackageEntries» correspondiente a la responsabilidad de EditorBootstrapService. Entradas: packageDescriptor: dato de entrada necesario para la operación; palette: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void addPackageEntries(PackageDescriptor packageDescriptor, List<PaletteEntry> palette) {
        for (ClassDescriptor classDescriptor : packageDescriptor.classes()) {
            if (classDescriptor.instantiable()) {
                palette.add(new PaletteEntry(classDescriptor.id(), packageDescriptor.name(), classDescriptor.name()));
            }
        }

        for (PackageDescriptor subpackage : packageDescriptor.subpackages()) {
            addPackageEntries(subpackage, palette);
        }
    }
}