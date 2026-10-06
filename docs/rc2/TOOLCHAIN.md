# Toolchain hermético y descubrimiento

HYDRA genera bytecode Java 24 y admite JDK 24 o 25 para cualificación. El descubrimiento no depende de una ruta fija: inspecciona overrides explícitos, variables de entorno, PATH, `java.home`, registro de Windows y raíces configuradas por proveedor. Un candidato Java solo es JDK válido si incluye `java` y `javac`.

La política está en `config/toolchain-discovery-policy.json`. `verify_toolchain.py --strict` deja `target/diagnostics/toolchain-inventory.json` con rutas, versiones y hashes disponibles.
