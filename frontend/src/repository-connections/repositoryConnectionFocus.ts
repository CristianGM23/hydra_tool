/**
 * Desplaza el formulario hasta el campo de una conexión de repositorio y le entrega el foco.
 *
 * @param field Nombre lógico del campo devuelto por la validación.
 * @returns No devuelve ningún valor; solo actualiza el foco de la interfaz.
 */
export function focusRepositoryField(field: string): void {
    const ids: Record<string, string> = {
        id: 'repository-profile-id', displayName: 'repository-display-name', repositoryUrl: 'repository-primary-url',
        owner: 'repository-owner', repository: 'repository-name', projectId: 'repository-project-id', branch: 'repository-branch',
        rootPath: 'repository-root-path', baseUrl: 'repository-base-url', apiVersion: 'repository-api-version', maximumArtifactBytes: 'repository-max-bytes',
    };
    const element = document.getElementById(ids[field] ?? field);
    element?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    if (element instanceof HTMLElement) element.focus({ preventScroll: true });
}
