// Arranca el BACKEND REAL de HYDRA para los specs de e2e/real (sin page.route ni mocks).
// Requisito previo: el runner V3 deja el classpath en target/archify-v3/e2e/classpath.txt (mvnw compile dependency:build-classpath).
// Aislamiento: directorio de trabajo temporal propio (todas las rutas relativas ./data/... caen ahí), escucha solo en 127.0.0.1.
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, '..', '..');
const api = Number(process.env.HYDRA_E2E_API_PORT ?? 18081);
const cpFile = path.join(repo, 'target', 'archify-v3', 'e2e', 'classpath.txt');
if (!fs.existsSync(cpFile)) { console.error('Falta ' + cpFile + ' (ejecuta el runner V3, paso prepare maven-classpath)'); process.exit(2); }
const classes = path.join(repo, 'build', 'hydra-maven', 'classes');
const cp = [classes, ...fs.readFileSync(cpFile, 'utf8').trim().split(path.delimiter).filter(Boolean)].map((p) => p.replace(/\\/g, '/')).join(path.delimiter);
const work = process.env.HYDRA_E2E_WORKDIR ?? path.join(repo, 'target', 'archify-v3', 'e2e', 'work');
fs.rmSync(work, { recursive: true, force: true });
fs.mkdirSync(work, { recursive: true });
const argfile = path.join(path.dirname(cpFile), 'java.argfile');
fs.writeFileSync(argfile, '-cp\n"' + cp.replace(/\\/g, '/') + '"\n', 'utf8');
const java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
const args = ['@' + argfile, '-Dserver.port=' + api, '-Dserver.address=127.0.0.1', '-Dhydra.e2e.real-backend=true', 'com.example.editor.EditorApplication'];
console.log('[real-backend] ' + java + ' ' + args.join(' ').slice(0, 200) + ' cwd=' + work);
const child = spawn(java, args, { cwd: work, stdio: 'inherit', env: { ...process.env } });
child.on('exit', (code) => process.exit(code ?? 0));
for (const sig of ['SIGINT', 'SIGTERM']) process.on(sig, () => { child.kill(); });
process.on('exit', () => { try { child.kill(); } catch { /* ya terminado */ } });
