import type { ProjectSession, ProjectTreeNode } from './types';

export function emptyProjectSession(): ProjectSession {
    return { active: false, project: null, directory: '', openedAt: null, restoredTabs: [] };
}

export function findProjectTreeNode(root: ProjectTreeNode | null, path: string): ProjectTreeNode | null {
    if (root === null) return null;
    if (root.relativePath === path) return root;
    for (const child of root.children) {
        const found = findProjectTreeNode(child, path);
        if (found !== null) return found;
    }
    return null;
}

export function projectParentPath(path: string): string {
    const separator = path.lastIndexOf('/');
    return separator < 0 ? '' : path.substring(0, separator);
}

export function projectFileName(path: string): string {
    const separator = path.lastIndexOf('/');
    return separator < 0 ? path : path.substring(separator + 1);
}

export function projectSelectionRoots(paths: Iterable<string>): string[] {
    const ordered = Array.from(new Set(Array.from(paths).filter(path => path !== ''))).sort((left, right) => left.length - right.length);
    return ordered.filter(path => !ordered.some(other => other !== path && path.startsWith(`${other}/`)));
}

export function uniqueProjectCopyTarget(currentTree: ProjectTreeNode | null, directory: string, source: string): string {
    const baseName = projectFileName(source);
    const join = (name: string) => directory === '' ? name : `${directory}/${name}`;
    let candidate = join(baseName);
    if (candidate !== source && findProjectTreeNode(currentTree, candidate) === null) return candidate;
    let suffix = 1;
    do { candidate = join(`${baseName}.copy${suffix === 1 ? '' : suffix}`); suffix += 1; }
    while (findProjectTreeNode(currentTree, candidate) !== null);
    return candidate;
}
