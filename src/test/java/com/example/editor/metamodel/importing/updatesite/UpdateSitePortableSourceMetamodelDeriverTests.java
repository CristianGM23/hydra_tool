package com.example.editor.metamodel.importing.updatesite;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class UpdateSitePortableSourceMetamodelDeriverTests {
    private final UpdateSitePortableSourceMetamodelDeriver deriver = new UpdateSitePortableSourceMetamodelDeriver();

    @Test
    void recoversXcoreTypesRelationsEnumsAndInheritanceWithoutExecutingSource() {
        String source = """
                package demo
                abstract class Entity { attr String id }
                class Book extends Entity {
                  attr String title
                  ref Author author
                  contains Chapter[*] chapters
                }
                class Author { attr String name }
                class Chapter { attr String text }
                enum Status { DRAFT PUBLISHED }
                """;
        var model = deriver.derive(UpdateSiteArtifactKind.XCORE, source.getBytes(StandardCharsets.UTF_8), "xcore-demo", "model/demo.xcore");
        assertThat(model.classes()).extracting(value -> value.name()).contains("Entity", "Book", "Author", "Chapter");
        assertThat(model.enums()).extracting(value -> value.name()).contains("Status");
        assertThat(model.relations()).anyMatch(value -> value.name().equals("extends"));
        assertThat(model.relations()).anyMatch(value -> value.name().equals("chapters"));
        assertThat(model.annotations()).containsEntry("hydra.recovery.nonExecuting", "true");
    }

    @Test
    void recoversTypeScriptInterfacesAndReferences() {
        String source = """
                export interface User { id: string; name?: string; }
                export interface Project { owner: User; members: User[]; title: string; }
                """;
        var model = deriver.derive(UpdateSiteArtifactKind.TYPESCRIPT_SOURCE, source.getBytes(StandardCharsets.UTF_8), "ts-demo", "src/model.ts");
        assertThat(model.classes()).extracting(value -> value.name()).contains("User", "Project");
        assertThat(model.relations()).extracting(value -> value.name()).contains("owner", "members");
    }

    @Test
    void recoversJavaScriptClassesFieldsAndMethodsWithoutExecution() {
        String source = """
                export class Person {
                    constructor() {
                        this.name = '';
                        this.active = true;
                    }
                    greet(prefix) {
                        return prefix + this.name;
                    }
                }
                """;
        var model = deriver.derive(UpdateSiteArtifactKind.JAVASCRIPT_SOURCE, source.getBytes(StandardCharsets.UTF_8), "js-demo", "src/model.js");
        assertThat(model.classes()).extracting(value -> value.name()).contains("Person");
        assertThat(model.classes().stream().filter(value -> value.name().equals("Person")).findFirst().orElseThrow().attributes())
                .extracting(value -> value.name()).contains("name", "active");
        assertThat(model.classes().stream().filter(value -> value.name().equals("Person")).findFirst().orElseThrow().operations())
                .extracting(value -> value.name()).contains("greet");
    }

    @Test
    void recoversGoStructsAndReferences() {
        String source = """
                package model
                type User struct {
                    ID string `json:\"id\"`
                    Name string `json:\"name\"`
                }
                type Project struct {
                    Owner *User `json:\"owner\"`
                    Members []User `json:\"members\"`
                }
                """;
        var model = deriver.derive(UpdateSiteArtifactKind.GO_SOURCE, source.getBytes(StandardCharsets.UTF_8), "go-demo", "model/model.go");
        assertThat(model.classes()).extracting(value -> value.name()).contains("User", "Project");
        assertThat(model.relations()).extracting(value -> value.name()).contains("owner", "members");
    }
}
