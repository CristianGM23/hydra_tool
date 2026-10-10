import java.nio.file.*;
import java.util.*;
import com.example.editor.integration.*;
import com.example.editor.integration.verification.*;
import com.example.editor.integration.parity.*;
import com.example.editor.standalone.distribution.*;

public class BD1Harness {
  static int assertions=0;
  static void check(boolean ok,String what){if(!ok)throw new AssertionError(what);assertions++;System.out.println("PASS "+what);}
  public static void main(String[] args) throws Exception {
    var assessor=new NativeParityEvidenceAssessor();
    var step=new IntegrationVerificationStep("test-besser-package","validation", ".",List.of("python","-m","pytest","-q"),0,true);
    var observed=new TechnologyValidationStepResult(step.id(),"validation",TechnologyValidationStepStatus.PASSED,List.of("python","-m","pytest","-q"),0,10,"ok","",List.of());
    var nativeReport=new TechnologyProjectValidationReport(IntegrationTechnology.BESSER_SDK,TechnologyValidationStatus.PASSED,null,List.of(observed),Map.of("validationMode","NATIVE_REQUIRED"));
    check(assessor.assess(nativeReport,List.of(step),List.of(NativeParityCapability.GAMIFICATION)).certified(),"native behavioral proof certified");
    check(!assessor.assess(nativeReport,List.of(step),List.of(NativeParityCapability.ROUND_TRIP)).certified(),"unverified capabilities fail closed");
    var structuralReport=new TechnologyProjectValidationReport(IntegrationTechnology.BESSER_SDK,TechnologyValidationStatus.PASSED,null,List.of(observed),Map.of("validationMode","STRUCTURAL"));
    check(!assessor.assess(structuralReport,List.of(step),List.of(NativeParityCapability.GAMIFICATION)).certified(),"structural probe cannot certify runtime");
    var tampered=new TechnologyValidationStepResult(step.id(),"validation",TechnologyValidationStepStatus.PASSED,List.of("echo","-m","pytest","-q"),0,1,"ok","",List.of());
    var tamperedReport=new TechnologyProjectValidationReport(IntegrationTechnology.BESSER_SDK,TechnologyValidationStatus.PASSED,null,List.of(tampered),Map.of("validationMode","NATIVE_REQUIRED"));
    check(!assessor.assess(tamperedReport,List.of(step),List.of(NativeParityCapability.GAMIFICATION)).certified(),"command substitution rejected");
    var planner=new StandaloneDistributionPlanner();
    var desktop=new StandaloneDistributionProfile("Library Editor","1.2.0",Set.of(StandaloneDistributionTarget.TAURI_WINDOWS),false,List.of(".xmi"),"stable");
    var configs=planner.plan(desktop);
    check(configs.stream().anyMatch(x->x.path().equals("src-tauri/Cargo.toml")),"cargo manifest generated");
    check(configs.stream().anyMatch(x->x.path().equals("src-tauri/src/main.rs")),"tauri executable entry generated");
    check(configs.stream().anyMatch(x->x.path().equals("src-tauri/capabilities/default.json")),"minimal OS permissions generated");
    var pwa=new StandaloneDistributionProfile("Library Editor","1.2.0",Set.of(StandaloneDistributionTarget.PWA),true,List.of(".xmi"),"stable");
    Path root=Files.createTempDirectory("hydra-bd1-runtime-");
    var accepted=new StandaloneDistributionValidationReport(true,List.of(),Map.of());
    var readiness=new StandaloneProductionReadinessService();
    check(!readiness.assess(root,pwa,accepted).valid(),"source-only PWA refused as deployment-ready");
    Files.createDirectories(root.resolve("dist/assets"));
    Files.writeString(root.resolve("dist/index.html"),"<script src=\"assets/app.js\"></script>");
    Files.writeString(root.resolve("dist/manifest.webmanifest"),"{}");
    Files.writeString(root.resolve("dist/service-worker.js"),"const SHELL=[];caches.open('cache');fetch('x');");
    check(!readiness.assess(root,pwa,accepted).valid(),"missing built JS reported");
    Files.writeString(root.resolve("dist/assets/app.js"),"document.body.innerHTML='Hello';");
    check(readiness.assess(root,pwa,accepted).valid(),"PWA static assets complete");
    Files.writeString(root.resolve("dist/index.html"),"<script src=\"https://untrusted.invalid/evil.js\"></script>");
    check(!readiness.assess(root,pwa,accepted).valid(),"remote executable scripts blocked");
    check(!readiness.assess(root,desktop,accepted).valid(),"desktop project without Rust source blocked");
    System.out.println("BD1_JAVA_HARNESS=PASS cases="+assertions);
  }
}
