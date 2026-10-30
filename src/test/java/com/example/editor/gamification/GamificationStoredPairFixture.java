package com.example.editor.gamification;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.example.editor.config.StorageProperties;
import com.example.editor.event.EventModelApplicationService;
import com.example.editor.event.EventModelChecker;
import com.example.editor.event.EventModelProperties;
import com.example.editor.event.persistence.JsonEventBindingModelRepository;
import com.example.editor.event.persistence.JsonEventDefinitionModelRepository;
import com.example.editor.gamification.persistence.JsonGamificationBindingRepository;
import com.example.editor.gamification.persistence.JsonGamificationDefinitionRepository;
import com.example.editor.gamification.web.CanonicalGamificationController;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.canonical.CanonicalMetamodelProperties;
import com.example.editor.metamodel.canonical.JsonCanonicalMetamodelRepository;
import com.example.editor.metamodel.validation.NeutralMetamodelValidator;
import com.example.editor.semantic.expression.ExpressionTestFixtures;
import com.example.editor.validation.expression.ExpressionModelProperties;
import com.example.editor.validation.expression.persistence.JsonExpressionModelRepository;
import com.example.editor.web.ApiExceptionHandler;
import tools.jackson.databind.json.JsonMapper;

/**
 * A real HYDRA gamification stack (stores on disk, application service, controller and API error handler) over one temporary directory,
 * without mocks. Opening a second fixture on the same directory is a restart of HYDRA: the same files, new objects and no state in memory.
 */
final class GamificationStoredPairFixture {
    static final String ID = ExpressionTestFixtures.METAMODEL_ID;
    static final String ROUTE = "/api/canonical-metamodels/" + ID + "/gamification";
    final Path root;
    final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
    final GamificationProperties properties = new GamificationProperties();
    final GamificationCorruptDocumentRecovery recovery;
    final JsonGamificationDefinitionRepository definitionRepository;
    final JsonGamificationBindingRepository bindingRepository;
    final CanonicalMetamodelApplicationService metamodelService;
    final GamificationApplicationService service;
    final MockMvc mvc;

    private GamificationStoredPairFixture(Path root, boolean createMetamodel) {
        this.root = root;
        StorageProperties storage = new StorageProperties(root.resolve("metamodels"), root.resolve("models"),
                root.resolve("notations"), root.resolve("validations"), root.resolve("quickfixes"),
                root.resolve("generators"), root.resolve("assets"), root.resolve("exports"), true);
        metamodelService = new CanonicalMetamodelApplicationService(
                new JsonCanonicalMetamodelRepository(storage, new CanonicalMetamodelProperties(), mapper), new NeutralMetamodelValidator());
        if(createMetamodel) metamodelService.create(ExpressionTestFixtures.metamodel());
        EventModelProperties eventModelProperties = new EventModelProperties();
        eventModelProperties.setDirectory(root.resolve("events"));
        EventModelApplicationService eventModelService = new EventModelApplicationService(
                new JsonEventDefinitionModelRepository(eventModelProperties, mapper),
                new JsonEventBindingModelRepository(eventModelProperties, mapper),
                new JsonExpressionModelRepository(storage, new ExpressionModelProperties(), mapper),
                metamodelService, new EventModelChecker());
        properties.setDirectory(root.resolve("gamification"));
        recovery = new GamificationCorruptDocumentRecovery(properties);
        definitionRepository = new JsonGamificationDefinitionRepository(properties, mapper);
        bindingRepository = new JsonGamificationBindingRepository(properties, mapper);
        service = new GamificationApplicationService(definitionRepository, bindingRepository, metamodelService, eventModelService,
                new GamificationChecker(), recovery);
        mvc = MockMvcBuilders.standaloneSetup(new CanonicalGamificationController(service)).setControllerAdvice(new ApiExceptionHandler()).build();
    }

    static GamificationStoredPairFixture open(Path root) { return new GamificationStoredPairFixture(root, true); }
    /** The same data directory seen by a new HYDRA process: nothing survives in memory. */
    GamificationStoredPairFixture restarted() { return new GamificationStoredPairFixture(root, false); }

    Path gamification() { return root.resolve("gamification"); }
    Path definitionsFile() { return gamification().resolve(ID + ".gamification-definitions.json"); }
    Path bindingsFile() { return gamification().resolve(ID + ".gamification-bindings.json"); }
    Path quarantines() { return gamification().resolve(".invalid-recovery"); }

    GamificationSaveResponse createValid() { return service.create(ID, definitions("Gamification"), bindings("Rule")); }

    static GamificationDefinitionModel definitions(String name) { return definitions(ID, name); }
    static GamificationDefinitionModel definitions(String metamodelId, String name) {
        return new GamificationDefinitionModel("1.0", "gamification-definitions", metamodelId, name,
                List.of(new PointTypeDefinition("points:quality", "Quality", "", 0L)), List.of(), List.of(), List.of(), List.of(), Map.of());
    }
    static GamificationBindingModel bindings(String ruleName) { return bindings(ID, ruleName); }
    static GamificationBindingModel bindings(String metamodelId, String ruleName) {
        GamificationRule rule = new GamificationRule("rule:1", ruleName, "core.validation.violation-resolved", "", "EQUALS", null,
                "points:quality", 2, "", "", "", GamificationRepeatPolicy.ONCE_PER_EVENT, 0, 100, 10, GamificationEffectKind.AWARD, true);
        return new GamificationBindingModel("1.0", "gamification-bindings", metamodelId, "gamification-definitions", List.of(rule), Map.of());
    }

    /** Every file under the data directory with the SHA-256 of its bytes and its modification time: any move, rewrite or touch changes it. */
    Map<String, String> snapshot() throws IOException {
        Map<String, String> files = new TreeMap<>();
        try(var walk = Files.walk(root)) {
            for(Path file : walk.filter(Files::isRegularFile).toList())
                files.put(root.relativize(file).toString().replace('\\', '/'), sha256(file) + "@" + Files.getLastModifiedTime(file).toMillis());
        }
        return files;
    }

    static String sha256(Path file) throws IOException {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))); }
        catch(java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    static String etag(Path file) throws IOException { return "\"" + sha256(file) + "\""; }

    static boolean windows() { return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win"); }

    /** Opens a file the way an antivirus or another program does on Windows: nobody else may read, write, delete or rename it meanwhile. */
    static FileChannel holdExclusively(Path file) throws IOException {
        return FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE, com.sun.nio.file.ExtendedOpenOption.NOSHARE_READ,
                com.sun.nio.file.ExtendedOpenOption.NOSHARE_WRITE, com.sun.nio.file.ExtendedOpenOption.NOSHARE_DELETE);
    }
    /** Lets everybody read the file but nobody rename or delete it (a scanner holding it open). */
    static FileChannel holdAgainstRename(Path file) throws IOException {
        return FileChannel.open(file, StandardOpenOption.READ, com.sun.nio.file.ExtendedOpenOption.NOSHARE_DELETE);
    }
}
