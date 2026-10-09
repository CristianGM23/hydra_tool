# Getting Started

### Reference Documentation
For further reference, please consider the following sections:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/4.1.0/maven-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/4.1.0/maven-plugin/build-image.html)
* [Spring Web](https://docs.spring.io/spring-boot/4.1.0/reference/web/servlet.html)
* [Spring Boot DevTools](https://docs.spring.io/spring-boot/4.1.0/reference/using/devtools.html)
* [Validation](https://docs.spring.io/spring-boot/4.1.0/reference/io/validation.html)
* [Spring Boot Actuator](https://docs.spring.io/spring-boot/4.1.0/reference/actuator/index.html)

### Guides
The following guides illustrate how to use some features concretely:

* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)
* [Validation](https://spring.io/guides/gs/validating-form-input/)
* [Building a RESTful Web Service with Spring Boot Actuator](https://spring.io/guides/gs/actuator-service/)

### Maven Parent overrides

Due to Maven's design, elements are inherited from the parent POM to the project POM.
While most of the inheritance is fine, it also inherits unwanted elements like `<license>` and `<developers>` from the parent.
To prevent this, the project POM contains empty overrides for these elements.
If you manually switch to a different parent and actually want the inheritance, you need to remove those overrides.


## HYDRA aggressive verification campaign

For release-level process, artifact, stress, round-trip and isolation verification on Windows/JDK 24 or 25, run:

```bat
VALIDAR_HYDRA_AGRESIVO_WINDOWS.cmd
```

The campaign requires an aggressive scenario for every registered HYDRA module, runs dedicated backend/frontend artifact and synergy tests, and then repeats the complete frontend suite with deterministic shuffled orders and different batch sizes/pools. See `docs/testing/AGGRESSIVE_PROCESS_CAMPAIGN.md` for acceptance criteria and reproducible seed configuration.
On Windows PowerShell, run frontend npm commands through `npm.cmd` (for example `npm.cmd run test:aggressive`) to avoid PowerShell execution-policy blocking of `npm.ps1`. The aggressive runner retries only proven Vitest worker-bootstrap failures with alternate pools in both the dedicated campaign and every shuffled batch. Pools that fail to bootstrap are marked degraded for the rest of the run, while assertion/test failures remain fatal and are never reclassified as infrastructure.

## R6C4 textual interaction parity

After applying the R6C4 Fix151-165 overlay on the validated Fix150 baseline, run on Windows:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\validation\validate_r6c4_fix151_165.ps1
```

The gate reuses the host-validated R6C3 baseline and qualifies the new neutral interaction layer, OpenAPI surface, focused JUnit tests and frontend build.

## HYDRA R6C4 Fix171 — Cualificación global

Desde la raíz del proyecto, tras aplicar el overlay completo con WinRAR:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\validation\validate_full_tree_fix171.ps1
```

El launcher valida el manifiesto sin borrar extras, verifica el tamaño lógico real de fuentes Java, TypeScript y CSS y ejecuta todos los controles del validador global anterior (incluida la suite Maven completa). Los detalles de un eventual fallo Maven quedan en `target/release/logs/final-tree/maven-full-verify.log`.
