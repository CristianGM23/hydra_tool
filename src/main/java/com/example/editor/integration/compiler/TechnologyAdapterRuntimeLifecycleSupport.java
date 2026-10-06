package com.example.editor.integration.compiler;

/**
 * Generates the neutral transactional lifecycle used by external Java and TypeScript hosts.
 * The generated runtime deliberately depends only on JDK/ECMAScript primitives so adapters can
 * embed it without adding a HYDRA runtime binary dependency.
 */
public final class TechnologyAdapterRuntimeLifecycleSupport {
    /** Descripción: Inicializa una instancia de TechnologyAdapterRuntimeLifecycleSupport sin estado. Entradas: no requiere parámetros. Salida: impide instanciar la utilidad. */
    /** Descripción: Inicializa una instancia de TechnologyAdapterRuntimeLifecycleSupport con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private TechnologyAdapterRuntimeLifecycleSupport(){ }

    /** Descripción: Genera el runtime semántico Java transaccional para un host externo. Entradas: packageName: paquete Java de destino. Salida: devuelve el código fuente Java autocontenido. */
    /** Descripción: Ejecuta la operación «javaSemanticRuntimeSource» correspondiente a la responsabilidad de TechnologyAdapterRuntimeLifecycleSupport. Entradas: packageName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String javaSemanticRuntimeSource(String packageName){
        return """
                package %s;

                import java.util.ArrayList;
                import java.util.LinkedHashMap;
                import java.util.List;
                import java.util.Map;
                import java.util.Objects;

                /** Transactional HYDRA lifecycle shared by generated Java hosts. */
                public final class HydraSemanticRuntime {
                    public interface Engine {
                        List<Diagnostic> validate(String semanticState, String trigger);
                        List<Event> deriveEvents(String beforeState, String afterState, String trigger, long sequence);
                        List<Event> runEventFlow(List<Event> confirmedEvents, String semanticState);
                        GamificationState updateGamification(GamificationState previous, List<Event> confirmedEvents, List<Event> flowEvents);
                    }
                    public record Diagnostic(String id,String severity,String message,String elementId,List<String> quickFixIds) {
                        public Diagnostic { quickFixIds = quickFixIds == null ? List.of() : List.copyOf(quickFixIds); }
                    }
                    public record Event(long sequence,String type,String elementId,Map<String,Object> payload) {
                        public Event { payload = payload == null ? Map.of() : Map.copyOf(payload); }
                    }
                    public record GamificationState(Map<String,Long> points,String level,List<String> achievements,long penalties) {
                        public GamificationState {
                            points = points == null ? Map.of() : Map.copyOf(points);
                            level = level == null ? "" : level;
                            achievements = achievements == null ? List.of() : List.copyOf(achievements);
                        }
                        public static GamificationState empty(){ return new GamificationState(Map.of(),"",List.of(),0L); }
                    }
                    public record Snapshot(String semanticState,List<Diagnostic> diagnostics,List<Event> events,
                            GamificationState gamification,long activationEpoch,long sequence) {
                        public Snapshot {
                            semanticState = semanticState == null ? "" : semanticState;
                            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
                            events = events == null ? List.of() : List.copyOf(events);
                            gamification = gamification == null ? GamificationState.empty() : gamification;
                        }
                    }
                    public record CommitResult(boolean changed,boolean rebaselined,List<Diagnostic> diagnostics,
                            List<Event> confirmedEvents,List<Event> flowEvents,GamificationState gamification,long sequence) { }

                    private final Engine engine;
                    private final int historyLimit;
                    private String baseline = "";
                    private List<Diagnostic> diagnostics = List.of();
                    private final List<Event> events = new ArrayList<>();
                    private GamificationState gamification = GamificationState.empty();
                    private long observedActivationEpoch = Long.MIN_VALUE;
                    private long sequence;
                    private Snapshot previousCheckpoint;

                    public HydraSemanticRuntime(Engine engine){ this(engine,256); }
                    public HydraSemanticRuntime(Engine engine,int historyLimit){
                        this.engine = Objects.requireNonNull(engine,"engine");
                        this.historyLimit = Math.max(16,historyLimit);
                    }

                    /** Validates the host projection without committing baseline, events, EventFlow or gamification. */
                    public synchronized List<Diagnostic> validateHost(String semanticState,String trigger){
                        String state = normalize(semanticState);
                        if(!HydraRuntimeActivation.isEnabled()) return List.of();
                        if(needsRebaseline()) return List.of();
                        return immutable(engine.validate(state,normalizeTrigger(trigger)));
                    }

