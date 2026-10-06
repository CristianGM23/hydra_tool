import com.example.editor.project.LocalProjectProperties;
import com.example.editor.project.ProjectException;
import com.example.editor.project.storage.ProjectFileOperations;
import com.example.editor.project.storage.ProjectZipService;
import java.nio.file.Files;
import java.nio.file.Path;

/** Stress ejecutable del ZIP portable: 3000 ficheros, round-trip, fallo transaccional y symlinks. */
public final class ProjectZipStressHarness {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        Path temporary=Files.createTempDirectory("hydra-project-zip-stress-");
        ProjectFileOperations operations=new ProjectFileOperations();
        try {
            LocalProjectProperties properties=new LocalProjectProperties();properties.setMaxArchiveEntries(6000);properties.setMaxArchiveSizeBytes(32L*1024*1024);properties.setMaxTreeDepth(24);properties.setMaxArchiveCompressionRatio(10000);
            ProjectZipService service=new ProjectZipService(properties,operations);Path source=temporary.resolve("source");Files.createDirectories(source.resolve("models/nested"));Files.writeString(source.resolve("modeling-project.json"),"{}");
            for(int index=0;index<3000;index++)Files.writeString(source.resolve("models/nested/f"+index+".txt"),"value-"+index);
            Files.createDirectories(source.resolve(".modeling/cache"));Files.writeString(source.resolve(".modeling/cache/ignored.bin"),"cache");
            long start=System.nanoTime();byte[] archive=service.exportProject(source);long exportMillis=(System.nanoTime()-start)/1_000_000L;Path destination=temporary.resolve("destination");start=System.nanoTime();service.importProject(archive,destination);long importMillis=(System.nanoTime()-start)/1_000_000L;
            require(Files.readString(destination.resolve("models/nested/f2999.txt")).equals("value-2999"),"round-trip content mismatch");require(!Files.exists(destination.resolve(".modeling/cache/ignored.bin")),"cache leaked into portable ZIP");
            LocalProjectProperties limited=new LocalProjectProperties();limited.setMaxArchiveEntries(10);limited.setMaxArchiveSizeBytes(32L*1024*1024);limited.setMaxArchiveCompressionRatio(10000);Path rejected=temporary.resolve("rejected");try{new ProjectZipService(limited,operations).importProject(archive,rejected);throw new AssertionError("entry budget was not enforced");}catch(ProjectException expected){require(expected.getReason()==ProjectException.Reason.FILE_TOO_LARGE,"unexpected entry-budget reason");}require(!Files.exists(rejected),"failed import published a partial destination");
            try(var stream=Files.list(temporary)){require(stream.noneMatch(path->path.getFileName().toString().startsWith(".rejected-import-")),"failed import leaked staging directory");}
            Path outside=temporary.resolve("outside.txt");Files.writeString(outside,"outside");Path link=source.resolve("models/link.txt");boolean symbolic=false;try{Files.createSymbolicLink(link,outside);symbolic=true;}catch(UnsupportedOperationException|java.io.IOException|SecurityException ignored){}
            if(symbolic){try{service.exportProject(source);throw new AssertionError("symbolic link was exported");}catch(ProjectException expected){require(expected.getReason()==ProjectException.Reason.INVALID_REQUEST,"unexpected symlink reason");}}
            System.out.println("PROJECT_ZIP_STRESS=PASS files=3000 archiveBytes="+archive.length+" exportMs="+exportMillis+" importMs="+importMillis);
        } finally {operations.deleteRecursively(temporary);}
    }
}
