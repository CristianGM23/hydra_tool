package com.example.editor.metamodel.importing.updatesite;

import java.util.List;

/** Estructura mínima de un .class sin ejecutar bytecode ni cargar el bundle. */
record ClassFileStructure(String className, String superClassName, int accessFlags,
        List<ClassMember> fields, List<ClassMember> methods, List<String> utf8Constants) {
    ClassFileStructure {
        className = className == null ? "" : className;
        superClassName = superClassName == null ? "" : superClassName;
        fields = fields == null ? List.of() : List.copyOf(fields);
        methods = methods == null ? List.of() : List.copyOf(methods);
        utf8Constants = utf8Constants == null ? List.of() : List.copyOf(utf8Constants);
    }

    record ClassMember(String name, String descriptor, int accessFlags) {
        ClassMember {
            name = name == null ? "" : name;
            descriptor = descriptor == null ? "" : descriptor;
        }
    }
}