                    /** Synchronizes a confirmed host state and emits side effects only for changes committed while enabled. */
                    public synchronized CommitResult synchronize(String semanticState,String trigger){
                        String state = normalize(semanticState);
                        String normalizedTrigger = normalizeTrigger(trigger);
                        if(!HydraRuntimeActivation.isEnabled()){
                            silentBaseline(state);
                            diagnostics = List.of();
                            previousCheckpoint = checkpoint();
                            return result(false,true,List.of(),List.of());
                        }
                        if(needsRebaseline() || observedActivationEpoch == Long.MIN_VALUE){
                            List<Diagnostic> nextDiagnostics = immutable(engine.validate(state,normalizedTrigger));
                            silentBaseline(state);
                            diagnostics = nextDiagnostics;
                            previousCheckpoint = checkpoint();
                            return result(false,true,List.of(),List.of());
                        }
                        if(Objects.equals(baseline,state)){
                            diagnostics = immutable(engine.validate(state,normalizedTrigger));
                            return result(false,false,List.of(),List.of());
                        }
                        Snapshot beforeCheckpoint = checkpoint();
                        String before = baseline;
                        long nextSequence = sequence + 1L;
                        List<Diagnostic> nextDiagnostics = immutable(engine.validate(state,normalizedTrigger));
                        List<Event> confirmed = immutableEvents(engine.deriveEvents(before,state,normalizedTrigger,nextSequence));
                        List<Event> flow = immutableEvents(engine.runEventFlow(confirmed,state));
                        GamificationState nextGamification = nonNull(engine.updateGamification(gamification,confirmed,flow));
                        previousCheckpoint = beforeCheckpoint;
                        diagnostics = nextDiagnostics;
                        appendHistory(confirmed);
                        appendHistory(flow);
                        gamification = nextGamification;
                        sequence = nextSequence;
                        baseline = state;
                        return result(true,false,confirmed,flow);
                    }

                    /** Captures the full observable execution state. */
                    public synchronized Snapshot checkpoint(){
                        return new Snapshot(baseline,diagnostics,events,gamification,observedActivationEpoch,sequence);
                    }

                    /** Returns the state that existed immediately before the most recent confirmed synchronization. */
                    public synchronized Snapshot checkpointBaseline(){
                        return previousCheckpoint == null ? checkpoint() : copy(previousCheckpoint);
                    }

                    /** Restores model baseline, diagnostics, event history and gamification as one semantic unit. */
                    public synchronized void restore(Snapshot snapshot){
                        Snapshot source = Objects.requireNonNull(snapshot,"snapshot");
                        baseline = source.semanticState();
                        diagnostics = immutable(source.diagnostics());
                        events.clear();
                        events.addAll(source.events());
                        trimHistory();
                        gamification = nonNull(source.gamification());
                        observedActivationEpoch = source.activationEpoch();
                        sequence = source.sequence();
                        previousCheckpoint = copy(source);
                    }

                    public synchronized String baseline(){ return baseline; }
                    public synchronized List<Diagnostic> diagnostics(){ return List.copyOf(diagnostics); }
                    public synchronized List<Event> events(){ return List.copyOf(events); }
                    public synchronized GamificationState gamification(){ return gamification; }
                    public synchronized long sequence(){ return sequence; }

                    private boolean needsRebaseline(){ return observedActivationEpoch != HydraRuntimeActivation.epoch(); }
                    private void silentBaseline(String state){
                        baseline = state;
                        observedActivationEpoch = HydraRuntimeActivation.epoch();
                    }
                    private CommitResult result(boolean changed,boolean rebaselined,List<Event> confirmed,List<Event> flow){
                        return new CommitResult(changed,rebaselined,diagnostics,confirmed,flow,gamification,sequence);
                    }
                    private void appendHistory(List<Event> values){ events.addAll(values); trimHistory(); }
                    private void trimHistory(){ while(events.size() > historyLimit) events.remove(0); }
                    private static String normalize(String value){ return value == null ? "" : value; }
                    private static String normalizeTrigger(String value){ return value == null || value.isBlank() ? "HOST_COMMIT" : value; }
                    private static <T> List<T> immutable(List<T> values){ return values == null ? List.of() : List.copyOf(values); }
                    private static List<Event> immutableEvents(List<Event> values){ return values == null ? List.of() : List.copyOf(values); }
                    private static GamificationState nonNull(GamificationState value){ return value == null ? GamificationState.empty() : value; }
                    private static Snapshot copy(Snapshot value){
                        return new Snapshot(value.semanticState(),value.diagnostics(),value.events(),value.gamification(),value.activationEpoch(),value.sequence());
                    }
                }
                """.formatted(packageName);
    }

    /** Descripción: Genera el runtime semántico Python transaccional para un host externo. Entradas: no requiere parámetros. Salida: devuelve el código fuente Python autocontenido. */
    /** Descripción: Ejecuta la operación «pythonSemanticRuntimeSource» correspondiente a la responsabilidad de TechnologyAdapterRuntimeLifecycleSupport. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static String pythonSemanticRuntimeSource(){
        return """
                from __future__ import annotations
                from copy import deepcopy
                from dataclasses import dataclass, field
                from typing import Any, Protocol

