package com.example.editor.metamodel.importing.updatesite;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/** Analizador acotado del formato JVM ClassFile. No define clases en la JVM. */
@Component
public class ClassFileStructureReader {

    /** Descripción: Obtiene la información correspondiente a «read» dentro de ConstantIndex. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ ClassFileStructure read(byte[] content) {
        try(DataInputStream input = new DataInputStream(new ByteArrayInputStream(content))) {
            if(input.readInt() != 0xCAFEBABE) {
                throw new IllegalArgumentException("Not a JVM class file");
            }
            input.readUnsignedShort();
            input.readUnsignedShort();
            int constantPoolCount = input.readUnsignedShort();
            Object[] constants = new Object[constantPoolCount];
            for(int index = 1; index < constantPoolCount; index++) {
                int tag = input.readUnsignedByte();
                switch(tag) {
                    case 1 -> {
                        int length = input.readUnsignedShort();
                        constants[index] = new String(input.readNBytes(length), StandardCharsets.UTF_8);
                    }
                    case 3, 4 -> input.skipNBytes(4);
                    case 5, 6 -> { input.skipNBytes(8); index++; }
                    case 7, 8, 16, 19, 20 -> constants[index] = new ConstantIndex(input.readUnsignedShort());
                    case 9, 10, 11, 12, 17, 18 -> input.skipNBytes(4);
                    case 15 -> input.skipNBytes(3);
                    default -> throw new IllegalArgumentException("Unsupported JVM constant-pool tag: " + tag);
                }
            }
            int access = input.readUnsignedShort();
            String className = className(constants, input.readUnsignedShort());
            String superName = className(constants, input.readUnsignedShort());
            int interfaces = input.readUnsignedShort();
            input.skipNBytes(interfaces * 2L);
            List<ClassFileStructure.ClassMember> fields = readMembers(input, constants);
            List<ClassFileStructure.ClassMember> methods = readMembers(input, constants);
            skipAttributes(input);
            List<String> utf8 = new ArrayList<>();
            for(Object constant : constants) if(constant instanceof String value) utf8.add(value);
            return new ClassFileStructure(className, superName, access, fields, methods, utf8);
        } catch(IOException exception) {
            throw new IllegalArgumentException("Could not parse JVM class file", exception);
        }
    }

    /** Descripción: Obtiene la información correspondiente a «readMembers» dentro de ConstantIndex. Entradas: input: valor de entrada que debe procesarse; constants: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<ClassFileStructure.ClassMember> readMembers(DataInputStream input, Object[] constants) throws IOException {
        int count = input.readUnsignedShort();
        List<ClassFileStructure.ClassMember> result = new ArrayList<>(count);
        for(int index = 0; index < count; index++) {
            int access = input.readUnsignedShort();
            String name = utf8(constants, input.readUnsignedShort());
            String descriptor = utf8(constants, input.readUnsignedShort());
            skipAttributes(input);
            result.add(new ClassFileStructure.ClassMember(name, descriptor, access));
        }
        return List.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «skipAttributes» correspondiente a la responsabilidad de ConstantIndex. Entradas: input: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void skipAttributes(DataInputStream input) throws IOException {
        int count = input.readUnsignedShort();
        for(int index = 0; index < count; index++) {
            input.readUnsignedShort();
            long length = Integer.toUnsignedLong(input.readInt());
            input.skipNBytes(length);
        }
    }

    /** Descripción: Ejecuta la operación «className» correspondiente a la responsabilidad de ConstantIndex. Entradas: constants: dato de entrada necesario para la operación; classIndex: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String className(Object[] constants, int classIndex) {
        if(classIndex <= 0 || classIndex >= constants.length || !(constants[classIndex] instanceof ConstantIndex reference)) {
            return "";
        }
        return utf8(constants, reference.index()).replace('/', '.');
    }

    /** Descripción: Ejecuta la operación «utf8» correspondiente a la responsabilidad de ConstantIndex. Entradas: constants: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String utf8(Object[] constants, int index) {
        return index > 0 && index < constants.length && constants[index] instanceof String value ? value : "";
    }

    private record ConstantIndex(int index) { }
}
