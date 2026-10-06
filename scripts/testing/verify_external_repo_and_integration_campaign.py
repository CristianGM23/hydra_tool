from pathlib import Path
import re, sys
ROOT=Path(__file__).resolve().parents[2]
provider_enum=(ROOT/'src/main/java/com/example/editor/remote/storage/RemoteStorageProviderType.java').read_text()
provider_body=re.search(r'enum\s+RemoteStorageProviderType\s*\{([^}]*)\}',provider_enum,re.S).group(1)
providers=[x.strip() for x in provider_body.replace('\n',' ').split(',') if x.strip()]
remote='\n'.join((ROOT/p).read_text() for p in [
 'src/test/java/com/example/editor/remote/storage/AggressiveRemoteRepositoryRoundTripTests.java',
 'src/test/java/com/example/editor/remote/storage/AggressiveRemoteProviderProtocolTests.java',
 'src/test/java/com/example/editor/remote/storage/LiveRemoteRepositoryRoundTripTests.java'])
missing_providers=[] if 'RemoteStorageProviderType.values()' in remote else [p for p in providers if p not in remote]
tech_enum=(ROOT/'src/main/java/com/example/editor/integration/IntegrationTechnology.java').read_text()
tech_body=re.search(r'enum\s+IntegrationTechnology\s*\{([^}]*)\}',tech_enum,re.S).group(1)
technologies=[x.strip() for x in tech_body.replace('\n',' ').split(',') if x.strip()]
integration='\n'.join((ROOT/p).read_text() for p in [
 'src/test/java/com/example/editor/integration/verification/AggressiveGeneratedArtifactLifecycleTests.java',
 'src/test/java/com/example/editor/integration/verification/AggressiveSpecialIntegrationArtifactTests.java'])
missing_tech=[t for t in technologies if t not in integration]
if missing_providers or missing_tech:
 print('[HYDRA] External/integration aggressive coverage: FAIL')
 if missing_providers: print('Missing providers:',', '.join(missing_providers))
 if missing_tech: print('Missing technologies:',', '.join(missing_tech))
 sys.exit(1)
print(f'[HYDRA] External repository coverage: PASS ({len(providers)}/{len(providers)} providers)')
print(f'[HYDRA] Integration technology coverage: PASS ({len(technologies)}/{len(technologies)} technologies)')