                from . import runtime_activation

                @dataclass(frozen=True)
                class HydraDiagnostic:
                    id: str
                    severity: str
                    message: str
                    element_id: str = ''
                    quick_fix_ids: tuple[str, ...] = ()

                @dataclass(frozen=True)
                class HydraEvent:
                    sequence: int
                    type: str
                    element_id: str
                    payload: dict[str, Any] = field(default_factory=dict)

                @dataclass(frozen=True)
                class HydraGamificationState:
                    points: dict[str, int] = field(default_factory=dict)
                    level: str = ''
                    achievements: tuple[str, ...] = ()
                    penalties: int = 0

                @dataclass(frozen=True)
                class HydraRuntimeSnapshot:
                    semantic_state: str
                    diagnostics: tuple[HydraDiagnostic, ...]
                    events: tuple[HydraEvent, ...]
                    gamification: HydraGamificationState
                    activation_epoch: int
                    sequence: int

                @dataclass(frozen=True)
                class HydraCommitResult:
                    changed: bool
                    rebaselined: bool
                    diagnostics: tuple[HydraDiagnostic, ...]
                    confirmed_events: tuple[HydraEvent, ...]
                    flow_events: tuple[HydraEvent, ...]
                    gamification: HydraGamificationState
                    sequence: int

                class HydraSemanticEngine(Protocol):
                    def validate(self, state: str, trigger: str) -> list[HydraDiagnostic]: ...
                    def derive_events(self, before_state: str, after_state: str, trigger: str, sequence: int) -> list[HydraEvent]: ...
                    def run_event_flow(self, events: list[HydraEvent], state: str) -> list[HydraEvent]: ...
                    def update_gamification(self, previous: HydraGamificationState, events: list[HydraEvent], flow_events: list[HydraEvent]) -> HydraGamificationState: ...

                class HydraSemanticRuntime:
                    def __init__(self, engine: HydraSemanticEngine, history_limit: int = 256):
                        self._engine = engine
                        self._history_limit = max(16, int(history_limit))
                        self._baseline = ''
                        self._diagnostics: list[HydraDiagnostic] = []
                        self._events: list[HydraEvent] = []
                        self._gamification = HydraGamificationState()
                        self._observed_epoch = -(2**63)
                        self._sequence = 0
                        self._previous_checkpoint: HydraRuntimeSnapshot | None = None

                    def validate_host(self, semantic_state: str, trigger: str = 'HOST_VALIDATE') -> list[HydraDiagnostic]:
                        state = semantic_state or ''
                        if not runtime_activation.is_enabled() or self._needs_rebaseline():
                            return []
                        return deepcopy(list(self._engine.validate(state, trigger) or []))

                    def synchronize(self, semantic_state: str, trigger: str = 'HOST_COMMIT') -> HydraCommitResult:
                        state = semantic_state or ''
                        if not runtime_activation.is_enabled():
                            self._silent_baseline(state)
                            self._diagnostics = []
                            self._previous_checkpoint = self.checkpoint()
                            return self._result(False, True, [], [])
                        if self._needs_rebaseline() or self._observed_epoch == -(2**63):
                            next_diagnostics = deepcopy(list(self._engine.validate(state, trigger) or []))
                            self._silent_baseline(state)
                            self._diagnostics = next_diagnostics
                            self._previous_checkpoint = self.checkpoint()
                            return self._result(False, True, [], [])
                        if self._baseline == state:
                            self._diagnostics = deepcopy(list(self._engine.validate(state, trigger) or []))
                            return self._result(False, False, [], [])
                        before_checkpoint = self.checkpoint()
                        before = self._baseline
                        next_sequence = self._sequence + 1
                        next_diagnostics = deepcopy(list(self._engine.validate(state, trigger) or []))
                        confirmed = deepcopy(list(self._engine.derive_events(before, state, trigger, next_sequence) or []))
                        flow = deepcopy(list(self._engine.run_event_flow(confirmed, state) or []))
                        next_gamification = deepcopy(self._engine.update_gamification(self._gamification, confirmed, flow) or HydraGamificationState())
                        self._previous_checkpoint = before_checkpoint
                        self._diagnostics = next_diagnostics
                        self._events.extend(confirmed)
                        self._events.extend(flow)
                        self._trim()
                        self._gamification = next_gamification
                        self._sequence = next_sequence
                        self._baseline = state
                        return self._result(True, False, confirmed, flow)

