package com.example.editor.integration.bridge;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.editor.gamification.feedback.UserFeedbackEnvelope;
import com.example.editor.gamification.feedback.UserFeedbackSurface;

/** Adaptador best-effort del contrato neutral de feedback a la UI nativa de cada host. */
public final class HostFeedbackPresentationService {
    private static final Logger LOGGER=LoggerFactory.getLogger(HostFeedbackPresentationService.class);
    /** Descripción: Evita instancias de un adaptador puramente estático. Entradas: no requiere parámetros. Salida: no devuelve valor. */
    /** Descripción: Inicializa una instancia de HostFeedbackPresentationService con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private HostFeedbackPresentationService() { }
    /** Descripción: Adapta un sobre neutral a una notificación nativa del host sin comprometer el commit semántico si la UI falla. Entradas: bridge y feedback. Salida: true si el host aceptó la presentación. */
    /** Descripción: Ejecuta la operación «present» correspondiente a la responsabilidad de HostFeedbackPresentationService. Entradas: bridge: dato de entrada necesario para la operación; feedback: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public static boolean present(HostUiBridge bridge, UserFeedbackEnvelope feedback) {
        if(bridge==null||feedback==null||!feedback.presentation().enabled()) return false;
        if(!feedback.presentation().surfaces().contains(UserFeedbackSurface.TOAST)
                && !feedback.presentation().surfaces().contains(UserFeedbackSurface.STATUS)
                && !feedback.presentation().surfaces().contains(UserFeedbackSurface.DIALOG)) return false;
        Map<String,String> metadata=new LinkedHashMap<>(feedback.metadata());
        metadata.put("feedbackId",feedback.id());metadata.put("kind",feedback.kind().name());metadata.put("severity",feedback.severity().name());
        metadata.put("pointsDelta",String.valueOf(feedback.pointsDelta()));metadata.put("progressCurrent",String.valueOf(feedback.progressCurrent()));metadata.put("progressTarget",String.valueOf(feedback.progressTarget()));metadata.put("durationMillis",String.valueOf(feedback.presentation().durationMillis()));metadata.put("nextStep",feedback.nextStep());
        try { bridge.notify(new HostNotification(feedback.severity().name(),feedback.title(),feedback.message(),metadata)); return true; }
        catch(RuntimeException exception){ LOGGER.warn("Host feedback presentation failed; semantic runtime remains committed",exception); return false; }
    }
}
