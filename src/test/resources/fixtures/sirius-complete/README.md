# HYDRA Sirius Complete Fixture

Proyecto de prueba integral para validar la importación y reconstrucción de un proyecto Eclipse Sirius en HYDRA.

## Qué contiene

- **Metamodelo Ecore dinámico** (`hydratest.ecore`) y su `hydratest.genmodel`.
- **Viewpoint Specification Model Sirius** (`hydratest.odesign`).
- **Representación gráfica rica** con:
  - nodos cuadrados,
  - diamantes,
  - elipses,
  - notas,
  - gauge,
  - imagen SVG,
  - contenedor,
  - lista,
  - nodos de borde (ports),
  - aristas semánticas,
  - estilos condicionales,
  - capas,
  - filtro,
  - herramientas de creación, edición y borrado.
- **Tabla** y **árbol** Sirius para probar dialectos adicionales.
- **Validación nativa Sirius** y **EVL**.
- **Quick fixes EVL** y especificación de fixes nativos de Sirius.
- **Java service** auxiliar para probar detección de servicios AQL/Java.
- Modelos semánticos **válido** e **inválido**.
- `representations.aird` mínimo para probar reconocimiento de sesiones Sirius.
- Contrato `expected/hydra-import-expectations.json` con lo que HYDRA debería recuperar.

## Estructura

```text
HYDRA_Sirius_Complete_Fixture/
├─ com.hydra.sirius.fixture.metamodel/
│  └─ model/
│     ├─ hydratest.ecore
│     └─ hydratest.genmodel
├─ com.hydra.sirius.fixture.design/
│  ├─ description/hydratest.odesign
│  ├─ icons/datastore.svg
│  └─ src/com/hydra/sirius/fixture/design/Services.java
├─ com.hydra.sirius.fixture.validation/
│  └─ validation/hydratest.evl
├─ com.hydra.sirius.fixture.sample/
│  ├─ model/valid.hydraflow
│  ├─ model/invalid.hydraflow
│  └─ representations.aird
└─ expected/hydra-import-expectations.json
```

## Uso en Eclipse Sirius

1. Importar los cuatro proyectos mediante **File → Import → Existing Projects into Workspace**.
2. Abrir `com.hydra.sirius.fixture.design/description/hydratest.odesign` con el Sirius Specification Editor.
3. Convertir `com.hydra.sirius.fixture.sample` a **Modeling Project** si Eclipse no lo hace automáticamente.
4. Activar el viewpoint **HydraFlow**.
5. Abrir `model/valid.hydraflow` y crear una representación **Hydra Workflow Diagram**.
6. Ejecutar **Validate Diagram** para probar reglas nativas Sirius.
7. Ejecutar EVL sobre `valid.hydraflow` o `invalid.hydraflow` para probar validación externa y quick-fixes.

> El metamodelo se referencia también mediante `xsi:schemaLocation`, por lo que el fixture está pensado para poder analizarse de forma estática aun cuando no se haya generado código Java EMF.

## Casos de validación incluidos

### Sirius
- El nombre de cualquier `NamedElement` debe ser no vacío.
- Un `Task` no debe superar el 100 % de progreso.
- Una `Connection` no debe tener el mismo origen y destino.

### EVL
- Nombres obligatorios.
- Nombres únicos en el workflow.
- Duración de tarea positiva.
- Progreso entre 0 y 100.
- StartNode sin entradas.
- EndNode sin salidas.
- DecisionNode con expresión no vacía.
- Puertos con nombres únicos dentro de cada tarea.
- Conexiones sin self-loop.
- Conexiones con origen y destino definidos.
- Quick fixes para renombrado, progreso, duración y expresión de decisión.

## Qué debería comprobar HYDRA

El fichero `expected/hydra-import-expectations.json` define un inventario esperado de artefactos, mappings, estilos, reglas, herramientas y relaciones. Debe usarse como oracle de importación.
