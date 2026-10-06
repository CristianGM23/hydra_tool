import { HydraButton } from '../design-system/HydraButton';
import { useMemo, useState } from 'react';
import type { MouseEvent } from 'react';
import type { ArtifactKind, ProjectTreeNode } from './types';
import { FileIcon, FolderIcon, FolderTreeIcon, ProjectIcon } from '../components/AppIcons';

interface ProjectTreeProps {
    root: ProjectTreeNode;
    selectedPath: string;
    selectedPaths?: ReadonlySet<string>;
    changedPaths: Set<string>;
    onSelect: (node: ProjectTreeNode, additive?: boolean) => void;
    onOpen: (node: ProjectTreeNode) => void;
    onContextMenu?: (node: ProjectTreeNode, event: MouseEvent<HTMLButtonElement>) => void;
}

function artifactMark(kind: ArtifactKind): string {
    if (kind === 'PROJECT_DESCRIPTOR') return 'P';
    if (kind === 'METAMODEL' || kind === 'METAMODEL_DIAGRAM' || kind === 'GENERATED_ECORE') return 'MM';
    if (kind === 'MODEL' || kind === 'EDITOR_MODEL_SESSION' || kind === 'MODEL_DIAGRAM' || kind === 'EDITOR_SESSION_DIAGRAM') return 'M';
    if (kind.includes('VALIDATION') || kind === 'EXPRESSION_MODEL' || kind === 'ACTION_MODEL') return 'V';
    if (kind.includes('EVENT_FLOW')) return 'EF';
    if (kind.includes('EVENT')) return 'E';
    if (kind.includes('GAMIFICATION')) return 'G';
    if (kind === 'EDITOR_DEFINITION' || kind === 'REPRESENTATION_DEFINITION' || kind === 'PALETTE_DEFINITION' || kind === 'FORM_DEFINITION' || kind === 'NOTATION_DEFINITION') return 'UI';
    if (kind === 'INTEGRATION_PROFILE') return 'I';
    if (kind === 'GENERATED_DRAWIO' || kind === 'GENERATED_PLANTUML') return 'D';
    if (kind === 'GENERATED_JAVA') return 'J';
    if (kind === 'GENERATED_PYTHON') return 'Py';
    if (kind === 'GENERATED_IMAGE' || kind === 'ASSET') return 'A';
    if (kind === 'DOCUMENTATION') return 'Doc';
    if (kind === 'TEST_RESOURCE') return 'T';
    return 'F';
}

function artifactLabel(kind: ArtifactKind): string {
    return kind.toLowerCase().replaceAll('_', ' ');
}

function NodeIcon({ node }: { node: ProjectTreeNode }) {
    if (node.nodeType === 'PROJECT') return <span className="project-tree-icon project-tree-icon-project" title="Project"><ProjectIcon /></span>;
    if (node.nodeType === 'GROUP') return <span className="project-tree-icon project-tree-icon-group" title="Logical project group"><FolderTreeIcon /></span>;
    if (node.nodeType === 'DIRECTORY') return <span className="project-tree-icon project-tree-icon-folder" title="Folder"><FolderIcon /></span>;
    return <span className={`project-tree-icon project-tree-icon-file kind-${node.artifactKind.toLowerCase().replaceAll('_','-')}`} title={artifactLabel(node.artifactKind)}><FileIcon /><span className="project-tree-kind-mark">{artifactMark(node.artifactKind)}</span></span>;
}

export function ProjectTree(props: ProjectTreeProps) {
    const expansionKey = [props.root.id, ...props.root.children.map(child => child.id)].join('|');
    return <ProjectTreeSession key={expansionKey} {...props} />;
}

function ProjectTreeSession({ root, selectedPath, selectedPaths, changedPaths, onSelect, onOpen, onContextMenu }: ProjectTreeProps) {
    const initiallyExpanded = useMemo(() => new Set([root.id, ...root.children.map(child => child.id)]), [root]);
    const [expanded, setExpanded] = useState(initiallyExpanded);


    function toggle(node: ProjectTreeNode): void {
        if (node.children.length === 0) return;
        setExpanded(current => {
            const next = new Set(current);
            if (next.has(node.id)) next.delete(node.id); else next.add(node.id);
            return next;
        });
    }

    function renderNode(node: ProjectTreeNode, depth: number) {
        const hasChildren = node.children.length > 0;
        const isExpanded = expanded.has(node.id);
        const selected = selectedPaths !== undefined ? selectedPaths.has(node.relativePath) : node.relativePath === selectedPath && selectedPath !== '';
        const externallyChanged = changedPaths.has(node.relativePath);
        return (
            <li key={node.id}>
                <HydraButton
                    aria-selected={selected}
                    className={`project-tree-row${selected ? ' selected' : ''}${externallyChanged ? ' changed' : ''}`}
                    style={{ paddingLeft: `${8 + depth * 14}px` }}
                    title={`${node.artifactKind} · ${node.relativePath || node.name}`}
                    type="button"
                    onClick={event => {
                        const additive = event.ctrlKey || event.metaKey;
                        if (additive) onSelect(node, true); else onSelect(node);
                        // Compatibility contract: onSelect(node, additive || undefined);
                        if (hasChildren && !additive) toggle(node);
                    }}
                    onContextMenu={event => {
                        event.preventDefault();
                        onContextMenu?.(node, event);
                    }}
                    onDoubleClick={() => { if (node.nodeType === 'FILE') onOpen(node); }}
                >
                    <span className="project-tree-expander">{hasChildren ? (isExpanded ? '▾' : '▸') : ''}</span>
                    <NodeIcon node={node} />
                    <span className="project-tree-label">{node.name}</span>
                    {node.ownership === 'GENERATED' && <span className="project-tree-badge">gen</span>}
                    {externallyChanged && <span className="project-tree-change" aria-label="Changed externally">●</span>}
                </HydraButton>
                {hasChildren && isExpanded && <ul>{node.children.map(child => renderNode(child, depth + 1))}</ul>}
            </li>
        );
    }

    return <ul className="project-tree" aria-label="Project files" aria-multiselectable="true" role="tree">{renderNode(root, 0)}</ul>;
}
