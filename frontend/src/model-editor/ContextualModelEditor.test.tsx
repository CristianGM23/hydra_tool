import { render, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ContextualModelEditor } from './ContextualModelEditor';
import { listEditorDefinitions, loadEditorRuntime } from '../editor-definition/api';
import { loadMetaModel } from '../metamodel-designer/api';

vi.mock('../editor-definition/api', () => ({
    listEditorDefinitions: vi.fn(),
    loadEditorRuntime: vi.fn()
}));
vi.mock('../metamodel-designer/api', () => ({ loadMetaModel: vi.fn() }));
vi.mock('../editor-definition/EditorRuntimeSession', () => ({ EditorRuntimeSession: () => <div>runtime session</div> }));

const summary = { id:'people-editor', metamodelId:'people', name:'People editor', representationCount:2, toolCount:3, formCount:1, etag:'editor-etag' };
const metamodel = { schemaVersion:'2.1' as const, id:'people', name:'People', classes:[], enums:[], dataTypes:[], relations:[], annotations:{} };

describe('ContextualModelEditor fault tolerance', () => {
    beforeEach(() => {
        vi.resetAllMocks();
    });

    it('reports the configured editor unavailable when runtime compilation cannot be loaded so the basic editor can take over', async () => {
        vi.mocked(listEditorDefinitions).mockResolvedValue([summary]);
        vi.mocked(loadMetaModel).mockResolvedValue({ metamodel, etag:'meta-etag' });
        vi.mocked(loadEditorRuntime).mockRejectedValue(new Error('runtime unavailable'));
        const onAvailabilityChange = vi.fn();
        const onError = vi.fn();

        const { container } = render(<ContextualModelEditor metamodelId="people" onAvailabilityChange={onAvailabilityChange} onError={onError} onMessage={vi.fn()} />);

        await waitFor(() => expect(onAvailabilityChange).toHaveBeenLastCalledWith(false));
        expect(onError).toHaveBeenCalledWith('runtime unavailable');
        expect(container.querySelector('.contextual-model-editor')).toBeNull();
    });

    it('does not load the metamodel or claim runtime availability when no configured editor exists', async () => {
        vi.mocked(listEditorDefinitions).mockResolvedValue([]);
        const onAvailabilityChange = vi.fn();

        render(<ContextualModelEditor metamodelId="people" onAvailabilityChange={onAvailabilityChange} onError={vi.fn()} onMessage={vi.fn()} />);

        await waitFor(() => expect(onAvailabilityChange).toHaveBeenLastCalledWith(false));
        expect(loadMetaModel).not.toHaveBeenCalled();
        expect(loadEditorRuntime).not.toHaveBeenCalled();
    });
});
