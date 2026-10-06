# HYDRA validation strategy

The source tree is validated by current capabilities rather than snapshots of development versions.

Release validation covers:

- pre-GA source hygiene and absence of internal migration/LTS archaeology;
- frontend architecture and shared-control convergence;
- no visual HYDRA/metamodel version labels;
- Spanish/English i18n coverage;
- canonical API uniqueness and generated OpenAPI/client convergence;
- external-project deep recovery and safe archive limits;
- current invariants, security/process boundaries and concurrency budgets;
- TypeScript typecheck, ESLint, build, Vitest/Axe and Playwright;
- Maven clean verify on JDK 24 or 25, with Java 24 bytecode target;
- deterministic source, overlay and patch reproduction plus SHA-256 verification.
