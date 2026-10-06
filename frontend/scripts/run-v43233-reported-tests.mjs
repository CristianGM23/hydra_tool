import { spawnSync } from 'node:child_process';
const files = [
  'src/application-shell/HydraV43228ActionableErrorsContract.test.ts',
  'src/remote-auth/OAuthProviderSetup.test.tsx',
  'src/api/actionableError.test.ts',
  'src/api/inputErrorExplanation.test.ts',
  'src/accessibility/axeAccessibility.test.tsx',
];
const result = spawnSync(process.execPath, ['node_modules/vitest/vitest.mjs','run',...files], { stdio:'inherit', shell:false });
process.exit(result.status ?? 1);