                    def checkpoint(self) -> HydraRuntimeSnapshot:
                        return HydraRuntimeSnapshot(self._baseline, tuple(deepcopy(self._diagnostics)), tuple(deepcopy(self._events)), deepcopy(self._gamification), self._observed_epoch, self._sequence)

                    def checkpoint_baseline(self) -> HydraRuntimeSnapshot:
                        return deepcopy(self._previous_checkpoint) if self._previous_checkpoint is not None else self.checkpoint()

                    def restore(self, snapshot: HydraRuntimeSnapshot) -> None:
                        source = deepcopy(snapshot)
                        self._baseline = source.semantic_state
                        self._diagnostics = list(source.diagnostics)
                        self._events = list(source.events)
                        self._trim()
                        self._gamification = source.gamification
                        self._observed_epoch = source.activation_epoch
                        self._sequence = source.sequence
                        self._previous_checkpoint = deepcopy(source)

                    def baseline(self) -> str: return self._baseline
                    def diagnostics(self) -> list[HydraDiagnostic]: return deepcopy(self._diagnostics)
                    def events(self) -> list[HydraEvent]: return deepcopy(self._events)
                    def gamification(self) -> HydraGamificationState: return deepcopy(self._gamification)
                    def sequence(self) -> int: return self._sequence

                    def _needs_rebaseline(self) -> bool:
                        return self._observed_epoch != runtime_activation.epoch()

                    def _silent_baseline(self, state: str) -> None:
                        self._baseline = state
                        self._observed_epoch = runtime_activation.epoch()

                    def _trim(self) -> None:
                        overflow = len(self._events) - self._history_limit
                        if overflow > 0:
                            del self._events[:overflow]

                    def _result(self, changed: bool, rebaselined: bool, confirmed: list[HydraEvent], flow: list[HydraEvent]) -> HydraCommitResult:
                        return HydraCommitResult(changed, rebaselined, tuple(deepcopy(self._diagnostics)), tuple(deepcopy(confirmed)), tuple(deepcopy(flow)), deepcopy(self._gamification), self._sequence)
                """;
    }

    /** Descripción: Genera el runtime semántico TypeScript transaccional para un host externo. Entradas: no requiere parámetros. Salida: devuelve el código fuente TypeScript autocontenido. */
    /** Descripción: Ejecuta la operación «typescriptSemanticRuntimeSource» correspondiente a la responsabilidad de TechnologyAdapterRuntimeLifecycleSupport. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static String typescriptSemanticRuntimeSource(){
        return """
                import { hydraRuntimeActivation } from './hydra-runtime-activation.js';

                export type HydraDiagnostic={id:string;severity:string;message:string;elementId:string;quickFixIds:string[]};
                export type HydraEvent={sequence:number;type:string;elementId:string;payload:Record<string,unknown>};
                export type HydraGamificationState={points:Record<string,number>;level:string;achievements:string[];penalties:number};
                export type HydraRuntimeSnapshot={semanticState:string;diagnostics:HydraDiagnostic[];events:HydraEvent[];gamification:HydraGamificationState;activationEpoch:number;sequence:number};
                export type HydraCommitResult={changed:boolean;rebaselined:boolean;diagnostics:HydraDiagnostic[];confirmedEvents:HydraEvent[];flowEvents:HydraEvent[];gamification:HydraGamificationState;sequence:number};
                export interface HydraSemanticEngine{
                  validate(state:string,trigger:string):HydraDiagnostic[];
                  deriveEvents(beforeState:string,afterState:string,trigger:string,sequence:number):HydraEvent[];
                  runEventFlow(events:HydraEvent[],state:string):HydraEvent[];
                  updateGamification(previous:HydraGamificationState,events:HydraEvent[],flowEvents:HydraEvent[]):HydraGamificationState;
                }
                const emptyGamification=():HydraGamificationState=>({points:{},level:'',achievements:[],penalties:0});
                const copyGamification=(v:HydraGamificationState):HydraGamificationState=>({points:{...v.points},level:v.level,achievements:[...v.achievements],penalties:v.penalties});
                const copySnapshot=(v:HydraRuntimeSnapshot):HydraRuntimeSnapshot=>({semanticState:v.semanticState,diagnostics:v.diagnostics.map(d=>({...d,quickFixIds:[...d.quickFixIds]})),events:v.events.map(e=>({...e,payload:{...e.payload}})),gamification:copyGamification(v.gamification),activationEpoch:v.activationEpoch,sequence:v.sequence});

