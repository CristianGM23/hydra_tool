# mxGraph / diagrams.net injectable target

HYDRA can compile a graphical editor definition into a portable JavaScript package for an mxGraph-compatible host and a self-contained diagrams.net/draw.io plug-in.

The package preserves the selected metamodel, editor definition, DIAGRAM representations, representation-specific mappings, palettes, creation tools, forms, visual styles and cell provenance. Canonical validation, quick-fix/action, operation, event, EventFlow and gamification documents are shipped alongside the graphical runtime when they are selected by the integration profile.

## Host contract

The generated runtime is disabled by default unless the integration profile explicitly opts in. A host enables the runtime only for the associated metamodel and may provide `HYDRA_MXGRAPH_HOST_BRIDGE` operations for validation, quick fixes, operations, events and gamification. Imported expressions are not evaluated with `eval` or `new Function`.

`PROJECT_IMAGE` mappings are resolved through `assetResolver(path)` or `HYDRA_MXGRAPH_HOST_BRIDGE.resolveAsset(path)`. Data URIs are escaped/wrapped so mxGraph's semicolon-separated style syntax cannot corrupt an embedded image.

## diagrams.net / draw.io

The generated `drawio/hydra-drawio-plugin.js` registers activation, validation, representation and behavior actions through `Draw.loadPlugin`. One dynamic sidebar palette is registered for every DIAGRAM representation. The package also contains one custom `.mxlibrary`-compatible XML library per DIAGRAM representation plus a combined library, including node and edge creation tools.

## Generic mxGraph applications

Applications that embed a compatible graph instance can load `src/hydra-mxgraph-runtime.js` directly. The runtime accepts a graph through `install(graph)` or `create({graph})`, exposes the available representations and palette entries, and preserves HYDRA identifiers on generated cells so the host can retain traceability and round-trip context.
