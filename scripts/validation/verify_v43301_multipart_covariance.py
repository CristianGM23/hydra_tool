from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[2]
service=ROOT/'src/main/java/com/example/editor/metamodel/importing/MetamodelBrowserDirectoryArchiveService.java'
test=ROOT/'src/test/java/com/example/editor/metamodel/importing/MetamodelBrowserDirectoryArchiveServiceTests.java'
s=service.read_text(encoding='utf-8')
t=test.read_text(encoding='utf-8')
checks=[
 ('archive accepts MultipartFile subtype lists', 'archive(List<? extends MultipartFile> files, List<String> paths)' in s),
 ('service consumes through MultipartFile interface', 'MultipartFile file = files.get(index);' in s),
 ('test retains inferred MockMultipartFile list', 'var files = List.of(file("a.ecore", "A"), file("b.ecore", "B"));' in t),
 ('test retains anonymous MockMultipartFile subtype', 'var deceptive = new MockMultipartFile' in t),
 ('test exercises singleton subtype list', 'service.archive(List.of(deceptive), List.of("model/large.ecore"))' in t),
 ('no unsafe cast inserted in test', '(MultipartFile)' not in t),
]
for name,ok in checks: print(('PASS' if ok else 'FAIL')+' - '+name)
failed=[name for name,ok in checks if not ok]
print(f'V4.32.101_MULTIPART_COVARIANCE={len(checks)-len(failed)}/{len(checks)}')
if failed: sys.exit(1)
