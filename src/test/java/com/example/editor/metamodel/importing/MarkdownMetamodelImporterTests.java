package com.example.editor.metamodel.importing;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.importing.importers.MarkdownMetamodelImporter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba el Markdown documental generado.
 */
class MarkdownMetamodelImporterTests {

    @Test
    void importsDocumentedClassesEnumsDataTypesAndRelations() {
        String markdown = """
                # Metamodel: Company

                - Stable ID: `company`
                - Schema version: `1.0`
                - Namespace URI: `urn:company`
                - Namespace prefix: `company`

                ## Custom data types

                | Name | Implementation | Serializable |
                |---|---|---:|
                | Money | `com.example.Money` | true |

                ## Enumerations

                ### State

                | Literal | Value |
                |---|---:|
                | ACTIVE | 1 |

                ## Classes

                ### Company

                - Stable ID: `class-company`
                - Kind: CLASS

                #### Attributes

                No attributes.

                #### Relations

                | Name | Target | Multiplicity | Kind |
                |---|---|---:|---|
                | employees | Person | 0..* | CONTAINMENT |

                ### Person

                - Stable ID: `class-person`
                - Kind: CLASS

                #### Attributes

                | Name | Type | Multiplicity | Default |
                |---|---|---:|---|
                | name | String | 1 | — |

                #### Relations

                No outgoing relations.
                """;
        var output = new MarkdownMetamodelImporter().importMetamodel(new MetamodelImportSource("company.md", "text/markdown", markdown.getBytes(StandardCharsets.UTF_8)), MetamodelImportOptions.defaults());
        assertThat(output.metamodel().classes()).hasSize(2);
        assertThat(output.metamodel().enums()).hasSize(1);
        assertThat(output.metamodel().dataTypes()).hasSize(1);
        assertThat(output.metamodel().relations()).singleElement().extracting(item -> item.kind()).isEqualTo(MetaRelationKind.CONTAINMENT);
    }
}
