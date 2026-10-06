package com.example.editor.gamification.runtime;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.example.editor.concurrent.ResourceStripedLock;
import com.example.editor.event.CanonicalEvent;
import com.example.editor.event.bus.CanonicalEventBus;
import com.example.editor.event.bus.EventSubscription;
import com.example.editor.gamification.AchievementDefinition;
import com.example.editor.gamification.ChallengeDefinition;
import com.example.editor.gamification.GamificationActorState;
import com.example.editor.gamification.GamificationApplicationException;
import com.example.editor.gamification.GamificationApplicationService;
import com.example.editor.gamification.GamificationBindingModel;
import com.example.editor.gamification.GamificationDefinitionModel;
import com.example.editor.gamification.GamificationLedger;
import com.example.editor.gamification.GamificationLedgerEntry;
import com.example.editor.gamification.GamificationLedgerStatus;
import com.example.editor.gamification.GamificationLoadResult;
import com.example.editor.gamification.GamificationProperties;
import com.example.editor.gamification.GamificationRepeatPolicy;
import com.example.editor.gamification.GamificationRule;
import com.example.editor.gamification.GamificationRuntimeState;
import com.example.editor.gamification.LevelDefinition;
import com.example.editor.gamification.persistence.GamificationRuntimeRepository;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.semantic.expression.ExpressionEvaluator;
import com.example.editor.gamification.feedback.GamificationFeedbackProjector;
import com.example.editor.gamification.feedback.UserFeedbackEnvelope;
import com.example.editor.validation.persistence.StoredCanonicalDocument;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/** Motor headless e idempotente de gamificación basado únicamente en eventos comprobados. */
@Service
public class GamificationRuntimeService {
    private static final Logger LOGGER = LoggerFactory.getLogger(GamificationRuntimeService.class);
    private final GamificationApplicationService applicationService;
    private final GamificationRuntimeRepository repository;
    private final GamificationProperties properties;
    private final CanonicalEventBus eventBus;
    private final ResourceStripedLock locks = new ResourceStripedLock(256);
    private final FeedbackProjectionCache feedbackCache;
    private final CanonicalMetamodelApplicationService metamodels;
    private final ExpressionEvaluator expressionEvaluator;
    private EventSubscription subscription;

    /** Descripción: Inicializa un runtime compatible para pruebas embebidas sin infraestructura semántica completa. Entradas: applicationService, repository, properties y eventBus. Salida: deja el runtime preparado con fallback histórico. */ public GamificationRuntimeService(GamificationApplicationService applicationService,
            GamificationRuntimeRepository repository, GamificationProperties properties, CanonicalEventBus eventBus) {
        this(applicationService, repository, properties, eventBus, null, null);
    }

    @Autowired
    /** Descripción: Inicializa el runtime completo con evaluación neutral de condiciones. Entradas: servicios de gamificación, metamodelos y expressionEvaluator. Salida: deja el runtime listo para procesar eventos. */ public GamificationRuntimeService(GamificationApplicationService applicationService,
            GamificationRuntimeRepository repository, GamificationProperties properties, CanonicalEventBus eventBus,
            CanonicalMetamodelApplicationService metamodels, ExpressionEvaluator expressionEvaluator) {
        this.applicationService = applicationService;
        this.repository = repository;
        this.properties = properties;
        this.eventBus = eventBus;
        this.metamodels = metamodels;
        this.expressionEvaluator = expressionEvaluator;
        this.feedbackCache = new FeedbackProjectionCache(properties.getMaximumFeedbackCacheEntries());
    }

