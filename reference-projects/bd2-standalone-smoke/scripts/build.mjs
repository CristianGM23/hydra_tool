/** Deterministic, dependency-free build for a small generated-editor smoke. */
import { readFileSync, mkdirSync, writeFileSync, copyFileSync, rmSync } from 'node:fs';
import { resolve } from 'node:path';
const root = resolve(import.meta.dirname, '..');
rmSync(resolve(root, 'dist'), { recursive: true, force: true });
mkdirSync(resolve(root, 'dist/assets'), { recursive: true });
for (const file of ['index.html', 'src/app.js', 'src/model.mjs', 'public/manifest.webmanifest']) {
  const relative = file.startsWith('src/') ? 'assets/' + file.slice(4) : file.startsWith('public/') ? file.slice(7) : file;
  const source = resolve(root, file); const destination = resolve(root, 'dist', relative);
  writeFileSync(destination, readFileSync(source));
}
console.log('HYDRA_BD2_WEB_FIXTURE_BUILD=PASS');
