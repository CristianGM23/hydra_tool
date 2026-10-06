package com.example.editor.project.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** Valida imágenes del proyecto antes de permitir su renderizado inline en el navegador. */
@Component
public class ProjectVisualAssetPolicy {
    /** Descripción: Valida una imagen del proyecto antes de servirla en línea. Entradas: path: ruta portable del recurso; content: contenido binario de la imagen. Salida: devuelve el tipo multimedia validado y el contenido seguro. */
    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: path: ruta o ubicación utilizada por la operación; content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public ProjectVisualAsset validate(String path, byte[] content) {
        if(content == null || content.length == 0) throw new IllegalArgumentException("Project image is empty");
        String extension = extension(path);
        return switch(extension) {
            case "svg" -> { validateSvg(content); yield new ProjectVisualAsset("image/svg+xml", content); }
            case "png" -> requireSignature(content, new byte[]{(byte)0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A}, "PNG", "image/png");
            case "jpg", "jpeg" -> requireJpeg(content);
            case "gif" -> requireGif(content);
            case "webp" -> requireWebp(content);
            default -> throw new IllegalArgumentException("Only SVG, PNG, JPEG, GIF and WebP project images can be rendered inline");
        };
    }
    /** Descripción: Comprueba una firma binaria conocida. Entradas: contenido, firma, nombre y tipo multimedia. Salida: devuelve el recurso validado o rechaza contenido incoherente. */
    /** Descripción: Valida las condiciones necesarias para «requireSignature» y rechaza entradas no válidas. Entradas: content: valor de entrada que debe procesarse; signature: dato de entrada necesario para la operación; name: dato de entrada necesario para la operación; mediaType: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ProjectVisualAsset requireSignature(byte[] content, byte[] signature, String name, String mediaType) {
        if(content.length < signature.length) throw new IllegalArgumentException(name + " image signature is invalid");
        for(int i=0;i<signature.length;i++) if(content[i] != signature[i]) throw new IllegalArgumentException(name + " image signature is invalid");
        return new ProjectVisualAsset(mediaType, content);
    }
    /** Descripción: Comprueba la firma JPEG. Entradas: contenido binario. Salida: devuelve una imagen JPEG validada. */
    /** Descripción: Valida las condiciones necesarias para «requireJpeg» y rechaza entradas no válidas. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private ProjectVisualAsset requireJpeg(byte[] content) {
        if(content.length < 4 || (content[0]&0xff)!=0xff || (content[1]&0xff)!=0xd8 || (content[2]&0xff)!=0xff) throw new IllegalArgumentException("JPEG image signature is invalid");
        return new ProjectVisualAsset("image/jpeg", content);
    }
    /** Descripción: Comprueba la firma GIF. Entradas: contenido binario. Salida: devuelve una imagen GIF validada. */
    /** Descripción: Valida las condiciones necesarias para «requireGif» y rechaza entradas no válidas. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private ProjectVisualAsset requireGif(byte[] content) {
        if(content.length < 6) throw new IllegalArgumentException("GIF image signature is invalid");
        String signature = new String(content,0,6,StandardCharsets.US_ASCII);
        if(!"GIF87a".equals(signature) && !"GIF89a".equals(signature)) throw new IllegalArgumentException("GIF image signature is invalid");
        return new ProjectVisualAsset("image/gif", content);
    }
    /** Descripción: Comprueba la firma RIFF/WebP. Entradas: contenido binario. Salida: devuelve una imagen WebP validada. */
    /** Descripción: Valida las condiciones necesarias para «requireWebp» y rechaza entradas no válidas. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private ProjectVisualAsset requireWebp(byte[] content) {
        if(content.length < 12 || !ascii(content,0,4).equals("RIFF") || !ascii(content,8,4).equals("WEBP")) throw new IllegalArgumentException("WebP image signature is invalid");
        return new ProjectVisualAsset("image/webp", content);
    }
    /** Descripción: Analiza un SVG de forma segura y rechaza contenido activo o externo. Entradas: contenido XML. Salida: finaliza sin error únicamente si el SVG es declarativo y seguro. */
    /** Descripción: Valida las condiciones necesarias para «validateSvg» y rechaza entradas no válidas. Entradas: content: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateSvg(byte[] content) {
        try {
            DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true); factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            Element root=factory.newDocumentBuilder().parse(new ByteArrayInputStream(content)).getDocumentElement();
            if(root==null || !"svg".equalsIgnoreCase(root.getLocalName()==null?root.getNodeName():root.getLocalName())) throw new IllegalArgumentException("SVG root element is required");
            inspect(root);
        } catch(IllegalArgumentException exception) { throw exception; }
        catch(ParserConfigurationException | SAXException | IOException exception) { throw new IllegalArgumentException("SVG image is invalid or contains unsupported active content", exception); }
    }
    /** Descripción: Recorre un elemento SVG y valida sus nodos y atributos. Entradas: elemento XML. Salida: rechaza scripts, eventos y referencias externas. */
    /** Descripción: Ejecuta la operación «inspect» correspondiente a la responsabilidad de ProjectVisualAsset. Entradas: element: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void inspect(Element element) {
        String name=(element.getLocalName()==null?element.getNodeName():element.getLocalName()).toLowerCase(Locale.ROOT);
        if(name.equals("script") || name.equals("foreignobject") || name.equals("iframe") || name.equals("object") || name.equals("embed") || name.equals("style")) throw new IllegalArgumentException("SVG active content is not allowed: " + name);
        NamedNodeMap attrs=element.getAttributes();
        for(int i=0;i<attrs.getLength();i++) {
            Node attr=attrs.item(i); String attrName=attr.getNodeName().toLowerCase(Locale.ROOT); String value=attr.getNodeValue()==null?"":attr.getNodeValue().trim().toLowerCase(Locale.ROOT);
            if(attrName.startsWith("on")) throw new IllegalArgumentException("SVG event handlers are not allowed");
            if(attrName.equals("href") || attrName.endsWith(":href")) if(!value.isEmpty() && !value.startsWith("#")) throw new IllegalArgumentException("SVG external references are not allowed");
            if(attrName.equals("style") && (value.contains("url(") || value.contains("javascript:") || value.contains("expression("))) throw new IllegalArgumentException("SVG style contains an external or executable reference");
        }
        NodeList children=element.getChildNodes(); for(int i=0;i<children.getLength();i++) if(children.item(i) instanceof Element child) inspect(child);
    }
    /** Descripción: Obtiene la extensión normalizada de una ruta. Entradas: ruta del recurso. Salida: extensión en minúsculas o cadena vacía. */
    /** Descripción: Ejecuta la operación «extension» correspondiente a la responsabilidad de ProjectVisualAsset. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String extension(String path) { String value=path==null?"":path.trim().toLowerCase(Locale.ROOT); int slash=Math.max(value.lastIndexOf('/'),value.lastIndexOf('\\')); int dot=value.lastIndexOf('.'); if(dot<0||dot<slash)return ""; return value.substring(dot+1); }
    /** Descripción: Lee una sección ASCII de una firma binaria. Entradas: contenido, desplazamiento y longitud. Salida: texto ASCII correspondiente. */
    /** Descripción: Ejecuta la operación «ascii» correspondiente a la responsabilidad de ProjectVisualAsset. Entradas: content: valor de entrada que debe procesarse; offset: dato de entrada necesario para la operación; length: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String ascii(byte[] content,int offset,int length) { return new String(content,offset,length,StandardCharsets.US_ASCII); }
    public record ProjectVisualAsset(String mediaType, byte[] content) { }
}
