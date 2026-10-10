import com.example.editor.standalone.distribution.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

public final class BD2Harness {
    private static int checked;
    private static void ok(boolean pass, String why) { checked++; if(!pass) throw new AssertionError(why); }
    private static void rejects(Runnable task, String why) {
        boolean thrown = false;
        try { task.run(); } catch(IllegalArgumentException | IllegalStateException exception) { thrown = true; }
        ok(thrown, why);
    }
    private static void put(Path path, String value) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, value, StandardCharsets.UTF_8);
    }
    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("hydra-bd2-web-test-");
        try {
            Path editor = temp.resolve("editor");
            Path dist = editor.resolve("dist");
            put(dist.resolve("index.html"), "<!doctype html><script src='./assets/main.js'></script>");
            put(dist.resolve("assets/main.js"), "console.log('HYDRA');");
            put(dist.resolve("manifest.webmanifest"), "{\"name\":\"HYDRA Generated Editor\"}");
            put(dist.resolve("service-worker.js"), "const SHELL=[]; caches.open('test'); self.addEventListener('fetch',()=>{});");
            var profile = new StandaloneDistributionProfile("People", "2.4.0", Set.of(StandaloneDistributionTarget.PWA),
                    true, List.of(".xmi"), "stable");
            var report = new StandaloneDistributionValidationReport(true, List.of(), Map.of());
            StandaloneWebDeploymentPackager packager = new StandaloneWebDeploymentPackager();
            Path first = temp.resolve("first.zip");
            var a = packager.packageWeb(editor, first, profile, report);
            ok(Files.exists(a.archive()), "web ZIP created");
            ok(a.entries().contains("assets/main.js"), "built assets included");
            ok(!a.entries().contains("src-tauri/Cargo.toml"), "not a source package");
            try(ZipFile zip = new ZipFile(first.toFile())) {
                ok(zip.getEntry("hydra-release.sha256") != null, "asset hashes included");
                ok(zip.getEntry("hydra-deployment.json") != null, "deployment metadata included");
                String hashes = new String(zip.getInputStream(zip.getEntry("hydra-release.sha256")).readAllBytes(), StandardCharsets.UTF_8);
                ok(hashes.contains("assets/main.js"), "nested assets hashed");
                ok(zip.getEntry("index.html") != null, "site root deployable");
            }
            var b = packager.packageWeb(editor, temp.resolve("second.zip"), profile, report);
            ok(a.sha256().equals(b.sha256()), "bitwise reproducible web ZIP");
            rejects(() -> packager.packageWeb(editor, temp.resolve("bad.zip"), profile,
                    new StandaloneDistributionValidationReport(false, List.of(
                            new StandaloneDistributionDiagnostic("BLOCK", StandaloneDistributionDiagnosticSeverity.ERROR, "dist", "blocked")), Map.of())),
                    "reject invalid readiness");
            var desktopOnly = new StandaloneDistributionProfile("People", "2.4.0", Set.of(StandaloneDistributionTarget.TAURI_LINUX),
                    true, List.of(), "stable");
            rejects(() -> packager.packageWeb(editor, temp.resolve("bad.zip"), desktopOnly, report), "reject non web profile");
            rejects(() -> packager.packageWeb(editor, editor.resolve("bad.zip"), profile, report), "reject output inside input");
            put(dist.resolve(".env"), "SECRET=demo");
            rejects(() -> packager.packageWeb(editor, temp.resolve("bad.zip"), profile, report), "reject secret");
            Files.delete(dist.resolve(".env"));
            put(dist.resolve("hydra-release.sha256"), "spoof");
            rejects(() -> packager.packageWeb(editor, temp.resolve("bad.zip"), profile, report), "reject manifest substitution");
            Files.delete(dist.resolve("hydra-release.sha256"));
            Path link = dist.resolve("linked.js");
            try { Files.createSymbolicLink(link, dist.resolve("assets/main.js"));
                  rejects(() -> packager.packageWeb(editor, temp.resolve("bad.zip"), profile, report), "reject symlink");
                  Files.delete(link);
            } catch(UnsupportedOperationException | java.nio.file.FileSystemException ignored) {
                System.out.println("SYMLINK_CASE=UNAVAILABLE on this filesystem");
            }
            System.out.println("BD2_WEB_JAVA=PASS cases=" + checked);
        } finally {
            try(var walk = Files.walk(temp)) {
                for(Path entry : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(entry);
            }
        }
    }
}
