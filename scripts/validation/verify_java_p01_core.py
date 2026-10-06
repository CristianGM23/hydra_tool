from __future__ import annotations
from pathlib import Path
import shutil,subprocess,sys,textwrap
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'target/release/java-p01-smoke'
if OUT.exists(): shutil.rmtree(OUT)
OUT.mkdir(parents=True,exist_ok=True)
classes=[
 'src/main/java/com/example/editor/concurrent/BoundedPermitPool.java',
 'src/main/java/com/example/editor/concurrent/ResourceStripedLock.java',
 'src/main/java/com/example/editor/io/BoundedIo.java',
 'src/main/java/com/example/editor/storage/StorageDocument.java',
 'src/main/java/com/example/editor/storage/StorageVersionConflictException.java',
 'src/main/java/com/example/editor/storage/TransactionalStorage.java',
 'src/main/java/com/example/editor/storage/LocalTransactionalStorage.java',
]
harness=OUT/'HydraP01CoreSmoke.java'
harness.write_text(textwrap.dedent('''
    import java.io.*;
    import java.nio.file.*;
    import java.time.Duration;
    import java.util.Arrays;
    import com.example.editor.concurrent.*;
    import com.example.editor.io.*;
    import com.example.editor.storage.*;
    public class HydraP01CoreSmoke {
      static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
      public static void main(String[] args)throws Exception{
        BoundedPermitPool pool=new BoundedPermitPool(2);
        var p1=pool.tryAcquire(Duration.ZERO);var p2=pool.tryAcquire(Duration.ZERO);var p3=pool.tryAcquire(Duration.ZERO);
        require(p1!=null&&p2!=null&&p3==null,"permit capacity");require(pool.snapshot().inUse()==2,"permit snapshot");
        p1.close();p1.close();require(pool.availablePermits()==1,"permit idempotent close");p2.close();require(pool.availablePermits()==2,"permit release");
        ResourceStripedLock locks=new ResourceStripedLock(17);require(locks.stripeCount()==32,"stripe normalization");require(locks.lockFor("a")==locks.lockFor("a"),"stable resource lock");
        byte[] five=new byte[]{1,2,3,4,5};require(Arrays.equals(BoundedIo.read(new ByteArrayInputStream(five),5),five),"bounded read");
        boolean limited=false;try{BoundedIo.read(new ByteArrayInputStream(five),4);}catch(IOException expected){limited=true;}require(limited,"bounded overflow");
        Path root=Files.createTempDirectory("hydra-storage-smoke-");try{
          LocalTransactionalStorage storage=new LocalTransactionalStorage(root);
          StorageDocument first=storage.writeAtomically(Path.of("a/doc.bin"),five,null);require(storage.exists(Path.of("a/doc.bin")),"storage exists");
          StorageDocument read=storage.read(Path.of("a/doc.bin"),5).orElseThrow();require(Arrays.equals(read.content(),five)&&read.version().equals(first.version()),"storage read/version");
          byte[] six=new byte[]{1,2,3,4,5,6};StorageDocument second=storage.writeAtomically(Path.of("a/doc.bin"),six,first.version());
          boolean conflict=false;try{storage.writeAtomically(Path.of("a/doc.bin"),five,first.version());}catch(StorageVersionConflictException expected){conflict=true;}require(conflict,"optimistic conflict");
          require(storage.delete(Path.of("a/doc.bin"),second.version()),"versioned delete");
          boolean escape=false;try{storage.exists(Path.of("../escape"));}catch(IllegalArgumentException expected){escape=true;}require(escape,"path escape");
        } finally { try(var walk=Files.walk(root)){walk.sorted(java.util.Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});} }
        System.out.println("JAVA_P01_CORE=PASS");
      }
    }
'''),encoding='utf-8')
cmd=['javac','--release','21','-d',str(OUT)]+[str(ROOT/p) for p in classes]+[str(harness)]
compile_result=subprocess.run(cmd,cwd=ROOT,text=True,capture_output=True)
if compile_result.returncode!=0:
    print(compile_result.stdout);print(compile_result.stderr,file=sys.stderr);sys.exit(compile_result.returncode)
run=subprocess.run(['java','-cp',str(OUT),'HydraP01CoreSmoke'],cwd=ROOT,text=True,capture_output=True)
print(run.stdout,end='');print(run.stderr,end='',file=sys.stderr)
sys.exit(run.returncode)
