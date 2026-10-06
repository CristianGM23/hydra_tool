package com.example.editor.metamodel.importing.updatesite;

import java.util.Map;

/** Declarative fixture used to test an adapter without executing Eclipse code. */
public record UpdateSiteAdapterFixture(String extensionPoint, String elementName,
        Map<String, String> attributes) {
    public UpdateSiteAdapterFixture {
        extensionPoint = extensionPoint == null ? "" : extensionPoint.trim();
        elementName = elementName == null ? "" : elementName.trim();
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
