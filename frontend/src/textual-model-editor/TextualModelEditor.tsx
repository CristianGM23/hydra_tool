import { Suspense, lazy, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { HydraButton, HydraErrorBoundary, HydraTooltip } from '../design-system';
import { downloadGeneratedGrammarProject, loadActiveGrammarCompilation, loadGrammarCompilation, loadGrammar } from '../grammar-designer/api';
import type { GrammarCompilationSnapshot, GrammarDefinitionModel, GrammarDiagnostic, GrammarSummary } from '../grammar-designer/types';
import type { EditorCompletionProvider } from '../grammar-designer/GrammarMonacoEditor';
import { effectiveTextualCustomization } from '../grammar-designer/textualCustomization';
import { buildGrammarTextualExample } from '../grammar-designer/grammarTextualExample';
import { clampTextRange, clampedPositionToOffset } from '../grammar-designer/grammarTextRange';
import { useHydraLanguage } from '../i18n';
import { generateTextualSessionCode, listTextualSessionCodeGenerationTargets, previewTextualSessionCode } from '../model-codegen/api';
import type { ModelCodeGenerationPreview, ModelCodeGenerationTarget } from '../model-codegen/types';
import {
    checkTextualRuntime,
    createTextualDefinition,
    createTextualSession,
    listGrammarSummaries,
    listTextualDefinitions,
    loadTextualDefinition,
    replaceTextualDefinition,
    listTextualSharedLayerOptions,
    parseTextualSession,
    saveTextualSession,
} from './api';
import type {
    TextualEditorDefinitionModel,
    TextualModelDiagnostic,
    TextualModelSession,
    TextualModelSourceDocument,
    TextualRuntimeNegotiation,
} from './types';
import { TextualLanguageServicesPanel } from './TextualLanguageServicesPanel';
import { TextualModelProblems } from './TextualModelProblems';
import { TextualModelOutline } from './TextualModelOutline';
import { textualModelCompletions } from './textualModelCompletion';
import './textual-model-editor.css';

const features = ['COMPLETION', 'HOVER', 'GO_TO_DEFINITION', 'FIND_REFERENCES', 'RENAME', 'FORMATTING', 'FOLDING', 'OUTLINE', 'DIAGNOSTICS', 'QUICK_FIXES'] as const;
const INLINE_PERSPECTIVE_OPTIONS = [
    { id: 'SEMANTIC_MODEL', label: 'Valid semantic model' },
    { id: 'GENERATED_VIEW', label: 'Generated output preview' },
] as const;
type InlinePerspectiveMode = typeof INLINE_PERSPECTIVE_OPTIONS[number]['id'];

const LazyGrammarMonacoEditor = lazy(() => import('../grammar-designer/GrammarMonacoEditor').then(module => ({ default: module.GrammarMonacoEditor })));
const LazyModelCodeGenerationDialog = lazy(() => import('../model-codegen/ModelCodeGenerationDialog').then(module => ({ default: module.ModelCodeGenerationDialog })));

interface RuntimeResolutionState {
    key:string;
    negotiation:TextualRuntimeNegotiation|null;
    snapshot:GrammarCompilationSnapshot|null;
    error:string;
    failed:boolean;
}

function emptyDefinition(grammarId = ''): TextualEditorDefinitionModel {
    return {
        schemaVersion: '1.0', id: 'textual-editor', name: 'Editor textual', grammarId, compilationSnapshotId: '',
        syntaxStyleDefinitionId: '', fileExtensions: ['.txt'], entryRule: '', runtimeMode: 'AUTO', features: [...features],
        validationModelIds: [], actionModelIds: [], eventDefinitionIds: [], eventFlowIds: [], gamificationDefinitionIds: [], settings: { 'hydra.grammarSnapshotPolicy': 'FOLLOW_ACTIVE' },
    };
}

function diagnosticAdapter(session: TextualModelSession | null): GrammarDiagnostic[] {
    return (session?.diagnostics ?? []).map(item => ({
        ...item,
        cause: item.metadata?.cause ?? item.message,
        rule: '',
        property: item.metadata?.property ?? '',
        expectedType: item.metadata?.type ?? '',
        nativeCode: '',
        technicalDetail: item.code,
        candidates: [],
        codeActionIds: [],
    }));
}

const GRAMMAR_SNAPSHOT_POLICY = 'hydra.grammarSnapshotPolicy';

function followsActiveGrammar(definition: TextualEditorDefinitionModel): boolean {
    return (definition.settings?.[GRAMMAR_SNAPSHOT_POLICY] ?? 'FOLLOW_ACTIVE').toUpperCase() !== 'PINNED';
}

function sessionMatchesSource(session: TextualModelSession | null, source: TextualModelSourceDocument): boolean {
    if (session === null || session.lastParsedRevision !== source.revision) return false;
    const document = session.documents.find(item => item.id === source.id);
    return document?.content === source.content;
}

function createDefaultGenerationOptions(definition: TextualEditorDefinitionModel, _session: TextualModelSession, modelName: string) {
    return {
        namespace: `generated.textual.${definition.grammarId || 'model'}`,
        artifactName: modelName,
        includeTraceability: true,
        features: {
            generateTests: false,
            generateGettersSetters: false,
            generateComparison: false,
            generateStringRepresentation: false,
            generateEquality: false,
        },
    };
}

interface TextualActionButtonProps {
    label: string;
    tooltipLabel: string;
    tooltipTitle: string;
    tooltipBody: ReactNode;
    onClick?: () => void;
    disabled?: boolean;
    variant?: 'primary' | 'secondary' | 'ghost';
    className?: string;
}

function TextualActionButton({label,tooltipLabel,tooltipTitle,tooltipBody,onClick,disabled,variant,className}:TextualActionButtonProps) {
    return <div className="hydra-button-with-tooltip"><HydraButton type="button" variant={variant} disabled={disabled} className={className} onClick={onClick}>{label}</HydraButton><HydraTooltip label={tooltipLabel} title={tooltipTitle}>{tooltipBody}</HydraTooltip></div>;
}

export function TextualModelEditor() {
    const { t } = useHydraLanguage();
    const [grammars, setGrammars] = useState<GrammarSummary[]>([]);
    const [definitions, setDefinitions] = useState<TextualEditorDefinitionModel[]>([]);
    const [definition, setDefinition] = useState<TextualEditorDefinitionModel>(() => emptyDefinition());
    const [session, setSession] = useState<TextualModelSession | null>(null);
    const [source, setSource] = useState<TextualModelSourceDocument>({ id: 'main', relativePath: 'model.txt', content: '', revision: 0 });
    const [message, setMessage] = useState('');
    const [grammarModel,setGrammarModel]=useState<GrammarDefinitionModel|null>(null);
    const [focusProblem, setFocusProblem] = useState<TextualModelDiagnostic | null>(null);
    const [runtimeState, setRuntimeState] = useState<RuntimeResolutionState>({key:'',negotiation:null,snapshot:null,error:'',failed:false});
    const [perspectivesOpen, setPerspectivesOpen] = useState(false);
    const [validationPending, setValidationPending] = useState(false);
    const [inlinePerspective, setInlinePerspective] = useState<InlinePerspectiveMode>('SEMANTIC_MODEL');
    const [inlineTargets, setInlineTargets] = useState<ModelCodeGenerationTarget[]>([]);
    const [inlineTargetId, setInlineTargetId] = useState('');
    const [inlinePreview, setInlinePreview] = useState<ModelCodeGenerationPreview | null>(null);
    const [inlinePreviewLoading, setInlinePreviewLoading] = useState(false);
    const [inlinePreviewError, setInlinePreviewError] = useState('');
    const runtimeSerial = useRef(0);
    const sessionSerial = useRef(0);
    const etagRef = useRef('');
    const definitionEtagRef = useRef('');
    const sourceRef = useRef(source);
    const sessionRef = useRef<TextualModelSession | null>(null);
    const parseBusyRef = useRef(false);
    const parseGenerationRef = useRef(0);
    const queuedSourceRef = useRef<TextualModelSourceDocument | null>(null);
    useEffect(() => { sourceRef.current = source; }, [source]);
    const runtimeKey = definition.grammarId.trim()===''?'':`${definition.grammarId}|${definition.compilationSnapshotId}|${definition.runtimeMode}`;
    const currentRuntime = runtimeState.key===runtimeKey?runtimeState:null;
    const runtime = currentRuntime?.negotiation??null;
    const activeSnapshot = currentRuntime?.snapshot??null;
    const runtimeError = currentRuntime?.error??'';
    const runtimeLoading = runtimeKey!==''&&currentRuntime===null;
    const selectedGrammar = grammars.find(item => item.id === definition.grammarId);
    const modelDisplayName = String((session?.lastValidSemanticModel as {name?:unknown}|null)?.name ?? `${definition.name}-model`);

    useEffect(() => {
        let disposed=false;
        void Promise.all([listGrammarSummaries(), listTextualDefinitions()]).then(async([availableGrammars, availableDefinitions]) => {
            if(disposed)return;
            setGrammars(availableGrammars);
            if (availableDefinitions[0]) {
                const loaded=await loadTextualDefinition(availableDefinitions[0].id);
                if(disposed)return;
                definitionEtagRef.current=loaded.etag;setDefinitions(availableDefinitions);setDefinition(loaded.definition);return;
            }
            if (availableGrammars[0]) {
                const used=new Set(availableDefinitions.map(item=>item.id));
                const draft=emptyDefinition(availableGrammars[0].id);let nextId=draft.id,index=2;while(used.has(nextId))nextId=`${draft.id}-${index++}`;
                const uniqueDraft=nextId===draft.id?draft:{...draft,id:nextId};
                try{
                    const created=await createTextualDefinition(uniqueDraft);
                    if(disposed)return;
                    const loaded=await loadTextualDefinition(created.id);if(disposed)return;
                    definitionEtagRef.current=loaded.etag;setDefinitions([loaded.definition]);setDefinition(loaded.definition);
                }catch(error){if(!disposed){setDefinitions([]);setDefinition(uniqueDraft);setMessage(error instanceof Error?error.message:String(error));}}
                return;
            }
            setDefinitions([]);
        }).catch(error => { if(!disposed)setMessage(error instanceof Error ? error.message : String(error)); });
        return()=>{disposed=true;};
    }, []);

    useEffect(()=>{
        if(definition.grammarId.trim()===''){queueMicrotask(()=>setGrammarModel(null));return;}
        let disposed=false;void loadGrammar(definition.grammarId).then(result=>{if(disposed)return;setGrammarModel(result.grammar);const current=sourceRef.current;if(current.revision===0&&current.content.trim()===''){const example=buildGrammarTextualExample(result.grammar);const seeded={...current,content:example.startsWith('// ')?'':example};sourceRef.current=seeded;setSource(seeded);}}).catch(()=>{if(!disposed)setGrammarModel(null);});return()=>{disposed=true;};
    },[definition.grammarId,activeSnapshot?.id]);

    useEffect(() => {
        if (runtimeKey === '') return;
        const current = ++runtimeSerial.current;
        let disposed = false;
        void (async()=>{
            try{
                const snapshot=followsActiveGrammar(definition)||definition.compilationSnapshotId.trim()===''?await loadActiveGrammarCompilation(definition.grammarId):await loadGrammarCompilation(definition.compilationSnapshotId);
                if(disposed||current!==runtimeSerial.current)return;
                if(snapshot===null){setRuntimeState({key:runtimeKey,negotiation:null,snapshot:null,error:t('Compile the grammar successfully before using its textual editor.'),failed:true});return;}
                const pinned={...definition,compilationSnapshotId:snapshot.id};
                const negotiation=await checkTextualRuntime(pinned);
                if(!disposed&&current===runtimeSerial.current)setRuntimeState({key:runtimeKey,negotiation,snapshot,error:'',failed:false});
            }catch(error){if(!disposed&&current===runtimeSerial.current)setRuntimeState({key:runtimeKey,negotiation:null,snapshot:null,error:error instanceof Error?error.message:String(error),failed:true});}
        })();
        return () => { disposed = true; };
    }, [definition, runtimeKey, t]);

    useEffect(() => {
        if (runtimeKey === '' || activeSnapshot === null) return;
        let disposed = false;
        const pinned = { ...definition, compilationSnapshotId: activeSnapshot.id };
        void listTextualSharedLayerOptions(pinned).then(options => {
            if (disposed) return;
            setDefinition(current => {
                const same =
                    JSON.stringify(current.validationModelIds) === JSON.stringify(options.validationModelIds) &&
                    JSON.stringify(current.actionModelIds) === JSON.stringify(options.actionModelIds) &&
                    JSON.stringify(current.eventDefinitionIds) === JSON.stringify(options.eventDefinitionIds) &&
                    JSON.stringify(current.eventFlowIds) === JSON.stringify(options.eventFlowIds) &&
                    JSON.stringify(current.gamificationDefinitionIds) === JSON.stringify(options.gamificationDefinitionIds);
                return same ? current : {
                    ...current,
                    validationModelIds: options.validationModelIds,
                    actionModelIds: options.actionModelIds,
                    eventDefinitionIds: options.eventDefinitionIds,
                    eventFlowIds: options.eventFlowIds,
                    gamificationDefinitionIds: options.gamificationDefinitionIds,
                };
            });
        }).catch(() => {
            // shared-layer negotiation is opportunistic here: the editor remains usable with its basic language services.
        });
        return () => { disposed = true; };
    }, [activeSnapshot, definition, runtimeKey]);

    const acceptSessionResult=useCallback((result:{session:TextualModelSession;etag:string})=>{
        sessionRef.current=result.session;
        setSession(result.session);
        etagRef.current=result.etag;
        if(sessionMatchesSource(result.session,sourceRef.current))setValidationPending(false);
    },[]);
    function resetSession(){
        sessionSerial.current++;
        parseGenerationRef.current++;
        queuedSourceRef.current=null;
        parseBusyRef.current=false;
        sessionRef.current=null;
        setSession(null);
        setValidationPending(false);
        setInlineTargets([]);
        setInlineTargetId('');
        setInlinePreview(null);
        setInlinePreviewError('');
        etagRef.current='';
        setFocusProblem(null);
    }

    const sessionId=session?.id??'';
    useEffect(() => {
        if(runtimeKey===''||activeSnapshot===null||currentRuntime?.failed||currentRuntime?.negotiation===null)return;
        const persistedDefinition=definitions.find(item=>item.id===definition.id);
        if(persistedDefinition===undefined||persistedDefinition.grammarId!==definition.grammarId)return;
        if(session!==null&&session.editorDefinitionId===definition.id&&session.grammarSnapshotId===activeSnapshot.id)return;
        const current=++sessionSerial.current;
        parseGenerationRef.current++;
        queuedSourceRef.current=null;
        const start=window.setTimeout(()=>{
            if(current!==sessionSerial.current)return;
            setValidationPending(true);
            void createTextualSession(definition.id,[sourceRef.current]).then(result=>{
                if(current!==sessionSerial.current)return;
                acceptSessionResult(result);
                setMessage(result.session.diagnostics.some(item=>item.severity==='ERROR')?t('The current draft does not comply with the active grammar.'):t('Textual session started on the active compiled grammar.'));
            }).catch(error=>{
                if(current===sessionSerial.current){setValidationPending(false);setMessage(error instanceof Error?error.message:String(error));}
            });
        },0);
        return()=>window.clearTimeout(start);
    },[acceptSessionResult,activeSnapshot,currentRuntime?.failed,currentRuntime?.negotiation,definition,definitions,runtimeKey,session,t]);

    const drainParseQueue=useCallback(async(sessionIdToParse:string,generation:number):Promise<void>=>{
        if(parseBusyRef.current)return;
        parseBusyRef.current=true;
        try{
            while(queuedSourceRef.current!==null&&generation===parseGenerationRef.current&&sessionIdToParse===sessionRef.current?.id){
                const target=queuedSourceRef.current;
                queuedSourceRef.current=null;
                try{
                    const result=await parseTextualSession(sessionIdToParse,target.revision,[target],etagRef.current);
                    if(generation!==parseGenerationRef.current||sessionIdToParse!==sessionRef.current?.id)return;
                    if(result.session.lastParsedRevision===target.revision)acceptSessionResult(result);
                }catch(error){
                    if(generation===parseGenerationRef.current&&sessionIdToParse===sessionRef.current?.id)setMessage(error instanceof Error?error.message:String(error));
                }
            }
        }finally{
            parseBusyRef.current=false;
            if(queuedSourceRef.current!==null&&generation===parseGenerationRef.current&&sessionIdToParse===sessionRef.current?.id)void drainParseQueue(sessionIdToParse,generation);
        }
    },[acceptSessionResult]);

    useEffect(() => {
        if (sessionId === '') return;
        const currentSession=sessionRef.current;
        if(sessionMatchesSource(currentSession,source)){setValidationPending(false);return;}
        setValidationPending(true);
        const generation=parseGenerationRef.current;
        const target={...source};
        const timer = window.setTimeout(() => {
            queuedSourceRef.current=target;
            void drainParseQueue(sessionId,generation);
        }, 250);
        return () => window.clearTimeout(timer);
    }, [drainParseQueue, source, sessionId]);

    useEffect(() => {
        if (session === null || session.lastValidSemanticModel === null) {
            const reset=window.setTimeout(()=>{setInlineTargets([]);setInlineTargetId('');setInlinePreview(null);setInlinePreviewError('');},0);
            return()=>window.clearTimeout(reset);
        }
        let disposed = false;
        void listTextualSessionCodeGenerationTargets(session.id).then(targets => {
            if (disposed) return;
            setInlineTargets(targets);
            setInlineTargetId(current => current !== '' && targets.some(item => item.id === current)
                ? current
                : (targets.find(item => item.id === 'xmi')?.id ?? targets[0]?.id ?? ''));
        }).catch(error => {
            if (disposed) return;
            setInlineTargets([]);
            setInlineTargetId('');
            setInlinePreview(null);
            setInlinePreviewError(error instanceof Error ? error.message : String(error));
        });
        return () => { disposed = true; };
    }, [session]);

    useEffect(() => {
        if (session === null || inlinePerspective !== 'GENERATED_VIEW' || inlineTargetId === '') {
            if (inlinePerspective !== 'GENERATED_VIEW') {
                const reset=window.setTimeout(()=>{setInlinePreview(null);setInlinePreviewError('');},0);
                return()=>window.clearTimeout(reset);
            }
            return;
        }
        let disposed = false;
        const start=window.setTimeout(()=>{
            if(disposed)return;
            setInlinePreviewLoading(true);
            setInlinePreviewError('');
            const options = createDefaultGenerationOptions(definition, session, modelDisplayName);
            void previewTextualSessionCode(session.id, inlineTargetId, options).then(result => {
                if (disposed) return;
                setInlinePreview(result);
            }).catch(error => {
                if (disposed) return;
                setInlinePreview(null);
                setInlinePreviewError(error instanceof Error ? error.message : String(error));
            }).finally(() => {
                if (!disposed) setInlinePreviewLoading(false);
            });
        },0);
        return () => { disposed = true; window.clearTimeout(start); };
    }, [definition, inlinePerspective, inlineTargetId, modelDisplayName, session]);

    const valid = !validationPending && session?.draftValid === true && session.diagnostics.every(item => item.severity !== 'ERROR');
    const lastGood = useMemo(() => session?.metadata?.lastKnownGood ?? t('Not available'), [session, t]);
    const completionProvider = useMemo<EditorCompletionProvider|undefined>(() => grammarModel===null?undefined:(text,cursorOffset) => textualModelCompletions(grammarModel,text,cursorOffset).map(item=>({
        label:item.label,
        insertText:item.insertText,
        kind:item.kind,
        replaceStart:item.replaceStart,
        replaceEnd:item.replaceEnd,
        detail:item.kind==='LITERAL'?`${t('Grammar literal')} · ${t('Rule')} ${item.ruleName}`:item.kind==='REFERENCE'?`${t('Reference')} · ${item.propertyName||item.expected} · ${t('Rule')} ${item.ruleName}`:`${item.expected}${item.propertyName!==''?` · ${t('Property')} ${item.propertyName}`:''} · ${t('Rule')} ${item.ruleName}`,
        documentation:item.kind==='LITERAL'
            ? `${t('The active grammar requires this literal at the current position.')} ${t('Rule')}: ${item.ruleName}.`
            : item.kind==='REFERENCE'
                ? `${t('Enter an identifier that references the element required by this grammar position.')} ${t('Rule')}: ${item.ruleName}.`
                : `${t('Enter a value that complies with the expected terminal.')} ${t(item.expected)}${item.propertyName!==''?` · ${t('Property')}: ${item.propertyName}`:''}.`
    })), [grammarModel,t]);

    const draftStatusKey = validationPending
        ? 'validating'
        : session === null
            ? 'Not available'
            : session.diagnostics.some(item => item.severity === 'ERROR')
                ? 'needs review'
                : valid
                    ? 'validated'
                    : 'editing';

    const selectedInlineTarget = inlineTargets.find(item => item.id === inlineTargetId) ?? null;

    async function persistDefinition() {
        try {
            const existing=definitions.some(item=>item.id===definition.id);
            if(existing){
                let etag=definitionEtagRef.current;
                if(etag===''){const loaded=await loadTextualDefinition(definition.id);etag=loaded.etag;definitionEtagRef.current=etag;}
                const replaced=await replaceTextualDefinition(definition,etag);definitionEtagRef.current=replaced.etag;
                setDefinition(replaced.definition);setDefinitions(items=>items.map(item=>item.id===replaced.definition.id?replaced.definition:item));
            }else{
                const created=await createTextualDefinition(definition);const loaded=await loadTextualDefinition(created.id);definitionEtagRef.current=loaded.etag;
                setDefinition(loaded.definition);setDefinitions(items=>[...items.filter(item=>item.id!==loaded.definition.id),loaded.definition]);
            }
            setMessage(t('Definition saved.'));
        } catch (error) { setMessage(error instanceof Error ? error.message : String(error)); }
    }

    async function start() {
        try {
            const current=++sessionSerial.current;
            parseGenerationRef.current++;queuedSourceRef.current=null;setValidationPending(true);
            const result = await createTextualSession(definition.id, [source]);
            if(current!==sessionSerial.current)return;
            acceptSessionResult(result);
            setMessage(result.session.diagnostics.some(item=>item.severity==='ERROR')?t('The current draft does not comply with the active grammar.'):t('Textual session started on the active compiled grammar.'));
        } catch (error) { setMessage(error instanceof Error ? error.message : String(error)); }
    }

    async function save() {
        if (!session) return;
        try {
            const result = await saveTextualSession(session.id, etagRef.current);
            acceptSessionResult(result); setMessage(t('Valid textual model promoted.'));
        } catch (error) { setMessage(error instanceof Error ? error.message : String(error)); }
    }

    async function downloadLanguageProject() {
        if (activeSnapshot === null) return;
        try {
            await downloadGeneratedGrammarProject(activeSnapshot.id, activeSnapshot.grammarId);
        } catch {
            setMessage(t('Could not download the generated language project.'));
        }
    }

    function changeContent(content: string) {
        setValidationPending(true);
        setSource(current => ({ ...current, content, revision: current.revision + 1 }));
    }

    function applySyntaxQuickFix(diagnostic: TextualModelDiagnostic) {
        const replacement=diagnostic.metadata?.quickFixReplacement;
        if(replacement===undefined)return;
        const range=clampTextRange(source.content,diagnostic.range);
        const start=clampedPositionToOffset(source.content,range.start);
        const end=clampedPositionToOffset(source.content,range.end);
        const kind=(diagnostic.metadata?.quickFixKind??'REPLACE').toUpperCase();
        const inserted=kind==='INSERT'&&start>0&&!/\s$/.test(source.content.slice(0,start))?` ${replacement}`:replacement;
        const next=source.content.slice(0,start)+inserted+source.content.slice(kind==='INSERT'?start:end);
        changeContent(next);
        setFocusProblem(null);
        setMessage(t('Suggested grammar correction applied. HYDRA is validating the updated draft.'));
    }

    function renderEditorFallback() {
        return <div className="textual-editor-loading-fallback">
            <div className="textual-editor-loading-copy">
                <strong>{t('Safe editor')}</strong>
                <span>{t('The advanced editor surface is loading or recovering. You can keep editing the model text here.')}</span>
            </div>
            <textarea
                aria-label={t('Textual model source editor')}
                className="textual-editor-fallback-textarea"
                spellCheck={false}
                value={source.content}
                onChange={event => changeContent(event.target.value)}
            />
        </div>;
    }

    return <main className="textual-model-editor">
        <header>
            <div><span>{t('Textual model editor')}</span><h2>{definition.name}</h2><p>{t('The editable text and the last valid semantic model are kept as independent states.')}</p></div>
            <div>
                <TextualActionButton label={t('Save definition')} tooltipLabel={t('Information about Save definition')} tooltipTitle={t('Save definition')} tooltipBody={<><p>{t('Store the textual editor definition so you can reopen the same grammar, services and runtime settings later.')}</p><p>{t('This action saves the editor configuration. It does not promote the current model text.')}</p></>} onClick={()=>void persistDefinition()}/>
                <TextualActionButton label={t('New session')} tooltipLabel={t('Information about New session')} tooltipTitle={t('New session')} tooltipBody={<><p>{t('Start a fresh editing session on the active compiled grammar version using the current draft as the starting point.')}</p><p>{t('Use it when you want HYDRA to reopen the text against the current compiled grammar services.')}</p></>} onClick={()=>void start()}/>
                <TextualActionButton label={t('View model perspectives')} tooltipLabel={t('Information about View model perspectives')} tooltipTitle={t('View model perspectives')} tooltipBody={<><p>{t('Open the complete export and preview catalog for the last valid semantic model, including XMI, code and documentation targets.')}</p><p>{t('The command is enabled only when HYDRA already has a valid semantic model to project.')}</p></>} onClick={()=>setPerspectivesOpen(true)} disabled={session === null || session.lastValidSemanticModel === null}/>
                <TextualActionButton label={t('Promote valid model')} tooltipLabel={t('Information about Promote valid model')} tooltipTitle={t('Promote valid model')} tooltipBody={<><p>{t('Replace the previously accepted semantic model with the current text only when the draft has been validated successfully.')}</p><p>{t('HYDRA keeps the last valid semantic model until this action succeeds.')}</p></>} onClick={()=>void save()} variant="primary" disabled={!session || !valid || session.lastParsedRevision !== source.revision}/>
            </div>
        </header>
        {message && <div className="textual-message" role="status">{message}</div>}
        <section className="textual-definition">
            <label>{t('Definition')}<select value={definition.id} onChange={event => { const found = definitions.find(item => item.id === event.target.value); if (found) { resetSession(); setDefinition(found); } }}>
                {definitions.map(item => <option key={item.id}>{item.id}</option>)}<option value="textual-editor">{t('New definition')}</option>
            </select></label>
            <label>ID<input value={definition.id} onChange={event => setDefinition({ ...definition, id: event.target.value })} /></label>
            <label>{t('Name')}<input value={definition.name} onChange={event => setDefinition({ ...definition, name: event.target.value })} /></label>
            <label>{t('Grammar')}<select value={definition.grammarId} onChange={event => { resetSession(); const empty={...sourceRef.current,content:'',revision:0};sourceRef.current=empty;setSource(empty);setDefinition({ ...definition, grammarId: event.target.value, compilationSnapshotId: '', settings:{...definition.settings,[GRAMMAR_SNAPSHOT_POLICY]:'FOLLOW_ACTIVE'} }); }}>
                {grammars.map(item => <option key={item.id} value={item.id}>{item.name} · {item.technology}</option>)}
            </select></label>
        </section>
        <section className="textual-runtime-status">
            <span>{t('Grammar')}: <strong>{selectedGrammar ? `${selectedGrammar.name} · ${selectedGrammar.technology}` : t('Not available')}</strong></span>
            <span>{t('Compiled grammar version')}: <strong>{session?.grammarSnapshotId ?? activeSnapshot?.id ?? t('No compiled version')}</strong></span>
            <span>{t('Draft status')}: <strong>{t(draftStatusKey)}</strong></span>
            <span>{t('Last valid model')}: <strong>{lastGood}</strong></span>
        </section>
        <TextualLanguageServicesPanel snapshot={activeSnapshot} negotiation={runtime} loading={runtimeLoading} error={runtimeError} neutralCompletionAvailable={grammarModel!==null} onDownloadProject={() => void downloadLanguageProject()} />
        <section className="textual-workbench">
            <div className="textual-editor">
                <HydraErrorBoundary scope={t('Textual editor surface')} fallback={renderEditorFallback()}>
                    <Suspense fallback={renderEditorFallback()}>
                        <LazyGrammarMonacoEditor
                            value={source.content}
                            languageId={definition.grammarId || 'plaintext'}
                            diagnostics={validationPending ? [] : diagnosticAdapter(session)}
                            onChange={changeContent}
                            completionProvider={completionProvider}
                            focusRange={focusProblem?.range}
                            customization={grammarModel ? effectiveTextualCustomization(grammarModel) : undefined}
                        />
                    </Suspense>
                </HydraErrorBoundary>
            </div>
            <aside className="textual-semantic-panel">
                <div className="textual-semantic-panel-header">
                    <div>
                        <strong>{t('Validated model views')}</strong>
                        <span>{t('Inspect the accepted semantic model or preview generated outputs derived from it.')}</span>
                    </div>
                    <div className="textual-semantic-panel-controls">
                        <label>
                            <span>{t('View')}</span>
                            <select value={inlinePerspective} onChange={event => setInlinePerspective(event.target.value as InlinePerspectiveMode)}>
                                {INLINE_PERSPECTIVE_OPTIONS.map(option => <option key={option.id} value={option.id}>{t(option.label)}</option>)}
                            </select>
                        </label>
                        {inlinePerspective === 'GENERATED_VIEW' && <label>
                            <span>{t('Generated target')}</span>
                            <select value={inlineTargetId} onChange={event => setInlineTargetId(event.target.value)} disabled={inlineTargets.length === 0}>
                                {inlineTargets.length === 0
                                    ? <option value="">{t('No generated targets')}</option>
                                    : inlineTargets.map(target => <option key={target.id} value={target.id}>{target.displayName}</option>)}
                            </select>
                        </label>}
                    </div>
                </div>
                <TextualModelOutline sourceMap={session?.lastValidSourceMap ?? []} />
                {inlinePerspective === 'SEMANTIC_MODEL'
                    ? <pre>{JSON.stringify(session?.lastValidSemanticModel ?? {}, null, 2)}</pre>
                    : <div className="textual-generated-preview">
                        {inlineTargets.length === 0 && <div className="textual-generated-preview-empty">
                            <strong>{t('No generated targets')}</strong>
                            <span>{t('Compile and configure generation targets to preview Java, XMI, Python, diagrams or other exported views here.')}</span>
                        </div>}
                        {inlinePreviewLoading && <div className="textual-generated-preview-empty"><strong>{t('Loading generated preview…')}</strong></div>}
                        {!inlinePreviewLoading && inlinePreviewError !== '' && <div className="textual-generated-preview-empty"><strong>{t('Generated preview unavailable')}</strong><span>{inlinePreviewError}</span></div>}
                        {!inlinePreviewLoading && inlinePreview !== null && selectedInlineTarget !== null && <>
                            <div className="textual-generated-preview-meta">
                                <strong>{selectedInlineTarget.displayName}</strong>
                                <span>{selectedInlineTarget.description}</span>
                                <small>{inlinePreview.fileName} · {selectedInlineTarget.extension || selectedInlineTarget.mediaType}</small>
                            </div>
                            <pre>{inlinePreview.content}</pre>
                        </>}
                    </div>}
            </aside>
        </section>
        <TextualModelProblems diagnostics={validationPending ? [] : session?.diagnostics ?? []} pending={session===null||validationPending} onNavigate={setFocusProblem} onApplySyntaxFix={applySyntaxQuickFix} />
        {session !== null && <Suspense fallback={null}>
            <LazyModelCodeGenerationDialog
                open={perspectivesOpen}
                modelName={String((session.lastValidSemanticModel as {name?:unknown}|null)?.name ?? `${definition.name}-model`)}
                defaultTargetId="xmi"
                defaultNamespace={`generated.textual.${definition.grammarId || 'model'}`}
                loadTargets={() => listTextualSessionCodeGenerationTargets(session.id)}
                generate={(targetId, options, selection) => generateTextualSessionCode(session.id, targetId, options, selection)}
                preview={(targetId, options, selection) => previewTextualSessionCode(session.id, targetId, options, selection)}
                onClose={() => setPerspectivesOpen(false)}
                onError={value => setMessage(value)}
                onMessage={value => setMessage(value)}
            />
        </Suspense>}
    </main>;
}

export default TextualModelEditor;
