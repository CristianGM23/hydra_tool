package com.example.editor.gamification.feedback;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.example.editor.gamification.AchievementDefinition;
import com.example.editor.gamification.ChallengeDefinition;
import com.example.editor.gamification.GamificationBindingModel;
import com.example.editor.gamification.GamificationDefinitionModel;
import com.example.editor.gamification.GamificationLedger;
import com.example.editor.gamification.GamificationLedgerEntry;
import com.example.editor.gamification.GamificationLedgerStatus;
import com.example.editor.gamification.LevelDefinition;
import com.example.editor.gamification.PointTypeDefinition;
import com.example.editor.gamification.RewardDefinition;

/** Proyección determinista y sin estado de ledger -> feedback. El ledger es el outbox durable. */
public final class GamificationFeedbackProjector {
    /** Descripción: Evita instancias de un proyector puramente estático. Entradas: no requiere parámetros. Salida: no devuelve valor. */
    /** Descripción: Inicializa una instancia de GamificationFeedbackProjector con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private GamificationFeedbackProjector() { }

    /** Descripción: Proyecta de forma determinista el ledger canónico a retroalimentación portable y acotada. Entradas: definitions, bindings, ledger, actorFilter y limit. Salida: sobres de retroalimentación ordenados de más reciente a más antiguo. */
    /** Descripción: Ejecuta la operación «project» correspondiente a la responsabilidad de MutableProjection. Entradas: definitions: dato de entrada necesario para la operación; bindings: dato de entrada necesario para la operación; ledger: dato de entrada necesario para la operación; actorFilter: dato de entrada necesario para la operación; limit: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static List<UserFeedbackEnvelope> project(GamificationDefinitionModel definitions, GamificationBindingModel bindings,
            GamificationLedger ledger, String actorFilter, int limit) {
        if(definitions == null || bindings == null || ledger == null) return List.of();
        int boundedLimit = Math.max(1, Math.min(limit <= 0 ? 100 : limit, 1000));
        Map<String, PointTypeDefinition> pointTypes=index(definitions.pointTypes(), PointTypeDefinition::id);
        Map<String, AchievementDefinition> achievements=index(definitions.achievements(), AchievementDefinition::id);
        Map<String, ChallengeDefinition> challenges=index(definitions.challenges(), ChallengeDefinition::id);
        Map<String, RewardDefinition> rewards=index(definitions.rewards(), RewardDefinition::id);
        Map<String, LevelDefinition> levels=index(definitions.levels(), LevelDefinition::id);
        Map<String, MutableProjection> actors=new HashMap<>();
        List<UserFeedbackEnvelope> output=new ArrayList<>();
        List<GamificationLedgerEntry> ordered=ledger.entries().stream().sorted(Comparator.comparing(GamificationLedgerEntry::occurredAt).thenComparing(GamificationLedgerEntry::id)).toList();
        for(GamificationLedgerEntry entry:ordered){
            MutableProjection state=actors.computeIfAbsent(entry.actorId(), ignored->new MutableProjection());
            if(entry.status()==GamificationLedgerStatus.COMPENSATED) continue;
            UserFeedbackPresentationPolicy policy=GamificationFeedbackPolicyResolver.policy(definitions,bindings,entry.ruleId());
            if(!policy.enabled()) continue;
            if(entry.status()==GamificationLedgerStatus.REJECTED){
                if(policy.includeRejected()) add(output, envelope(entry,UserFeedbackKind.APPLICATION_REJECTED,UserFeedbackSeverity.INFO,
                        title(bindings,entry,"Application not granted"),"The gamification rule was not applied.",entry.metadata().getOrDefault("reason",""),
                        nextStep(bindings,entry,"Continue working; this rule may become eligible later."),0,entry.pointsTypeId(),0,0,"","","","",policy));
                continue;
            }
            if(!entry.compensationOf().isBlank()){
                if(!entry.pointsTypeId().isBlank()) state.points.merge(entry.pointsTypeId(),entry.pointsDelta(),Long::sum);
                add(output,envelope(entry,UserFeedbackKind.AWARD_COMPENSATED,UserFeedbackSeverity.INFO,
                        title(bindings,entry,"Previous gamification effect reverted"),"A previous gamification effect was compensated.",entry.metadata().getOrDefault("reason",""),
                        nextStep(bindings,entry,"Review the corrected model state and continue."),entry.pointsDelta(),entry.pointsTypeId(),0,0,"","","","",policy));
                continue;
            }
            String previousLevel=entry.pointsTypeId().isBlank()?"":state.levels.getOrDefault(entry.pointsTypeId(),"");
            if(!entry.pointsTypeId().isBlank()){
                PointTypeDefinition type=pointTypes.get(entry.pointsTypeId());
                long minimum=type==null?0:type.minimumBalance();
                state.points.put(entry.pointsTypeId(),Math.max(minimum,state.points.getOrDefault(entry.pointsTypeId(),0L)+entry.pointsDelta()));
            }
            if(entry.pointsDelta()!=0){
                boolean penalty=entry.pointsDelta()<0;
                String pointName=pointTypes.containsKey(entry.pointsTypeId())?pointTypes.get(entry.pointsTypeId()).name():entry.pointsTypeId();
                String defaultTitle=(penalty?"":"+")+entry.pointsDelta()+" "+pointName;
                String defaultMessage=penalty?"A verified outcome applied a gamification penalty.":"A verified outcome earned gamification points.";
                add(output,envelope(entry,penalty?UserFeedbackKind.PENALTY_APPLIED:UserFeedbackKind.POINTS_AWARDED,
                        penalty?UserFeedbackSeverity.WARNING:UserFeedbackSeverity.SUCCESS,title(bindings,entry,defaultTitle),defaultMessage,"",
                        nextStep(bindings,entry,penalty?"Review the related problem and its recommended correction.":"Continue with the next modeling goal."),
                        entry.pointsDelta(),entry.pointsTypeId(),state.points.getOrDefault(entry.pointsTypeId(),0L),0,"","","","",policy));
            }
            if(!entry.achievementId().isBlank() && state.achievements.add(entry.achievementId())){
                AchievementDefinition a=achievements.get(entry.achievementId()); String name=a==null?entry.achievementId():a.name();
                add(output,envelope(entry,UserFeedbackKind.ACHIEVEMENT_UNLOCKED,UserFeedbackSeverity.SUCCESS,
                        title(bindings,entry,"Achievement unlocked: "+name),a==null?"Achievement unlocked.":a.description(),"",
                        nextStep(bindings,entry,"Keep progressing toward the next achievement."),0,"",0,0,entry.achievementId(),"","","",policy));
                unlockReward(output,state,definitions,bindings,rewards,entry,a==null?"":a.rewardId(),policy,"Achievement reward");
            }
            if(!entry.challengeId().isBlank()){
                long progress=state.challenges.merge(entry.challengeId(),1L,Long::sum);
                ChallengeDefinition c=challenges.get(entry.challengeId());long target=c==null?0:c.targetCount();String name=c==null?entry.challengeId():c.name();
                UserFeedbackKind kind=target>0&&progress>=target?UserFeedbackKind.CHALLENGE_COMPLETED:UserFeedbackKind.CHALLENGE_PROGRESS;
                add(output,envelope(entry,kind,kind==UserFeedbackKind.CHALLENGE_COMPLETED?UserFeedbackSeverity.SUCCESS:UserFeedbackSeverity.INFO,
                        title(bindings,entry,kind==UserFeedbackKind.CHALLENGE_COMPLETED?"Challenge completed: "+name:"Challenge progress: "+name),
                        target>0?progress+" / "+target:"Progress updated.","",nextStep(bindings,entry,kind==UserFeedbackKind.CHALLENGE_COMPLETED?"Choose the next challenge.":"Continue with the recommended steps to complete the challenge."),
                        0,"",progress,target,"",entry.challengeId(),"","",policy));
                if(kind==UserFeedbackKind.CHALLENGE_COMPLETED) unlockReward(output,state,definitions,bindings,rewards,entry,c==null?"":c.rewardId(),policy,"Challenge reward");
            }
            unlockReward(output,state,definitions,bindings,rewards,entry,entry.rewardId(),policy,"Reward unlocked");
            if(!entry.pointsTypeId().isBlank()){
                long balance=state.points.getOrDefault(entry.pointsTypeId(),0L);
                LevelDefinition level=definitions.levels().stream().filter(value->value.pointTypeId().equals(entry.pointsTypeId())&&balance>=value.minimumPoints()).max(Comparator.comparingLong(LevelDefinition::minimumPoints)).orElse(null);
                if(level!=null){state.levels.put(entry.pointsTypeId(),level.id());if(!level.id().equals(previousLevel)){
                    add(output,envelope(entry,UserFeedbackKind.LEVEL_REACHED,UserFeedbackSeverity.SUCCESS,title(bindings,entry,"Level reached: "+level.name()),
                            "Your "+entry.pointsTypeId()+" balance reached "+balance+".","",nextStep(bindings,entry,"Continue progressing toward the next level."),0,entry.pointsTypeId(),balance,level.minimumPoints(),"","","",level.id(),policy));
                    unlockReward(output,state,definitions,bindings,rewards,entry,level.rewardId(),policy,"Level reward");
                }}
            }
        }
        return output.stream().filter(value->actorFilter==null||actorFilter.isBlank()||value.actorId().equals(actorFilter)).sorted(Comparator.comparing(UserFeedbackEnvelope::occurredAt).reversed().thenComparing(UserFeedbackEnvelope::id)).limit(boundedLimit).toList();
    }

    /** Descripción: Emite una única retroalimentación de recompensa cuando esta se desbloquea por primera vez en la proyección. Entradas: estado proyectado, definiciones, binding, entrada y política. Salida: añade el sobre al resultado cuando procede. */
    /** Descripción: Ejecuta la operación «unlockReward» correspondiente a la responsabilidad de MutableProjection. Entradas: output: dato de entrada necesario para la operación; state: dato de entrada necesario para la operación; definitions: dato de entrada necesario para la operación; bindings: dato de entrada necesario para la operación; rewards: dato de entrada necesario para la operación; entry: dato de entrada necesario para la operación; rewardId: identificador que delimita el recurso objetivo; policy: dato de entrada necesario para la operación; fallbackTitle: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void unlockReward(List<UserFeedbackEnvelope> output,MutableProjection state,GamificationDefinitionModel definitions,GamificationBindingModel bindings,Map<String,RewardDefinition> rewards,GamificationLedgerEntry entry,String rewardId,UserFeedbackPresentationPolicy policy,String fallbackTitle){
        if(rewardId==null||rewardId.isBlank()||!state.rewards.add(rewardId))return;RewardDefinition reward=rewards.get(rewardId);String name=reward==null?rewardId:reward.name();add(output,envelope(entry,UserFeedbackKind.REWARD_UNLOCKED,UserFeedbackSeverity.SUCCESS,title(bindings,entry,fallbackTitle+": "+name),reward==null?"Reward unlocked.":reward.description(),"",nextStep(bindings,entry,"The reward is now available."),0,"",0,0,"","",rewardId,"",policy));
    }
    /** Descripción: Construye un sobre portable estable a partir de una entrada del ledger. Entradas: entrada, semántica de feedback, progreso, referencias y política. Salida: sobre de retroalimentación correlacionado. */
    /** Descripción: Ejecuta la operación «envelope» correspondiente a la responsabilidad de MutableProjection. Entradas: entry: dato de entrada necesario para la operación; kind: dato de entrada necesario para la operación; severity: dato de entrada necesario para la operación; title: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; reason: dato de entrada necesario para la operación; nextStep: dato de entrada necesario para la operación; pointsDelta: dato de entrada necesario para la operación; pointTypeId: identificador que delimita el recurso objetivo; progressCurrent: dato de entrada necesario para la operación; progressTarget: dato de entrada necesario para la operación; achievementId: identificador que delimita el recurso objetivo; challengeId: identificador que delimita el recurso objetivo; rewardId: identificador que delimita el recurso objetivo; levelId: identificador que delimita el recurso objetivo; policy: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static UserFeedbackEnvelope envelope(GamificationLedgerEntry entry,UserFeedbackKind kind,UserFeedbackSeverity severity,String title,String message,String reason,String nextStep,long pointsDelta,String pointTypeId,long progressCurrent,long progressTarget,String achievementId,String challengeId,String rewardId,String levelId,UserFeedbackPresentationPolicy policy){
        String subject=!achievementId.isBlank()?achievementId:!challengeId.isBlank()?challengeId:!rewardId.isBlank()?rewardId:!levelId.isBlank()?levelId:pointTypeId;
        String id="feedback:"+entry.id()+":"+kind.name().toLowerCase()+(subject.isBlank()?"":":"+subject.replaceAll("[^A-Za-z0-9_.:-]","-"));
        Map<String,String> metadata=new LinkedHashMap<>();metadata.put("ledgerEntryId",entry.id());metadata.put("ruleId",entry.ruleId());metadata.put("eventTypeId",entry.metadata().getOrDefault("eventTypeId",""));metadata.put("diagnosticId",entry.metadata().getOrDefault("diagnosticId",""));
        List<UserFeedbackAction> actions=new ArrayList<>();if(!metadata.get("diagnosticId").isBlank())actions.add(new UserFeedbackAction("open-diagnostic","Open problem","OPEN_DIAGNOSTIC",Map.of("diagnosticId",metadata.get("diagnosticId"))));
        return new UserFeedbackEnvelope("1.0",id,entry.metamodelId(),entry.actorId(),kind,severity,title,message,reason,nextStep,pointsDelta,pointTypeId,progressCurrent,progressTarget,achievementId,challengeId,rewardId,levelId,entry.sourceEventId(),entry.correlationId(),entry.occurredAt(),policy,actions,metadata);
    }
    /** Descripción: Resuelve el título configurable por regla. Entradas: bindings, entrada y fallback. Salida: título efectivo. */
    /** Descripción: Ejecuta la operación «title» correspondiente a la responsabilidad de MutableProjection. Entradas: bindings: dato de entrada necesario para la operación; entry: dato de entrada necesario para la operación; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String title(GamificationBindingModel bindings,GamificationLedgerEntry entry,String fallback){return GamificationFeedbackPolicyResolver.text(bindings,entry.ruleId(),"title",fallback);}
    /** Descripción: Resuelve el siguiente paso configurable por regla. Entradas: bindings, entrada y fallback. Salida: recomendación efectiva. */
    /** Descripción: Ejecuta la operación «nextStep» correspondiente a la responsabilidad de MutableProjection. Entradas: bindings: dato de entrada necesario para la operación; entry: dato de entrada necesario para la operación; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String nextStep(GamificationBindingModel bindings,GamificationLedgerEntry entry,String fallback){return GamificationFeedbackPolicyResolver.text(bindings,entry.ruleId(),"nextStep",fallback);}
    /** Descripción: Añade un sobre únicamente cuando su política permite presentación. Entradas: output y value. Salida: actualiza la colección de salida. */
    /** Descripción: Ejecuta la operación «add» correspondiente a la responsabilidad de MutableProjection. Entradas: output: dato de entrada necesario para la operación; value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void add(List<UserFeedbackEnvelope> output,UserFeedbackEnvelope value){if(value.presentation().enabled())output.add(value);}
    /** Descripción: Indexa definiciones por identificador preservando orden determinista. Entradas: values y extractor de id. Salida: mapa indexado. */
    /** Descripción: Ejecuta la operación «index» correspondiente a la responsabilidad de MutableProjection. Entradas: values: colección de elementos que debe procesarse; id: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private static <T> Map<String,T> index(List<T> values,java.util.function.Function<T,String> id){Map<String,T> result=new LinkedHashMap<>();if(values!=null)for(T value:values)result.put(id.apply(value),value);return result;}
    private static final class MutableProjection{final Map<String,Long> points=new LinkedHashMap<>();final Map<String,String> levels=new LinkedHashMap<>();final Set<String> achievements=new LinkedHashSet<>();final Map<String,Long> challenges=new LinkedHashMap<>();final Set<String> rewards=new LinkedHashSet<>();}
}
