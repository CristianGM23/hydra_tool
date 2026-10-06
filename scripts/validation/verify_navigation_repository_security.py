from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
checks = []

def read(path: str) -> str:
    return (ROOT / path).read_text(encoding='utf-8')

def check(name: str, condition: bool) -> None:
    checks.append((name, bool(condition)))
    print(('PASS' if condition else 'FAIL') + ' - ' + name)

registry = read('frontend/src/application-shell/moduleRegistry.ts')
expected = ['METAMODEL','EDITORS','VALIDATIONS','QUICK_FIXES','EVENTS','EVENT_FLOWS','GAMIFICATION']
pos = [registry.find("id: '" + token + "'") for token in expected]
check('DSL workflow contains every authoring layer', all(value >= 0 for value in pos))
check('DSL workflow follows metamodel-editor-validation-repair-event-flow-gamification order', pos == sorted(pos))
check('repository connections are outside authoring layers', registry.find("id: 'REPOSITORIES'") > registry.find("id: 'GAMIFICATION'"))

rail = read('frontend/src/application-shell/HydraModuleRail.tsx')
check('active navigation layer explains its high-level purpose', 'hydra-module-link-description' in rail and 'module.description' in rail)
check('navigation groups separate DSL use delivery and operations', all(token in rail for token in ["id: 'DSL'", "id: 'USE'", "id: 'DELIVERY'", "id: 'OPERATIONS'"]))

shell_css = read('frontend/src/application-shell/application-shell.css')
check('home capability rows use stable aligned grid', 'grid-template-columns:minmax(0,1fr) 168px 112px' in shell_css and '.hydra-home-module-row' in shell_css)
check('compound home buttons permit wrapped text', '.hydra-home-module-row {\n    white-space: normal;' in shell_css)

guided_css = read('frontend/src/design-system/hydra-design-system-part-2.css')
check('guided workflow cards do not force one-line content', '.hydra-guided-stepper > button' in guided_css and 'white-space: normal' in guided_css and 'overflow-wrap: anywhere' in guided_css)

merge = read('frontend/src/update-site-import-center/UpdateSiteLayerMergePanel.tsx')
check('merge uses selectable HYDRA metamodels and editors', 'Destination metamodel' in merge and 'Destination editor' in merge and '<HydraSelect' in merge)
check('editor ETag is loaded and read-only by default', 'Current editor ETag' in merge and 'readOnly value={expectedEtag}' in merge and 'If-Match' in merge)
check('manual ETag change is isolated behind advanced disclosure', 'Advanced concurrency override' in merge and '<details className="update-site-merge-advanced">' in merge)

knowledge = read('frontend/src/update-site-import-center/UpdateSiteDiagnosticKnowledge.ts')
evidence = read('frontend/src/update-site-import-center/UpdateSiteEvidencePanel.tsx')
update_css = read('frontend/src/update-site-import-center/update-site-import-center-core-workspace.css')
check('confidence levels have explanatory knowledge', all(token in knowledge + evidence for token in ['Exact','Recovered','Derived','Inferred']))
check('update-site errors have explicit danger styling', bool(re.search(r'error[^\{]*\{[^\}]*--hydra-(?:color-)?danger|severity-error[^\{]*\{[^\}]*danger', update_css, re.I | re.S)))

kit = read('src/main/java/com/example/editor/remote/auth/RemoteOAuthRegistrationKit.java')
service = read('src/main/java/com/example/editor/remote/auth/RemoteOAuthProviderSetupService.java')
controller = read('src/main/java/com/example/editor/remote/auth/web/RemoteAuthController.java')
props = read('src/main/java/com/example/editor/remote/auth/RemoteAuthProperties.java')
app_props = read('src/main/resources/application.properties')
oauth_ui = read('frontend/src/remote-auth/OAuthProviderSetup.tsx')
check('registration kit exposes only public coordinates and neutral server credential metadata', 'serverCredentialEnvironmentVariable' in kit and 'serverCredentialRequired' in kit and 'clientSecretEnvironmentVariable' not in kit and 'String clientSecret,' not in kit and 'accessToken' not in kit and 'refreshToken' not in kit)
check('registration-kit API is no-store', 'registration-kit' in controller and 'cacheControl(CacheControl.noStore())' in controller)
check('browser setup accepts no client secret value', 'setClientSecret' not in oauth_ui and 'client-secret-' not in oauth_ui and 'type="password"' not in oauth_ui and 'containsSecrets: false' in oauth_ui)
check('public registration sheet warns against credentials', 'This sheet contains no password, access token, refresh token or client secret.' in oauth_ui)
check('GitHub defaults to browser authorization instead of device flow', 'hydra.remote-auth.providers.github.device-flow-preferred=${HYDRA_GITHUB_DEVICE_FLOW:false}' in app_props)
check('Microsoft defaults to browser authorization instead of device flow', 'hydra.remote-auth.providers.onedrive.device-flow-preferred=${HYDRA_MICROSOFT_DEVICE_FLOW:false}' in app_props)
check('OneDrive defaults to app-folder least privilege', 'Files.ReadWrite.AppFolder' in props)
check('Google Drive defaults to drive.file least privilege', 'https://www.googleapis.com/auth/drive.file' in props)
check('hosted GitHub Google and Microsoft secrets stay server-side', all(token in service for token in ['HYDRA_GITHUB_OAUTH_CLIENT_SECRET','HYDRA_GOOGLE_OAUTH_CLIENT_SECRET','HYDRA_MICROSOFT_OAUTH_CLIENT_SECRET']))
check('hosted OneDrive requires a server secret unless device or loopback flow applies', 'provider==RemoteStorageProviderType.ONEDRIVE&&!devicePreferred&&!loopback' in service)
check('provider console URLs are fixed by HYDRA', all(url in service for url in ['https://github.com/settings/developers','https://gitlab.com/-/profile/applications','https://entra.microsoft.com/','https://console.cloud.google.com/apis/credentials','https://www.dropbox.com/developers/apps']))
check('Dropbox guidance recommends app-folder access', 'Choose App Folder access unless the connection truly needs pre-existing files outside HYDRA' in oauth_ui)
check('GitHub guidance disables wildcard callbacks and reserves device flow for constrained use', 'keep GitHub callback wildcard matching disabled' in oauth_ui and 'headless or constrained deployments' in oauth_ui)

passed = sum(1 for _, ok in checks if ok)
print(f'NAVIGATION_REPOSITORY_SECURITY={passed}/{len(checks)}')
raise SystemExit(0 if passed == len(checks) else 1)
