package com.example.editor.codegen;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import com.example.editor.definition.CodeGenerationFeatureOptions;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.validation.model.CanonicalModelDocument;
import tools.jackson.databind.json.JsonMapper;

/** Renderer de lenguajes fuente enriquecibles mediante capacidades opcionales y tests portables. */
@Component
public class SourceModelCodeGenerationRenderer implements ModelCodeGenerationRenderer {
    private static final Set<String> TARGETS = Set.of("java", "typescript", "javascript", "go", "python");
    private static final Set<ModelCodeGenerationFeature> FEATURES = Set.of(ModelCodeGenerationFeature.values());
    private final ModelCodeGenerationSupport support;

    /** Descripción: Inicializa el renderer de código fuente con el mapper de producción. Entradas: mapper JSON. Salida: renderer listo. */
    /** Descripción: Inicializa una instancia de SourceModelCodeGenerationRenderer con sus dependencias y datos de entrada. Entradas: mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public SourceModelCodeGenerationRenderer(JsonMapper mapper) { this.support = new ModelCodeGenerationSupport(mapper); }

    /** Descripción: Expone los lenguajes fuente enriquecidos atendidos por este renderer. Entradas: ninguna. Salida: conjunto estable de destinos. */
    /** Descripción: Ejecuta la operación «targetIds» correspondiente a la responsabilidad de SourceModelCodeGenerationRenderer. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Set<String> targetIds() { return TARGETS; }

    /** Descripción: Expone las capacidades opcionales soportadas por los lenguajes fuente incorporados. Entradas: destino. Salida: tests, accessors, comparación, string e igualdad. */
    /** Descripción: Ejecuta la operación «supportedFeatures» correspondiente a la responsabilidad de SourceModelCodeGenerationRenderer. Entradas: targetId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ @Override public Set<ModelCodeGenerationFeature> supportedFeatures(String targetId) { requireTarget(targetId); return FEATURES; }

    /** Descripción: Genera código fuente y, opcionalmente, un paquete reproducible con pruebas. Entradas: destino, descriptor, metamodelo, modelo y opciones. Salida: fuente o ZIP. */
    /** Descripción: Crea o prepara el resultado de «generate» dentro de SourceModelCodeGenerationRenderer. Entradas: targetId: identificador que delimita el recurso objetivo; target: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; model: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ @Override public GeneratedModelArtifact generate(String targetId, ModelCodeGenerationTarget target, MetaModel metamodel,
            CanonicalModelDocument model, ModelCodeGenerationOptions options) {
        requireTarget(targetId);
        String source = switch(targetId) {
            case "java" -> javaSource(metamodel, model, options);
            case "typescript" -> typeScript(model, options);
            case "javascript" -> javaScript(model, options);
            case "go" -> goSource(model, options);
            case "python" -> python(model, options);
            default -> throw new IllegalArgumentException("Unsupported source target: " + targetId);
        };
        String base = support.safeFile(options.artifactName().isBlank() ? model.name() : options.artifactName());
        if(options.features().generateTests()) return sourceBundle(targetId, options, base, source);
        return new GeneratedModelArtifact(base + target.extension(), target.mediaType(), source.getBytes(StandardCharsets.UTF_8));
    }

    /** Descripción: Genera Java autocontenido con clases Element y Relation y helpers según el perfil. Entradas: metamodelo, modelo y opciones. Salida: fuente Java. */
    /** Descripción: Ejecuta la operación «javaSource» correspondiente a la responsabilidad de SourceModelCodeGenerationRenderer. Entradas: metamodel: dato de entrada necesario para la operación; model: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ private String javaSource(MetaModel metamodel, CanonicalModelDocument model, ModelCodeGenerationOptions options) {
        CodeGenerationFeatureOptions features = options.features(); String pkg = options.namespace().replaceAll("[^A-Za-z0-9_.]", "_"); String type = support.javaType(options.artifactName());
        String data = model.elements().stream().map(element -> "new Element(\"" + support.escape(element.id()) + "\", \"" + support.escape(support.className(metamodel, element.classId())) + "\", attributes(" + javaAttributeArguments(element.attributes()) + "))").collect(Collectors.joining(",\n            "));
        String relations = model.relations().stream().map(relation -> "new Relation(\"" + support.escape(relation.relationId()) + "\", \"" + support.escape(relation.sourceElementId()) + "\", \"" + support.escape(relation.targetElementId()) + "\")").collect(Collectors.joining(",\n            "));
        return "package " + pkg + ";\n\n" + support.traceComment("//", model, options) + "import java.util.*;\n\npublic final class " + type + " {\n" + javaElementClass(features) + javaRelationClass(features)
                + "    public static final List<Element> ELEMENTS = List.of(\n            " + data + "\n    );\n"
                + "    public static final List<Relation> RELATIONS = List.of(\n            " + relations + "\n    );\n"
                + "    private static Map<String,Object> attributes(Object... values){Map<String,Object> result=new LinkedHashMap<>();for(int i=0;i<values.length;i+=2)result.put((String)values[i],values[i+1]);return result;}\n"
                + "    private " + type + "(){}\n}\n";
    }

    /** Descripción: Construye la clase Element Java respetando accessors, comparación, representación textual e igualdad. Entradas: capacidades activadas. Salida: código de clase interna. */
    /** Descripción: Ejecuta la operación «javaElementClass» correspondiente a la responsabilidad de SourceModelCodeGenerationRenderer. Entradas: features: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String javaElementClass(CodeGenerationFeatureOptions features) {
        String visibility = features.generateGettersSetters() ? "private" : "public"; String comparable = features.generateComparison() ? " implements Comparable<Element>" : "";
        StringBuilder out = new StringBuilder("    public static final class Element").append(comparable).append(" {\n        ").append(visibility).append(" String id;\n        ").append(visibility).append(" String type;\n        ").append(visibility).append(" Map<String,Object> attributes;\n        public Element(String id,String type,Map<String,Object> attributes){this.id=id;this.type=type;this.attributes=attributes;}\n");
        if(features.generateGettersSetters()) out.append("        public String getId(){return id;} public void setId(String id){this.id=id;}\n        public String getType(){return type;} public void setType(String type){this.type=type;}\n        public Map<String,Object> getAttributes(){return attributes;} public void setAttributes(Map<String,Object> attributes){this.attributes=attributes;}\n");
        if(features.generateComparison()) out.append("        @Override public int compareTo(Element other){return Comparator.nullsFirst(String::compareTo).compare(id,other==null?null:other.id);}\n");
        if(features.generateStringRepresentation()) out.append("        @Override public String toString(){return \"Element{id='\"+id+\"', type='\"+type+\"', attributes=\"+attributes+\"}\";}\n");
        if(features.generateEquality()) out.append("        @Override public boolean equals(Object other){if(this==other)return true;if(!(other instanceof Element that))return false;return Objects.equals(id,that.id)&&Objects.equals(type,that.type)&&Objects.equals(attributes,that.attributes);}\n        @Override public int hashCode(){return Objects.hash(id,type,attributes);}\n");
        return out.append("    }\n").toString();
    }

    /** Descripción: Construye la clase Relation Java aplicando el mismo perfil de helpers. Entradas: capacidades activadas. Salida: código de clase interna. */
    /** Descripción: Ejecuta la operación «javaRelationClass» correspondiente a la responsabilidad de SourceModelCodeGenerationRenderer. Entradas: features: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String javaRelationClass(CodeGenerationFeatureOptions features) {
        String visibility = features.generateGettersSetters() ? "private" : "public"; String comparable = features.generateComparison() ? " implements Comparable<Relation>" : "";
        StringBuilder out = new StringBuilder("    public static final class Relation").append(comparable).append(" {\n        ").append(visibility).append(" String type;\n        ").append(visibility).append(" String source;\n        ").append(visibility).append(" String target;\n        public Relation(String type,String source,String target){this.type=type;this.source=source;this.target=target;}\n");
        if(features.generateGettersSetters()) out.append("        public String getType(){return type;} public void setType(String type){this.type=type;}\n        public String getSource(){return source;} public void setSource(String source){this.source=source;}\n        public String getTarget(){return target;} public void setTarget(String target){this.target=target;}\n");
        if(features.generateComparison()) out.append("        @Override public int compareTo(Relation other){if(other==null)return 1;int result=Comparator.nullsFirst(String::compareTo).compare(type,other.type);if(result!=0)return result;result=Comparator.nullsFirst(String::compareTo).compare(source,other.source);return result!=0?result:Comparator.nullsFirst(String::compareTo).compare(target,other.target);}\n");
        if(features.generateStringRepresentation()) out.append("        @Override public String toString(){return \"Relation{type='\"+type+\"', source='\"+source+\"', target='\"+target+\"'}\";}\n");
        if(features.generateEquality()) out.append("        @Override public boolean equals(Object other){if(this==other)return true;if(!(other instanceof Relation that))return false;return Objects.equals(type,that.type)&&Objects.equals(source,that.source)&&Objects.equals(target,that.target);}\n        @Override public int hashCode(){return Objects.hash(type,source,target);}\n");
        return out.append("    }\n").toString();
    }

    /** Descripción: Genera TypeScript estricto con helpers opcionales y sin dependencias de runtime. Entradas: modelo y opciones. Salida: módulo TypeScript. */
    /** Descripción: Ejecuta la operación «typeScript» correspondiente a la responsabilidad de SourceModelCodeGenerationRenderer. Entradas: model: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ private String typeScript(CanonicalModelDocument model, ModelCodeGenerationOptions options) {
        CodeGenerationFeatureOptions features = options.features(); StringBuilder helpers = new StringBuilder("export class GeneratedElement {\n  constructor(public id:string, public type:string, public attributes:Record<string,unknown>) {}\n");
        if(features.generateGettersSetters()) helpers.append("  getId(){return this.id;} setId(value:string){this.id=value;}\n  getType(){return this.type;} setType(value:string){this.type=value;}\n  getAttributes(){return this.attributes;} setAttributes(value:Record<string,unknown>){this.attributes=value;}\n");
        if(features.generateComparison()) helpers.append("  compareTo(other:GeneratedElement){return this.id.localeCompare(other.id);}\n");
        if(features.generateStringRepresentation()) helpers.append("  toString(){return `${this.type}(${this.id}) ${JSON.stringify(this.attributes)}`;}\n");
        if(features.generateEquality()) helpers.append("  equals(other:GeneratedElement){return this.id===other.id&&this.type===other.type&&JSON.stringify(this.attributes)===JSON.stringify(other.attributes);}\n");
        helpers.append("}\n"); return support.traceComment("//", model, options) + "export const namespace = " + support.javascriptString(options.namespace()) + ";\nexport const model = " + support.modelJson(model) + " as const;\nexport type GeneratedModel = typeof model;\n" + helpers;
    }

    /** Descripción: Genera JavaScript ES module con helpers opcionales. Entradas: modelo y opciones. Salida: módulo JavaScript. */
    /** Descripción: Ejecuta la operación «javaScript» correspondiente a la responsabilidad de SourceModelCodeGenerationRenderer. Entradas: model: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ private String javaScript(CanonicalModelDocument model, ModelCodeGenerationOptions options) {
        CodeGenerationFeatureOptions features = options.features(); StringBuilder helpers = new StringBuilder("export class GeneratedElement {\n  constructor(id,type,attributes){this.id=id;this.type=type;this.attributes=attributes;}\n");
        if(features.generateGettersSetters()) helpers.append("  getId(){return this.id;} setId(value){this.id=value;}\n  getType(){return this.type;} setType(value){this.type=value;}\n  getAttributes(){return this.attributes;} setAttributes(value){this.attributes=value;}\n");
        if(features.generateComparison()) helpers.append("  compareTo(other){return this.id.localeCompare(other.id);}\n");
        if(features.generateStringRepresentation()) helpers.append("  toString(){return `${this.type}(${this.id}) ${JSON.stringify(this.attributes)}`;}\n");
        if(features.generateEquality()) helpers.append("  equals(other){return !!other&&this.id===other.id&&this.type===other.type&&JSON.stringify(this.attributes)===JSON.stringify(other.attributes);}\n");
        helpers.append("}\n"); return support.traceComment("//", model, options) + "export const namespace = " + support.javascriptString(options.namespace()) + ";\nexport const model = " + support.modelJson(model) + ";\n" + helpers;
    }

    /** Descripción: Genera Go idiomático con helpers opcionales. Entradas: modelo y opciones. Salida: fuente Go. */
    /** Descripción: Ejecuta la operación «goSource» correspondiente a la responsabilidad de SourceModelCodeGenerationRenderer. Entradas: model: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ private String goSource(CanonicalModelDocument model, ModelCodeGenerationOptions options) {
        CodeGenerationFeatureOptions features = options.features(); StringBuilder out = new StringBuilder("package ").append(support.goIdentifier(support.lastSegment(options.namespace()))).append("\n\n").append(support.traceComment("//", model, options));
        java.util.List<String> imports = new java.util.ArrayList<>();
        if(features.generateStringRepresentation()) imports.add("fmt");
        if(features.generateEquality()) imports.add("reflect");
        if(!imports.isEmpty()) { out.append("import (\n"); for(String item : imports) out.append("\t\"").append(item).append("\"\n"); out.append(")\n\n"); }
        out.append("// HYDRA generated model.\ntype GeneratedElement struct { ID string; Type string; Attributes map[string]any }\n");
        if(features.generateGettersSetters()) out.append("func (e *GeneratedElement) GetID() string { return e.ID }\nfunc (e *GeneratedElement) SetID(v string) { e.ID = v }\nfunc (e *GeneratedElement) GetType() string { return e.Type }\nfunc (e *GeneratedElement) SetType(v string) { e.Type = v }\n");
        if(features.generateComparison()) out.append("func (e GeneratedElement) CompareTo(other GeneratedElement) int { if e.ID < other.ID { return -1 }; if e.ID > other.ID { return 1 }; return 0 }\n");
        if(features.generateStringRepresentation()) out.append("func (e GeneratedElement) String() string { return fmt.Sprintf(\"%s(%s) %v\", e.Type, e.ID, e.Attributes) }\n");
        if(features.generateEquality()) out.append("func (e GeneratedElement) Equal(other GeneratedElement) bool { return e.ID == other.ID && e.Type == other.Type && reflect.DeepEqual(e.Attributes, other.Attributes) }\n");
        return out.append("\nvar ModelJSON = ").append(support.goString(support.modelJson(model))).append("\n").toString();
    }

    /** Descripción: Genera Python con helpers convencionales y sin dependencias externas. Entradas: modelo y opciones. Salida: módulo Python. */
    /** Descripción: Ejecuta la operación «python» correspondiente a la responsabilidad de SourceModelCodeGenerationRenderer. Entradas: model: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ private String python(CanonicalModelDocument model, ModelCodeGenerationOptions options) {
        CodeGenerationFeatureOptions features = options.features(); StringBuilder out = new StringBuilder(support.traceComment("#", model, options)).append("NAMESPACE = ").append(support.pythonString(options.namespace())).append("\nMODEL = ").append(support.pythonJson(support.modelJson(model))).append("\n\nclass GeneratedElement:\n    def __init__(self, element_id, element_type, attributes):\n        self.id = element_id\n        self.type = element_type\n        self.attributes = attributes\n");
        if(features.generateGettersSetters()) out.append("    def get_id(self): return self.id\n    def set_id(self, value): self.id = value\n    def get_type(self): return self.type\n    def set_type(self, value): self.type = value\n    def get_attributes(self): return self.attributes\n    def set_attributes(self, value): self.attributes = value\n");
        if(features.generateComparison()) out.append("    def compare_to(self, other): return (self.id > other.id) - (self.id < other.id)\n");
        if(features.generateStringRepresentation()) out.append("    def __str__(self): return f\"{self.type}({self.id}) {self.attributes}\"\n");
        if(features.generateEquality()) out.append("    def __eq__(self, other): return isinstance(other, GeneratedElement) and self.id == other.id and self.type == other.type and self.attributes == other.attributes\n    def __hash__(self): return hash((self.id, self.type, __import__('json').dumps(self.attributes, sort_keys=True, default=str)))\n");
        return out.toString();
    }

    /** Descripción: Empaqueta fuente y prueba portable en rutas convencionales de cada lenguaje. Entradas: destino, opciones, nombre base y fuente. Salida: ZIP reproducible. */
    /** Descripción: Ejecuta la operación «sourceBundle» correspondiente a la responsabilidad de SourceModelCodeGenerationRenderer. Entradas: targetId: identificador que delimita el recurso objetivo; options: configuración que gobierna el comportamiento; base: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private GeneratedModelArtifact sourceBundle(String targetId, ModelCodeGenerationOptions options, String base, String source) {
        Map<String,byte[]> files = new LinkedHashMap<>(); String test = testSource(targetId, options, base);
        switch(targetId) {
            case "java" -> { String pkg = options.namespace().replace('.', '/').replaceAll("[^A-Za-z0-9_/]", "_"); String type = support.javaType(options.artifactName()); files.put("src/main/java/" + pkg + "/" + type + ".java", support.bytes(source)); files.put("src/test/java/" + pkg + "/" + type + "Test.java", support.bytes(test)); }
            case "typescript" -> { files.put("src/" + base + ".ts", support.bytes(source)); files.put("test/" + base + ".test.ts", support.bytes(test)); files.put("tsconfig.json", support.bytes("{\n  \"compilerOptions\": { \"strict\": true, \"target\": \"ES2020\", \"module\": \"CommonJS\", \"outDir\": \"dist\" },\n  \"include\": [\"src/**/*.ts\", \"test/**/*.ts\"]\n}\n")); }
            case "javascript" -> { files.put("src/" + base + ".js", support.bytes(source)); files.put("test/" + base + ".test.js", support.bytes(test)); files.put("package.json", support.bytes("{\n  \"type\": \"module\",\n  \"scripts\": { \"test\": \"node test/" + base + ".test.js\" }\n}\n")); }
            case "python" -> { String module = support.identifier(base).toLowerCase(java.util.Locale.ROOT); files.put(module + ".py", support.bytes(source)); files.put("test_" + module + ".py", support.bytes(testSource("python", options, module))); }
            case "go" -> { files.put("generated.go", support.bytes(source)); files.put("generated_test.go", support.bytes(test)); files.put("go.mod", support.bytes("module " + goModule(options.namespace()) + "\n\ngo 1.22\n")); }
            default -> throw new IllegalArgumentException("Tests are not implemented for code generation target: " + targetId);
        }
        return new GeneratedModelArtifact(base + "-" + targetId + ".zip", "application/zip", support.zip(files));
    }

    /** Descripción: Genera tests autocontenidos o basados exclusivamente en librerías estándar. Entradas: destino, opciones y nombre base. Salida: código de prueba. */
    /** Descripción: Ejecuta la operación «testSource» correspondiente a la responsabilidad de SourceModelCodeGenerationRenderer. Entradas: targetId: identificador que delimita el recurso objetivo; options: configuración que gobierna el comportamiento; base: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String testSource(String targetId, ModelCodeGenerationOptions options, String base) {
        String type = support.javaType(options.artifactName());
        return switch(targetId) {
            case "java" -> "package " + options.namespace().replaceAll("[^A-Za-z0-9_.]", "_") + ";\n\npublic final class " + type + "Test {\n    public static void main(String[] args) {\n        if (" + type + ".ELEMENTS == null || " + type + ".RELATIONS == null) throw new AssertionError(\"Generated model collections must exist\");\n    }\n}\n";
            case "typescript" -> "import { model } from '../src/" + base + "';\nif (!model || !Array.isArray(model.elements) || !Array.isArray(model.relations)) throw new Error('Generated model is invalid');\n";
            case "javascript" -> "import { model } from '../src/" + base + ".js';\nif (!model || !Array.isArray(model.elements) || !Array.isArray(model.relations)) throw new Error('Generated model is invalid');\n";
            case "python" -> "import unittest\nimport " + base.replace('-', '_') + " as generated\n\nclass GeneratedModelTest(unittest.TestCase):\n    def test_model_is_available(self):\n        self.assertIsInstance(generated.MODEL, dict)\n\nif __name__ == '__main__': unittest.main()\n";
            case "go" -> "package " + support.goIdentifier(support.lastSegment(options.namespace())) + "\n\nimport \"testing\"\n\nfunc TestGeneratedModel(t *testing.T) { if ModelJSON == \"\" { t.Fatal(\"generated model must not be empty\") } }\n";
            default -> throw new IllegalArgumentException("Tests are not implemented for code generation target: " + targetId);
        };
    }

    /** Descripción: Convierte atributos Java en pares clave/valor compatibles con valores nulos. Entradas: mapa de atributos. Salida: argumentos para el helper attributes. */
    /** Descripción: Ejecuta la operación «javaAttributeArguments» correspondiente a la responsabilidad de SourceModelCodeGenerationRenderer. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String javaAttributeArguments(Map<String,Object> values) { return values.entrySet().stream().map(entry -> "\"" + support.escape(entry.getKey()) + "\", " + support.javaLiteral(entry.getValue())).collect(Collectors.joining(", ")); }

    /** Descripción: Convierte un namespace en ruta de módulo Go portable. Entradas: namespace. Salida: ruta segura de módulo. */
    /** Descripción: Ejecuta la operación «goModule» correspondiente a la responsabilidad de SourceModelCodeGenerationRenderer. Entradas: namespace: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String goModule(String namespace) { String value = namespace.replace('.', '/').replaceAll("[^A-Za-z0-9_./-]", "-").replaceAll("/+", "/"); return value.isBlank() ? "generated/model" : value; }

    /** Descripción: Rechaza identificadores ajenos al renderer de código fuente. Entradas: destino. Salida: no devuelve valor; valida el contrato. */
    /** Descripción: Valida las condiciones necesarias para «requireTarget» y rechaza entradas no válidas. Entradas: targetId: identificador que delimita el recurso objetivo. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void requireTarget(String targetId) { if(!TARGETS.contains(targetId)) throw new IllegalArgumentException("Source renderer does not support target: " + targetId); }
}