                /** Transactional HYDRA lifecycle for generated Node/TypeScript hosts. */
                export class HydraSemanticRuntime{
                  private baselineState=''; private diagnosticsState:HydraDiagnostic[]=[]; private eventHistory:HydraEvent[]=[];
                  private gamificationState=emptyGamification(); private observedEpoch=Number.MIN_SAFE_INTEGER; private sequenceValue=0;
                  private previousCheckpoint:HydraRuntimeSnapshot|undefined; private readonly historyLimit:number;
                  constructor(private readonly engine:HydraSemanticEngine,historyLimit=256){this.historyLimit=Math.max(16,historyLimit);}
                  validateHost(state:string,trigger='HOST_VALIDATE'):HydraDiagnostic[]{
                    if(!hydraRuntimeActivation.isEnabled()||this.needsRebaseline()) return [];
                    return this.engine.validate(state??'',trigger).map(d=>({...d,quickFixIds:[...d.quickFixIds]}));
                  }
                  synchronize(state:string,trigger='HOST_COMMIT'):HydraCommitResult{
                    const current=state??'';
                    if(!hydraRuntimeActivation.isEnabled()){this.silentBaseline(current);this.diagnosticsState=[];this.previousCheckpoint=this.checkpoint();return this.result(false,true,[],[]);}
                    if(this.needsRebaseline()||this.observedEpoch===Number.MIN_SAFE_INTEGER){const nextDiagnostics=[...(this.engine.validate(current,trigger)??[])];this.silentBaseline(current);this.diagnosticsState=nextDiagnostics;this.previousCheckpoint=this.checkpoint();return this.result(false,true,[],[]);}
                    if(this.baselineState===current){this.diagnosticsState=this.engine.validate(current,trigger);return this.result(false,false,[],[]);}
                    const beforeCheckpoint=this.checkpoint(); const before=this.baselineState; const next=this.sequenceValue+1;
                    const nextDiagnostics=[...(this.engine.validate(current,trigger)??[])];
                    const confirmed=[...(this.engine.deriveEvents(before,current,trigger,next)??[])];
                    const flow=[...(this.engine.runEventFlow(confirmed,current)??[])];
                    const nextGamification=this.engine.updateGamification(this.gamificationState,confirmed,flow)??emptyGamification();
                    this.previousCheckpoint=beforeCheckpoint;this.diagnosticsState=nextDiagnostics;
                    this.eventHistory.push(...confirmed,...flow);this.trim();this.gamificationState=nextGamification;
                    this.sequenceValue=next;this.baselineState=current;return this.result(true,false,confirmed,flow);
                  }
                  checkpoint():HydraRuntimeSnapshot{return copySnapshot({semanticState:this.baselineState,diagnostics:this.diagnosticsState,events:this.eventHistory,gamification:this.gamificationState,activationEpoch:this.observedEpoch,sequence:this.sequenceValue});}
                  checkpointBaseline():HydraRuntimeSnapshot{return this.previousCheckpoint?copySnapshot(this.previousCheckpoint):this.checkpoint();}
                  restore(snapshot:HydraRuntimeSnapshot):void{const s=copySnapshot(snapshot);this.baselineState=s.semanticState;this.diagnosticsState=s.diagnostics;this.eventHistory=s.events;this.gamificationState=s.gamification;this.observedEpoch=s.activationEpoch;this.sequenceValue=s.sequence;this.previousCheckpoint=copySnapshot(s);this.trim();}
                  baseline():string{return this.baselineState;} diagnostics():HydraDiagnostic[]{return [...this.diagnosticsState];} events():HydraEvent[]{return [...this.eventHistory];} gamification():HydraGamificationState{return copyGamification(this.gamificationState);} sequence():number{return this.sequenceValue;}
                  private needsRebaseline():boolean{return this.observedEpoch!==hydraRuntimeActivation.epoch();}
                  private silentBaseline(state:string):void{this.baselineState=state;this.observedEpoch=hydraRuntimeActivation.epoch();}
                  private trim():void{while(this.eventHistory.length>this.historyLimit)this.eventHistory.shift();}
                  private result(changed:boolean,rebaselined:boolean,confirmedEvents:HydraEvent[],flowEvents:HydraEvent[]):HydraCommitResult{return{changed,rebaselined,diagnostics:[...this.diagnosticsState],confirmedEvents:[...confirmedEvents],flowEvents:[...flowEvents],gamification:copyGamification(this.gamificationState),sequence:this.sequenceValue};}
                }
                """;
    }
}