    @PostConstruct
    /** Descripción: Ejecuta la operación «subscribe» correspondiente a la responsabilidad de RuntimeDocuments. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void subscribe() {
        subscription = eventBus.subscribe(event -> properties.isEnabled() && event != null
                && !"false".equalsIgnoreCase(event.metadata().getOrDefault("hydra.gamificationEnabled","true"))
                && !event.context().metamodelId().isBlank(), event -> {
            try { process(event.context().metamodelId(), event); }
            catch(GamificationApplicationException exception) {
                if(exception.getReason() != GamificationApplicationException.Reason.NOT_FOUND) LOGGER.warn("Gamification event processing failed", exception);
            } catch(RuntimeException exception) { LOGGER.warn("Gamification event processing failed", exception); }
        });
    }

    @PreDestroy
    /** Descripción: Elimina o libera el recurso gestionado por «close» de forma controlada. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void close() { if(subscription != null) subscription.close(); }

    /** Descripción: Procesa la operación «process» coordinando las responsabilidades de RuntimeDocuments. Entradas: metamodelId: identificador que delimita el recurso objetivo; event: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public GamificationProcessingResult process(String metamodelId, CanonicalEvent event) {
        if(event == null) throw new GamificationApplicationException(GamificationApplicationException.Reason.INVALID_REQUEST, "CanonicalEvent is required");
        if(depth(event) >= 16) return new GamificationProcessingResult(metamodelId, event.id(), state(metamodelId).state(), List.of(), List.of(), List.of(), List.of());
        synchronized(locks.lockFor(metamodelId)) {
            GamificationLoadResult configuration = applicationService.find(metamodelId);
            RuntimeDocuments current = loadRuntime(metamodelId);
            if(current.ledger.entries().stream().anyMatch(entry -> entry.sourceEventId().equals(event.id())))
                return new GamificationProcessingResult(metamodelId, event.id(), current.state, List.of(), List.of(), List.of(), List.of());

            List<GamificationLedgerEntry> entries = new ArrayList<>(current.ledger.entries());
            List<GamificationLedgerEntry> granted = new ArrayList<>();
            List<GamificationLedgerEntry> rejected = new ArrayList<>();
            List<GamificationLedgerEntry> compensated = compensate(event, entries);
            if(compensated.isEmpty()) {
                for(GamificationRule rule : configuration.bindings().rules()) {
                    if(!rule.enabled() || !matches(rule, event, metamodelId)) continue;
                    String actorId = actorId(event);
                    String awardKey = event.id() + "|" + rule.id();
                    if(current.state.processedAwardKeys().contains(awardKey) || entries.stream().anyMatch(entry -> entry.sourceEventId().equals(event.id()) && entry.ruleId().equals(rule.id()))) continue;
                    String rejection = rejection(rule, actorId, event.occurredAt(), entries);
                    long appliedPoints = rejection.isBlank() ? appliedPoints(rule, actorId, configuration.definitions(), current.state) : 0L;
                    if(rejection.isBlank() && rule.points() < 0 && appliedPoints == 0L) rejection = "Minimum point balance reached";
                    GamificationLedgerEntry entry = entry(metamodelId, actorId, event, rule,
                            rejection.isBlank() ? GamificationLedgerStatus.ACTIVE : GamificationLedgerStatus.REJECTED,
                            rejection.isBlank() ? appliedPoints : 0L, "", rejection);
                    entries.add(entry);
                    if(rejection.isBlank()) granted.add(entry); else rejected.add(entry);
                }
            }
            GamificationLedger updatedLedger = new GamificationLedger("1.0", metamodelId, metamodelId, Instant.now(), trim(entries));
            GamificationRuntimeState updatedState = rebuildState(metamodelId, configuration.definitions(), updatedLedger);
            persist(current, updatedState, updatedLedger);
            feedbackCache.invalidate(metamodelId);
            List<UserFeedbackEnvelope> feedback = GamificationFeedbackProjector.project(configuration.definitions(), configuration.bindings(), updatedLedger, actorId(event), 250).stream()
                    .filter(item -> item.sourceEventId().equals(event.id())).toList();
            publish(event, granted, rejected, compensated);
            return new GamificationProcessingResult(metamodelId, event.id(), updatedState, granted, rejected, compensated, feedback);
        }
    }

    /** Descripción: Ejecuta la operación «state» correspondiente a la responsabilidad de RuntimeDocuments. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public GamificationRuntimeSnapshot state(String metamodelId) {
        RuntimeDocuments documents = loadRuntime(metamodelId);
        return new GamificationRuntimeSnapshot(documents.state, documents.stateEtag, documents.ledger, documents.ledgerEtag);
    }

    /** Descripción: Reconstruye retroalimentación durable desde el ledger canónico y reutiliza una caché acotada por ETag. Entradas: metamodelId, actorId y limit. Salida: feed portable filtrado y limitado. */
    /** Descripción: Ejecuta la operación «feedback» correspondiente a la responsabilidad de RuntimeDocuments. Entradas: metamodelId: identificador que delimita el recurso objetivo; actorId: identificador que delimita el recurso objetivo; limit: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public List<UserFeedbackEnvelope> feedback(String metamodelId, String actorId, int limit) {
        GamificationLoadResult configuration = applicationService.find(metamodelId);
        RuntimeDocuments documents = loadRuntime(metamodelId);
        int boundedLimit = Math.max(1, Math.min(limit <= 0 ? 100 : limit, 1000));
        FeedbackProjectionKey key = new FeedbackProjectionKey(metamodelId, actorId, boundedLimit, configuration.definitionEtag(), configuration.bindingEtag(), documents.ledgerEtag);
        return feedbackCache.get(key).orElseGet(() -> {
            List<UserFeedbackEnvelope> projected = GamificationFeedbackProjector.project(configuration.definitions(), configuration.bindings(), documents.ledger, actorId, boundedLimit);
            feedbackCache.put(key, projected);
            return projected;
        });
    }

    /** Descripción: Ejecuta la operación «reset» correspondiente a la responsabilidad de RuntimeDocuments. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void reset(String metamodelId) {
        synchronized(locks.lockFor(metamodelId)) { repository.reset(metamodelId); feedbackCache.invalidate(metamodelId); }
    }

    /** Descripción: Obtiene la información correspondiente a «loadRuntime» dentro de RuntimeDocuments. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private RuntimeDocuments loadRuntime(String metamodelId) {
        Optional<StoredCanonicalDocument<GamificationRuntimeState>> state = repository.findState(metamodelId);
        Optional<StoredCanonicalDocument<GamificationLedger>> ledger = repository.findLedger(metamodelId);
        return new RuntimeDocuments(state.map(StoredCanonicalDocument::document).orElseGet(() -> GamificationRuntimeState.empty(metamodelId)),
                state.map(StoredCanonicalDocument::etag).orElse(""), ledger.map(StoredCanonicalDocument::document).orElseGet(() -> GamificationLedger.empty(metamodelId)),
                ledger.map(StoredCanonicalDocument::etag).orElse(""), state.isPresent(), ledger.isPresent());
    }

    /** Descripción: Ejecuta la operación «persist» correspondiente a la responsabilidad de RuntimeDocuments. Entradas: previous: dato de entrada necesario para la operación; state: dato de entrada necesario para la operación; ledger: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void persist(RuntimeDocuments previous, GamificationRuntimeState state, GamificationLedger ledger) {
        StoredCanonicalDocument<GamificationRuntimeState> savedState = repository.saveState(state, previous.stateEtag);
        try { repository.saveLedger(ledger, previous.ledgerEtag); }
        catch(RuntimeException exception) {
            try {
                if(previous.stateExisted) repository.saveState(previous.state, savedState.etag());
                else repository.deleteState(state.metamodelId(), savedState.etag());
            } catch(RuntimeException rollbackFailure) { exception.addSuppressed(rollbackFailure); }
            throw new GamificationApplicationException(GamificationApplicationException.Reason.STORAGE_ERROR,
                    "Gamification ledger failed and state rollback was attempted", exception);
        }
    }

    /** Descripción: Ejecuta la operación «compensate» correspondiente a la responsabilidad de RuntimeDocuments. Entradas: event: dato de entrada necesario para la operación; entries: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private List<GamificationLedgerEntry> compensate(CanonicalEvent event, List<GamificationLedgerEntry> entries) {
        String explicitEventId = text(event.payload().get("compensatesEventId"));
        String diagnosticId = text(event.payload().get("diagnosticId"));
        boolean compensationEvent = !explicitEventId.isBlank() || "core.quickfix.undone".equals(event.eventTypeId())
                || "core.validation.violation-reopened".equals(event.eventTypeId());
        if(!compensationEvent) return List.of();
        String actor = actorId(event);
        List<GamificationLedgerEntry> matches = entries.stream().filter(entry -> entry.status() == GamificationLedgerStatus.ACTIVE)
                .filter(entry -> explicitEventId.isBlank() || entry.sourceEventId().equals(explicitEventId))
                .filter(entry -> diagnosticId.isBlank() || diagnosticId.equals(entry.metadata().get("diagnosticId")))
                .filter(entry -> event.actor() == null || entry.actorId().equals(actor))
                .sorted(Comparator.comparing(GamificationLedgerEntry::occurredAt).reversed()).toList();
        if(explicitEventId.isBlank() && !diagnosticId.isBlank() && matches.size() > 1) matches = List.of(matches.get(0));
        if(explicitEventId.isBlank() && diagnosticId.isBlank()) return List.of();
        List<GamificationLedgerEntry> compensated = new ArrayList<>();
        Set<String> ids = matches.stream().map(GamificationLedgerEntry::id).collect(java.util.stream.Collectors.toSet());
        for(int index = 0; index < entries.size(); index++) {
            GamificationLedgerEntry original = entries.get(index);
            if(!ids.contains(original.id())) continue;
            entries.set(index, copyStatus(original, GamificationLedgerStatus.COMPENSATED));
            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put("reason", event.eventTypeId());
            GamificationLedgerEntry reversal = new GamificationLedgerEntry("ledger:" + UUID.randomUUID(), original.metamodelId(),
                    original.actorId(), event.id(), original.ruleId(), Instant.now(), original.pointsTypeId(), -original.pointsDelta(),
                    "", "", "", GamificationLedgerStatus.ACTIVE, original.id(), event.correlationId(), metadata);
            entries.add(reversal);
            compensated.add(reversal);
        }
        return compensated;
    }

    /** Evalúa condiciones de gamificación mediante el kernel neutral, con fallback solo para pruebas embebidas. */
    /** Descripción: Evalúa una regla de gamificación con el kernel neutral cuando está disponible. Entradas: rule, event y metamodelId. Salida: devuelve true cuando la regla aplica. */ private boolean matches(GamificationRule rule, CanonicalEvent event, String metamodelId) {
        if(metamodels == null || expressionEvaluator == null) return GamificationEventMatcher.matches(rule, event);
        return GamificationEventMatcher.matches(rule, event, metamodels.findById(metamodelId).metamodel(), expressionEvaluator);
    }

