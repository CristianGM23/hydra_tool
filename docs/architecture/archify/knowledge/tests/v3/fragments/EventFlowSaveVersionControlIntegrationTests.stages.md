EventFlowSaveVersionControlIntegrationTests - stage mapping
- events_author_flow.a7 (save the flow and the diagram, version controlled): aFlowAndItsDiagramAreSavedWithVersionControl l.105-123 (flow versions equal the recomputed SHA-256, stale version and flow without end rejected with identical bytes) and l.130-137 (diagram saved with its own version, stale attempt rejected).
- events_author_flow.r1 (saved flow artifact): l.101-104 and l.110-114 read the raw flow JSON from disk (ids, steps, second flow) and the service reload.
