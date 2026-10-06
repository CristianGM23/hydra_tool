import com.example.editor.io.AtomicFiles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class AtomicFilesStressHarness {
    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory("hydra-atomic-files-");
        try {
            Path target=root.resolve("nested/state.bin");
            byte[] first="primera versión".getBytes(StandardCharsets.UTF_8);
            AtomicFiles.writeReplacing(target,first);
            require(Arrays.equals(first,Files.readAllBytes(target)),"initial atomic write");
            byte[] large=new byte[4*1024*1024];
            for(int i=0;i<large.length;i++)large[i]=(byte)(i*31);
            AtomicFiles.writeReplacing(target,large);
            require(Files.size(target)==large.length,"large replacement size");
            require(Arrays.equals(large,Files.readAllBytes(target)),"large replacement content");
            try(var stream=Files.walk(root)){
                long temporaries=stream.filter(path->path.getFileName().toString().endsWith(".tmp")).count();
                require(temporaries==0,"temporary cleanup");
            }
            Path source=root.resolve("source.txt");
            Path destination=root.resolve("destination.txt");
            Files.writeString(source,"new");
            AtomicFiles.moveNew(source,destination);
            require("new".equals(Files.readString(destination)),"new publication");
            Path copySource=root.resolve("copy-source.bin");Path copyTarget=root.resolve("copy-target.bin");
            Files.write(copySource,large);long copied=AtomicFiles.copyReplacingBounded(copySource,copyTarget,large.length);
            require(copied==large.length&&Arrays.equals(large,Files.readAllBytes(copyTarget)),"bounded streaming copy");
            boolean rejected=false;try{AtomicFiles.copyReplacingBounded(copySource,copyTarget,large.length-1L);}catch(java.io.IOException expected){rejected=true;}
            require(rejected,"oversized streaming copy rejected");
            require(Arrays.equals(large,Files.readAllBytes(copyTarget)),"failed bounded copy preserves previous target");
            System.out.println("ATOMIC_FILES_STRESS=PASS");
        } finally {
            if(Files.exists(root))try(var stream=Files.walk(root)){for(Path path:stream.sorted((a,b)->b.getNameCount()-a.getNameCount()).toList())Files.deleteIfExists(path);}
        }
    }
    private static void require(boolean condition,String label){if(!condition)throw new AssertionError(label);}
}
