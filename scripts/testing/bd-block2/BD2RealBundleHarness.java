import com.example.editor.standalone.distribution.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Real generated fixture integration: readiness -> deployable web archive. */
public final class BD2RealBundleHarness {
    public static void main(String[] args) {
        if(args.length != 2) throw new IllegalArgumentException("Expected editor root and ZIP output");
        Path editor = Path.of(args[0]);
        Path archive = Path.of(args[1]);
        var profile = new StandaloneDistributionProfile("HYDRA Editor Fixture", "1.0.0",
                Set.of(StandaloneDistributionTarget.PWA), true, List.of(), "stable");
        var source = new StandaloneDistributionValidationReport(true, List.of(), Map.of());
        var readiness = new StandaloneProductionReadinessService().assess(editor, profile, source);
        if(!readiness.valid()) throw new IllegalStateException("Compiled fixture readiness failed: " + readiness.diagnostics());
        var packageResult = new StandaloneWebDeploymentPackager().packageWeb(editor, archive, profile, readiness);
        if(!packageResult.entries().contains("assets/model.mjs"))
            throw new IllegalStateException("Generated editor semantics were omitted from web bundle");
        System.out.println("BD2_GENERATED_EDITOR_WEB=PASS files=" + packageResult.entries().size()
                + " sha256=" + packageResult.sha256());
    }
}
