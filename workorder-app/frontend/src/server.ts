import * as http from 'node:http';
import * as fs from 'node:fs';
import * as path from 'node:path';

/**
 * Serves the front-end files (index.html, app.js, styles.css).
 * The one thing this server does beyond "serve files" is inject the
 * API's URL into index.html (from the API_BASE_URL env var), so the
 * browser script knows where to send its requests.
 */

const PORT = Number(process.env.PORT ?? 3000);
const API_BASE_URL = process.env.API_BASE_URL ?? 'http://localhost:8080';
const PUBLIC_DIR = path.join(__dirname, '..', 'public');

const CONTENT_TYPES: Record<string, string> = {
    '.html': 'text/html; charset=utf-8',
    '.js': 'text/javascript; charset=utf-8',
    '.css': 'text/css; charset=utf-8',
};

function sendNotFound(res: http.ServerResponse): void {
    res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('Not found');
}

function serveIndex(res: http.ServerResponse): void {
    const indexPath = path.join(PUBLIC_DIR, 'index.html');
    fs.readFile(indexPath, 'utf8', (err, html) => {
        if (err) {
            res.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' });
            res.end('Failed to load index.html');
            return;
        }
        const configScript = `<script>window.__API_BASE_URL__ = ${JSON.stringify(API_BASE_URL)};</script>`;
        const injected = html.replace('</head>', `${configScript}\n</head>`);
        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
        res.end(injected);
    });
}

function serveStaticAsset(urlPath: string, res: http.ServerResponse): void {
    // Prevent path traversal outside PUBLIC_DIR.
    const safePath = path.normalize(urlPath).replace(/^(\.\.[/\\])+/, '');
    const filePath = path.join(PUBLIC_DIR, safePath);
    if (!filePath.startsWith(PUBLIC_DIR)) {
        sendNotFound(res);
        return;
    }
    fs.readFile(filePath, (err, data) => {
        if (err) {
            sendNotFound(res);
            return;
        }
        const ext = path.extname(filePath);
        const contentType = CONTENT_TYPES[ext] ?? 'application/octet-stream';
        res.writeHead(200, { 'Content-Type': contentType });
        res.end(data);
    });
}

const server = http.createServer((req, res) => {
    const url = req.url ?? '/';
    if (url === '/' || url === '/index.html') {
        serveIndex(res);
        return;
    }
    serveStaticAsset(url, res);
});

server.listen(PORT, () => {
    console.log(`Work Order UI listening on http://localhost:${PORT}`);
    console.log(`Pointing at API: ${API_BASE_URL}`);
});
