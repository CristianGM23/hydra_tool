# HYDRA managed full transformation toolchains

HYDRA 2.0 closes the managed native transformation integration for ATL, ETL, Acceleo, EGL and Xtend without redistributing third-party engines. The core catalog always contains checksum-verifiable bridge packages. Java, Python and Go include their independent runtimes. The five JVM ecosystems use a trusted `JVM_CLASSPATH` backend and remain fail-closed until an administrator assembles a full package containing the engine libraries and the corresponding HYDRA driver JAR.

## Runtime contract

A full JVM package must contain `engine/classpath.txt`, the language driver named by `native.runtime.file`, every dependency referenced by the classpath manifest, and the exact `native.runtime.mainClass` declared by the profile. Paths are relative to the package, symbolic links are rejected, checksums cover every payload file, and browser requests can only select packages already present in the server-owned catalog.

The driver receives one argument: the HYDRA bridge request JSON path. It must execute only the exported native subset advertised by the selected language and write the normal HYDRA native result artifact expected by the parity service. Unsupported semantics must fail rather than fall back silently to the canonical runtime.

## Building full packages

Use `scripts/toolchains/assemble_full_transformation_toolchain.py` on the server. Example:

`python scripts/toolchains/assemble_full_transformation_toolchain.py --language atl --engine-dir /srv/hydra-engines/atl --toolchain-version <installed-version>`

Supported values are `atl`, `etl`, `acceleo`, `egl` and `xtend`. The supplied engine directory must already contain the HYDRA language driver JAR (`hydra-atl-runtime.jar`, `hydra-etl-runtime.jar`, `hydra-acceleo-runtime.jar`, `hydra-egl-runtime.jar` or `hydra-xtend-runtime.jar`) plus the legally obtained third-party dependencies. The assembler rejects symlinks, generates the classpath manifest, updates package metadata and recomputes complete SHA-256 coverage.

This means the HYDRA implementation is complete even when a deployment has not installed a proprietary/third-party runtime: diagnosis reports `TOOL_UNAVAILABLE`, and no capability is simulated or downgraded behind the user's back.
