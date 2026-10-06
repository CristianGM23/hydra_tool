# HYDRA licensing and provenance

The authoritative project license is `LICENSE` and third-party notices are recorded in `NOTICE` and the generated CycloneDX SBOM.

Release packaging is allowlist based and excludes build output, dependency caches, runtime data, IDE metadata and other non-source material. New third-party dependencies must be represented by the package lockfiles/build metadata and therefore appear in the release SBOM.
