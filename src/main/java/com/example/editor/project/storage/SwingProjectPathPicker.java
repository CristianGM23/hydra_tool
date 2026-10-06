package com.example.editor.project.storage;

import java.awt.EventQueue;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import com.example.editor.project.LocalProjectProperties;
import com.example.editor.project.ProjectPathPicker;
import com.example.editor.project.ProjectPathPickerMode;
import com.example.editor.project.ProjectPathPickerRequest;
import com.example.editor.project.ProjectPathPickerResult;

/** Selector Swing opcional para instalaciones locales de escritorio. */
public class SwingProjectPathPicker implements ProjectPathPicker {
    private final LocalProjectProperties properties;

    /** Descripción: Inicializa una instancia de SwingProjectPathPicker con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public SwingProjectPathPicker(LocalProjectProperties properties) {
        this.properties = properties;
    }

    @Override
    /** Descripción: Ejecuta la operación «pick» correspondiente a la responsabilidad de SwingProjectPathPicker. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ProjectPathPickerResult pick(ProjectPathPickerRequest request) {
        if(!properties.isNativePathPickerEnabled()){
            return ProjectPathPickerResult.unavailable(request.mode(),"The native path picker is disabled. Enter the path manually.");
        }
        if(GraphicsEnvironment.isHeadless()){
            return ProjectPathPickerResult.unavailable(request.mode(),"The native path picker is not available in a headless environment. Enter the path manually.");
        }
        AtomicReference<ProjectPathPickerResult> result = new AtomicReference<>();
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        Runnable selection = () -> {
            try {
                result.set(openChooser(request));
            } catch(RuntimeException exception){
                failure.set(exception);
            }
        };
        try {
            if(EventQueue.isDispatchThread()){
                selection.run();
            } else {
                EventQueue.invokeAndWait(selection);
            }
        } catch(InterruptedException exception){
            Thread.currentThread().interrupt();
            return ProjectPathPickerResult.unavailable(request.mode(),"The native path selection was interrupted.");
        } catch(Exception exception){
            return ProjectPathPickerResult.unavailable(request.mode(),"The native path picker could not be opened: " + exception.getMessage());
        }
        if(failure.get() != null){
            return ProjectPathPickerResult.unavailable(request.mode(),"The native path picker could not be opened: " + failure.get().getMessage());
        }
        return result.get();
    }

    /** Descripción: Crea o prepara el resultado de «openChooser» dentro de SwingProjectPathPicker. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ProjectPathPickerResult openChooser(ProjectPathPickerRequest request) {
        JFileChooser chooser = new JFileChooser(initialLocation(request.initialDirectory()));
        chooser.setDialogTitle(request.title().isBlank() ? defaultTitle(request.mode()) : request.title());
        chooser.setAcceptAllFileFilterUsed(request.mode() == ProjectPathPickerMode.METAMODEL_FILE);
        configureMode(chooser,request.mode());
        int selected;
        JDialog owner = new JDialog();
        owner.setUndecorated(true);
        owner.setAlwaysOnTop(true);
        owner.setModal(false);
        owner.setSize(1,1);
        owner.setLocationRelativeTo(null);
        try {
            owner.setVisible(true);
            owner.toFront();
            chooser.requestFocusInWindow();
            selected = chooser.showOpenDialog(owner);
        } finally {
            owner.dispose();
        }
        if(selected != JFileChooser.APPROVE_OPTION){
            return ProjectPathPickerResult.cancelled(request.mode());
        }
        File file = chooser.getSelectedFile();
        Path path = file.toPath().toAbsolutePath().normalize();
        Path directory = Files.isDirectory(path) ? path : path.getParent();
        return new ProjectPathPickerResult(true,false,path.toString(),directory == null ? "" : directory.toString(),file.getName(),request.mode(),"");
    }

    /** Descripción: Ejecuta la operación «configureMode» correspondiente a la responsabilidad de SwingProjectPathPicker. Entradas: chooser: dato de entrada necesario para la operación; mode: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void configureMode(JFileChooser chooser,ProjectPathPickerMode mode) {
        if(mode == ProjectPathPickerMode.DIRECTORY){
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            return;
        }
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if(mode == ProjectPathPickerMode.ZIP_ARCHIVE){
            chooser.setFileFilter(new FileNameExtensionFilter("Project ZIP archives (*.zip)","zip"));
        } else if(mode == ProjectPathPickerMode.PROJECT_DESCRIPTOR){
            chooser.setFileFilter(projectDescriptorFilter());
        } else if(mode == ProjectPathPickerMode.METAMODEL_FILE){
            chooser.setFileFilter(new FileNameExtensionFilter("Supported metamodel files","ecore","xmi","xml","json","xtext","langium","g4","puml","plantuml","drawio","md"));
        }
    }

    /** Descripción: Ejecuta la operación «projectDescriptorFilter» correspondiente a la responsabilidad de SwingProjectPathPicker. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private FileFilter projectDescriptorFilter() {
        return new FileFilter() {
            @Override
            /** Descripción: Ejecuta la operación «accept» correspondiente a la responsabilidad de SwingProjectPathPicker. Entradas: file: archivo o contenido que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean accept(File file) {
                return file.isDirectory() || "modeling-project.json".equalsIgnoreCase(file.getName());
            }

            @Override
            /** Descripción: Obtiene la información correspondiente a «getDescription» dentro de SwingProjectPathPicker. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getDescription() {
                return "Modeling project descriptor (modeling-project.json)";
            }
        };
    }

    /** Descripción: Crea o prepara el resultado de «initialLocation» dentro de SwingProjectPathPicker. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private File initialLocation(String value) {
        if(value == null || value.isBlank()){
            return properties.getRegistryDirectory().toAbsolutePath().normalize().toFile();
        }
        Path candidate = Path.of(value).toAbsolutePath().normalize();
        if(Files.isDirectory(candidate)){
            return candidate.toFile();
        }
        Path parent = candidate.getParent();
        return parent == null ? properties.getRegistryDirectory().toAbsolutePath().normalize().toFile() : parent.toFile();
    }

    /** Descripción: Ejecuta la operación «defaultTitle» correspondiente a la responsabilidad de SwingProjectPathPicker. Entradas: mode: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String defaultTitle(ProjectPathPickerMode mode) {
        return switch(mode) {
            case DIRECTORY -> "Select project directory";
            case PROJECT_DESCRIPTOR -> "Select modeling-project.json";
            case ZIP_ARCHIVE -> "Select project ZIP";
            case METAMODEL_FILE -> "Select metamodel file";
        };
    }
}
