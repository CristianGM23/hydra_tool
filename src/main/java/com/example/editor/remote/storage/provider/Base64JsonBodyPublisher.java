package com.example.editor.remote.storage.provider;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.example.editor.remote.storage.RemoteStorageException;

/**
 * Publica JSON con un campo Base64 sin materializar una segunda copia Base64 ni
 * el documento JSON completo en heap. El byte[] de artefacto sigue siendo
 * propiedad temporal del comando de transferencia.
 */
final class Base64JsonBodyPublisher {
    private static final byte[] TABLE="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".getBytes(StandardCharsets.US_ASCII);
    /** Descripción: Inicializa una instancia de Base64JsonBodyPublisher con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private Base64JsonBodyPublisher(){}

    /** Descripción: Crea o prepara el resultado de «create» dentro de EncodingInputStream. Entradas: mapper: componente de conversión utilizado durante el procesamiento; metadata: dato de entrada necesario para la operación; contentField: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ static HttpRequest.BodyPublisher create(ObjectMapper mapper,Map<String,Object> metadata,String contentField,byte[] content) {
        byte[] prefix=prefix(mapper,metadata,contentField);byte[] suffix="\"}".getBytes(StandardCharsets.US_ASCII);byte[] source=content==null?new byte[0]:content;
        long encodedLength=4L*((source.length+2L)/3L);long contentLength=prefix.length+encodedLength+suffix.length;
        HttpRequest.BodyPublisher streaming=HttpRequest.BodyPublishers.ofInputStream(()->new SequenceInputStream(Collections.enumeration(List.of(
                new ByteArrayInputStream(prefix),new EncodingInputStream(source),new ByteArrayInputStream(suffix)))));
        return HttpRequest.BodyPublishers.fromPublisher(streaming,contentLength);
    }

    /** Descripción: Ejecuta la operación «prefix» correspondiente a la responsabilidad de EncodingInputStream. Entradas: mapper: componente de conversión utilizado durante el procesamiento; metadata: dato de entrada necesario para la operación; contentField: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static byte[] prefix(ObjectMapper mapper,Map<String,Object> metadata,String contentField) {
        try {
            byte[] json=mapper.writeValueAsBytes(metadata==null?Map.of():metadata);
            if(json.length<2||json[json.length-1]!='}') throw new RemoteStorageException(RemoteStorageException.Reason.REMOTE_ERROR,"Remote request metadata could not be serialized safely");
            byte[] field=mapper.writeValueAsBytes(contentField==null?"content":contentField);
            byte[] result=Arrays.copyOf(json,json.length-1+1+field.length+2);
            int offset=json.length-1;result[offset++]=',';System.arraycopy(field,0,result,offset,field.length);offset+=field.length;result[offset++]=':';result[offset]='\"';
            return result;
        } catch(JacksonException exception){throw new RemoteStorageException(RemoteStorageException.Reason.REMOTE_ERROR,"Remote request metadata could not be serialized",exception);}
    }

    /** Base64 incremental: usa un bloque constante de cuatro bytes. */
    private static final class EncodingInputStream extends InputStream {
        private final byte[] source;private int sourceIndex;private final byte[] quartet=new byte[4];private int quartetIndex=4;
        /** Descripción: Inicializa una instancia de EncodingInputStream con sus dependencias y datos de entrada. Entradas: source: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private EncodingInputStream(byte[] source){this.source=source;}
        /** Descripción: Obtiene la información correspondiente a «read» dentro de EncodingInputStream. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public int read(){if(quartetIndex>=4&&!fill())return -1;return quartet[quartetIndex++]&0xff;}
        /** Descripción: Obtiene la información correspondiente a «read» dentro de EncodingInputStream. Entradas: target: dato de entrada necesario para la operación; offset: dato de entrada necesario para la operación; length: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public int read(byte[] target,int offset,int length) throws IOException {
            if(target==null)throw new NullPointerException("target");if(offset<0||length<0||offset+length>target.length)throw new IndexOutOfBoundsException();if(length==0)return 0;
            int written=0;while(written<length){int value=read();if(value<0)break;target[offset+written++]=(byte)value;}return written==0?-1:written;
        }
        /** Descripción: Ejecuta la operación «fill» correspondiente a la responsabilidad de EncodingInputStream. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean fill(){if(sourceIndex>=source.length)return false;int remaining=source.length-sourceIndex;int a=source[sourceIndex++]&0xff;int b=remaining>1?source[sourceIndex++]&0xff:0;int c=remaining>2?source[sourceIndex++]&0xff:0;
            quartet[0]=TABLE[a>>>2];quartet[1]=TABLE[((a&3)<<4)|(b>>>4)];quartet[2]=remaining>1?TABLE[((b&15)<<2)|(c>>>6)]:(byte)'=';quartet[3]=remaining>2?TABLE[c&63]:(byte)'=';quartetIndex=0;return true;}
    }
}
