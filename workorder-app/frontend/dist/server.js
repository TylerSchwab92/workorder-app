"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
const http = __importStar(require("node:http"));
const fs = __importStar(require("node:fs"));
const path = __importStar(require("node:path"));
/**
 * Serves the front-end files (index.html, app.js, styles.css).
 * The one thing this server does beyond "serve files" is inject the
 * API's URL into index.html (from the API_BASE_URL env var), so the
 * browser script knows where to send its requests.
 */
const PORT = Number(process.env.PORT ?? 3000);
const API_BASE_URL = process.env.API_BASE_URL ?? 'http://localhost:8080';
const PUBLIC_DIR = path.join(__dirname, '..', 'public');
const CONTENT_TYPES = {
    '.html': 'text/html; charset=utf-8',
    '.js': 'text/javascript; charset=utf-8',
    '.css': 'text/css; charset=utf-8',
};
function sendNotFound(res) {
    res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('Not found');
}
function serveIndex(res) {
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
function serveStaticAsset(urlPath, res) {
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
