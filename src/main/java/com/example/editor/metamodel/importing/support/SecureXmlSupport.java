package com.example.editor.metamodel.importing.support;

import java.io.ByteArrayInputStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;

import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;

/**
 * Crea analizadores XML sin acceso a recursos externos.
 */
public final class SecureXmlSupport {

    /** Descripción: Inicializa una instancia de SecureXmlSupport con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private SecureXmlSupport() {
    }

    /** Descripción: Ejecuta la operación «parse» correspondiente a la responsabilidad de SecureXmlSupport. Entradas: content: valor de entrada que debe procesarse; description: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static Document parse(byte[] content, String description) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(content));
        } catch(Exception exception) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Could not parse " + description, exception);
        }
    }
}
