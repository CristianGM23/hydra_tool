// Sirve frontend/dist y reenvía /api/* al backend REAL (equivalente a `vite preview` con el proxy de vite.config.ts, pero con puertos configurables).
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const dist = path.resolve(here, '..', 'dist');
const web = Number(process.env.HYDRA_E2E_WEB_PORT ?? 4183);
const api = Number(process.env.HYDRA_E2E_API_PORT ?? 18081);
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.mjs': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.json': 'application/json; charset=utf-8', '.svg': 'image/svg+xml', '.png': 'image/png', '.ico': 'image/x-icon', '.woff2': 'font/woff2', '.woff': 'font/woff', '.ttf': 'font/ttf', '.map': 'application/json' };
if (!fs.existsSync(path.join(dist, 'index.html'))) { console.error('Falta frontend/dist (npm run build)'); process.exit(2); }
http.createServer((req, res) => {
    if (req.url.startsWith('/api/') || req.url.startsWith('/actuator/')) {
        const proxy = http.request({ host: '127.0.0.1', port: api, path: req.url, method: req.method, headers: req.headers }, (up) => { res.writeHead(up.statusCode ?? 502, up.headers); up.pipe(res); });
        proxy.on('error', () => { res.writeHead(502); res.end('backend no disponible'); });
        req.pipe(proxy);
        return;
    }
    const clean = decodeURIComponent(req.url.split('?')[0]);
    let file = path.join(dist, clean === '/' ? 'index.html' : clean);
    if (!file.startsWith(dist) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) file = path.join(dist, 'index.html');
    res.writeHead(200, { 'content-type': types[path.extname(file)] ?? 'application/octet-stream' });
    fs.createReadStream(file).pipe(res);
}).listen(web, '127.0.0.1', () => console.log('[real-web] http://127.0.0.1:' + web + ' -> api ' + api));