    /** Descripción: Ejecuta la operación «rejection» correspondiente a la responsabilidad de RuntimeDocuments. Entradas: rule: dato de entrada necesario para la operación; actorId: identificador que delimita el recurso objetivo; occurredAt: dato de entrada necesario para la operación; entries: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String rejection(GamificationRule rule, String actorId, Instant occurredAt, List<GamificationLedgerEntry> entries) {
        List<GamificationLedgerEntry> active = entries.stream().filter(entry -> entry.status() == GamificationLedgerStatus.ACTIVE)
                .filter(entry -> entry.actorId().equals(actorId) && entry.ruleId().equals(rule.id()) && entry.compensationOf().isBlank()).toList();
        if(rule.repeatPolicy() == GamificationRepeatPolicy.ONCE && !active.isEmpty()) return "Rule can only be applied once";
        if(rule.maxAwardsPerActor() > 0 && active.size() >= rule.maxAwardsPerActor()) return "Maximum applications per actor reached";
        LocalDate day = occurredAt.atZone(ZoneOffset.UTC).toLocalDate();
        long daily = active.stream().filter(entry -> entry.occurredAt().atZone(ZoneOffset.UTC).toLocalDate().equals(day)).count();
        if(rule.maxAwardsPerDay() > 0 && daily >= rule.maxAwardsPerDay()) return "Daily application limit reached";
        if(rule.repeatPolicy() == GamificationRepeatPolicy.DAILY_LIMIT && daily >= rule.maxAwardsPerDay()) return "Daily repeat policy limit reached";
        if(rule.repeatPolicy() == GamificationRepeatPolicy.COOLDOWN && !active.isEmpty()) {
            Instant last = active.stream().map(GamificationLedgerEntry::occurredAt).max(Instant::compareTo).orElse(Instant.MIN);
            if(last.plusSeconds(rule.cooldownSeconds()).isAfter(occurredAt)) return "Rule cooldown is active";
        }
        return "";
    }

    /** Descripción: Ejecuta la operación «appliedPoints» correspondiente a la responsabilidad de RuntimeDocuments. Entradas: rule: dato de entrada necesario para la operación; actorId: identificador que delimita el recurso objetivo; definitions: dato de entrada necesario para la operación; current: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private long appliedPoints(GamificationRule rule, String actorId, GamificationDefinitionModel definitions,
            GamificationRuntimeState current) {
        if(rule.points() >= 0 || rule.pointsTypeId().isBlank()) return rule.points();
        long minimum = definitions.pointTypes().stream().filter(type -> type.id().equals(rule.pointsTypeId()))
                .map(com.example.editor.gamification.PointTypeDefinition::minimumBalance).findFirst().orElse(0L);
        long balance = Optional.ofNullable(current.actors().get(actorId)).map(actor -> actor.points().getOrDefault(rule.pointsTypeId(), 0L)).orElse(0L);
        return Math.max(rule.points(), minimum - balance);
    }

    /** Descripción: Ejecuta la operación «entry» correspondiente a la responsabilidad de RuntimeDocuments. Entradas: metamodelId: identificador que delimita el recurso objetivo; actorId: identificador que delimita el recurso objetivo; event: dato de entrada necesario para la operación; rule: dato de entrada necesario para la operación; status: dato de entrada necesario para la operación; points: dato de entrada necesario para la operación; compensationOf: dato de entrada necesario para la operación; reason: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private GamificationLedgerEntry entry(String metamodelId, String actorId, CanonicalEvent event, GamificationRule rule,
            GamificationLedgerStatus status, long points, String compensationOf, String reason) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("eventTypeId", event.eventTypeId());
        metadata.put("diagnosticId", text(event.payload().get("diagnosticId")));
        metadata.put("subjectId", event.subject() == null ? "" : event.subject().elementId());
        metadata.put("transactionId", event.transactionId());
        metadata.put("effectKind", rule.effectKind().name());
        metadata.put("requestedPointsDelta", String.valueOf(rule.points()));
        if(!reason.isBlank()) metadata.put("reason", reason);
        return new GamificationLedgerEntry("ledger:" + UUID.randomUUID(), metamodelId, actorId, event.id(), rule.id(),
                event.occurredAt(), rule.pointsTypeId(), points, status == GamificationLedgerStatus.ACTIVE ? rule.achievementId() : "",
                status == GamificationLedgerStatus.ACTIVE ? rule.challengeId() : "", status == GamificationLedgerStatus.ACTIVE ? rule.rewardId() : "",
                status, compensationOf, event.correlationId(), metadata);
    }

    /** Descripción: Ejecuta la operación «rebuildState» correspondiente a la responsabilidad de RuntimeDocuments. Entradas: metamodelId: identificador que delimita el recurso objetivo; definitions: dato de entrada necesario para la operación; ledger: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private GamificationRuntimeState rebuildState(String metamodelId, GamificationDefinitionModel definitions, GamificationLedger ledger) {
        Map<String, MutableActor> mutable = new LinkedHashMap<>();
        Set<String> processed = new LinkedHashSet<>();
        for(GamificationLedgerEntry entry : ledger.entries()) {
            if(entry.compensationOf().isBlank()) processed.add(entry.sourceEventId() + "|" + entry.ruleId());
            // Compensation entries are audit evidence. The original entry is already marked COMPENSATED,
            // so applying the negative entry again would subtract the award twice during state rebuild.
            if(entry.status() != GamificationLedgerStatus.ACTIVE || !entry.compensationOf().isBlank()) continue;
            MutableActor actor = mutable.computeIfAbsent(entry.actorId(), ignored -> new MutableActor());
            if(!entry.pointsTypeId().isBlank()) actor.points.merge(entry.pointsTypeId(), entry.pointsDelta(), Long::sum);
            if(!entry.achievementId().isBlank()) actor.achievements.add(entry.achievementId());
            if(!entry.challengeId().isBlank()) actor.challenges.merge(entry.challengeId(), 1L, Long::sum);
            if(!entry.rewardId().isBlank()) actor.rewards.add(entry.rewardId());
        }
        Map<String, Long> minimumBalances = definitions.pointTypes().stream().collect(java.util.stream.Collectors.toMap(
                com.example.editor.gamification.PointTypeDefinition::id,
                com.example.editor.gamification.PointTypeDefinition::minimumBalance, (left, right) -> left, LinkedHashMap::new));
        mutable.values().forEach(actor -> actor.points.replaceAll((pointType, balance) -> Math.max(balance,
                minimumBalances.getOrDefault(pointType, 0L))));
        Map<String, AchievementDefinition> achievements = definitions.achievements().stream().collect(java.util.stream.Collectors.toMap(AchievementDefinition::id, value -> value));
        Map<String, ChallengeDefinition> challenges = definitions.challenges().stream().collect(java.util.stream.Collectors.toMap(ChallengeDefinition::id, value -> value));
        Map<String, GamificationActorState> actors = new LinkedHashMap<>();
        for(Map.Entry<String, MutableActor> actorEntry : mutable.entrySet()) {
            MutableActor actor = actorEntry.getValue();
            actor.achievements.forEach(id -> Optional.ofNullable(achievements.get(id)).map(AchievementDefinition::rewardId).filter(value -> !value.isBlank()).ifPresent(actor.rewards::add));
            actor.challenges.forEach((id, progress) -> Optional.ofNullable(challenges.get(id)).filter(value -> progress >= value.targetCount())
                    .map(ChallengeDefinition::rewardId).filter(value -> !value.isBlank()).ifPresent(actor.rewards::add));
            Map<String, String> levels = new LinkedHashMap<>();
            for(Map.Entry<String, Long> point : actor.points.entrySet()) {
                definitions.levels().stream().filter(level -> level.pointTypeId().equals(point.getKey()) && point.getValue() >= level.minimumPoints())
                        .max(Comparator.comparingLong(LevelDefinition::minimumPoints)).ifPresent(level -> {
                            levels.put(point.getKey(), level.id());
                            if(!level.rewardId().isBlank()) actor.rewards.add(level.rewardId());
                        });
            }
            actors.put(actorEntry.getKey(), new GamificationActorState(actorEntry.getKey(), actor.points, levels,
                    actor.achievements, actor.challenges, actor.rewards));
        }
        return new GamificationRuntimeState("1.0", metamodelId, metamodelId, Instant.now(), actors, processed);
    }

    /** Descripción: Ejecuta la operación «trim» correspondiente a la responsabilidad de RuntimeDocuments. Entradas: entries: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private List<GamificationLedgerEntry> trim(List<GamificationLedgerEntry> entries) {
        int maximum = properties.getMaximumLedgerEntries();
        if(entries.size() <= maximum) return List.copyOf(entries);
        return List.copyOf(entries.subList(entries.size() - maximum, entries.size()));
    }

    /** Descripción: Procesa la operación «publish» coordinando las responsabilidades de RuntimeDocuments. Entradas: source: dato de entrada necesario para la operación; granted: dato de entrada necesario para la operación; rejected: dato de entrada necesario para la operación; compensated: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void publish(CanonicalEvent source, List<GamificationLedgerEntry> granted,
            List<GamificationLedgerEntry> rejected, List<GamificationLedgerEntry> compensated) {
        int nextDepth = depth(source) + 1;
        for(GamificationLedgerEntry entry : granted) publish(GamificationRuntimeEventFactory.create(
                entry.pointsDelta() < 0 ? "core.gamification.penalty-applied" : "core.gamification.award-granted", source, entry, nextDepth));
        for(GamificationLedgerEntry entry : rejected) publish(GamificationRuntimeEventFactory.create("core.gamification.award-rejected", source, entry, nextDepth));
        for(GamificationLedgerEntry entry : compensated) publish(GamificationRuntimeEventFactory.create("core.gamification.award-compensated", source, entry, nextDepth));
    }
    /** Descripción: Procesa la operación «publish» coordinando las responsabilidades de RuntimeDocuments. Entradas: event: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void publish(CanonicalEvent event) { try { eventBus.publish(event); } catch(RuntimeException exception) { LOGGER.warn("Gamification event could not be delivered after commit", exception); } }
    /** Descripción: Ejecuta la operación «depth» correspondiente a la responsabilidad de RuntimeDocuments. Entradas: event: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int depth(CanonicalEvent event) { try { return Integer.parseInt(event.metadata().getOrDefault("hydra.gamification.depth", "0")); } catch(NumberFormatException exception) { return 0; } }
    /** Descripción: Ejecuta la operación «actorId» correspondiente a la responsabilidad de RuntimeDocuments. Entradas: event: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String actorId(CanonicalEvent event) {
        if(event.actor() != null && !event.actor().id().isBlank()) return event.actor().id();
        if(!event.context().sessionId().isBlank()) return event.context().sessionId();
        return "system";
    }
    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de RuntimeDocuments. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String text(Object value) { return value == null ? "" : String.valueOf(value); }
    /** Descripción: Ejecuta la operación «copyStatus» correspondiente a la responsabilidad de RuntimeDocuments. Entradas: value: valor de entrada que debe procesarse; status: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private GamificationLedgerEntry copyStatus(GamificationLedgerEntry value, GamificationLedgerStatus status) {
        return new GamificationLedgerEntry(value.id(), value.metamodelId(), value.actorId(), value.sourceEventId(), value.ruleId(), value.occurredAt(),
                value.pointsTypeId(), value.pointsDelta(), value.achievementId(), value.challengeId(), value.rewardId(), status,
                value.compensationOf(), value.correlationId(), value.metadata());
    }
    private record FeedbackProjectionKey(String semanticScopeId, String actorId, int limit, String definitionEtag, String bindingEtag, String ledgerEtag) {
        private FeedbackProjectionKey { semanticScopeId = semanticScopeId == null ? "" : semanticScopeId; actorId = actorId == null ? "" : actorId; definitionEtag = definitionEtag == null ? "" : definitionEtag; bindingEtag = bindingEtag == null ? "" : bindingEtag; ledgerEtag = ledgerEtag == null ? "" : ledgerEtag; }
    }
    private static final class FeedbackProjectionCache {
        private final int maximumEntries;
        private final LinkedHashMap<FeedbackProjectionKey, List<UserFeedbackEnvelope>> entries = new LinkedHashMap<>(16, 0.75f, true);
        /** Descripción: Inicializa una caché LRU acotada para proyecciones de feedback. Entradas: maximumEntries. Salida: caché lista para uso concurrente sincronizado. */
        /** Descripción: Inicializa una instancia de FeedbackProjectionCache con sus dependencias y datos de entrada. Entradas: maximumEntries: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private FeedbackProjectionCache(int maximumEntries) { this.maximumEntries = Math.max(8, maximumEntries); }
        private synchronized Optional<List<UserFeedbackEnvelope>> get(FeedbackProjectionKey key) { return Optional.ofNullable(entries.get(key)); }
        private synchronized void put(FeedbackProjectionKey key, List<UserFeedbackEnvelope> value) { entries.put(key, List.copyOf(value)); while(entries.size() > maximumEntries) entries.remove(entries.keySet().iterator().next()); }
        private synchronized void invalidate(String semanticScopeId) { entries.keySet().removeIf(key -> key.semanticScopeId().equals(semanticScopeId)); }
    }
    private record RuntimeDocuments(GamificationRuntimeState state, String stateEtag, GamificationLedger ledger,
            String ledgerEtag, boolean stateExisted, boolean ledgerExisted) { }
    private static final class MutableActor {
        private final Map<String, Long> points = new LinkedHashMap<>();
        private final Set<String> achievements = new LinkedHashSet<>();
        private final Map<String, Long> challenges = new LinkedHashMap<>();
        private final Set<String> rewards = new LinkedHashSet<>();
    }
}
