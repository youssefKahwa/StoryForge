// Usage: node render.mjs <edl.json> <runDir> <out.mp4> [compositionId]
import { bundle } from '@remotion/bundler';
import { ensureBrowser, renderMedia, selectComposition } from '@remotion/renderer';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const [edlPath, runDir, outPath, compositionId = 'LongForm'] = process.argv.slice(2);
if (!edlPath || !runDir || !outPath) {
  console.error('Usage: node render.mjs <edl.json> <runDir> <out.mp4>');
  process.exit(2);
}

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(runDir);
const edl = JSON.parse(fs.readFileSync(edlPath, 'utf8'));
const concurrency = Number(process.env.RENDER_CONCURRENCY) || null;

const TYPES = { '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.wav': 'audio/wav', '.mp3': 'audio/mpeg' };

const server = http.createServer((req, res) => {
  const requested = decodeURIComponent(new URL(req.url, 'http://local').pathname);
  const file = path.normalize(path.join(root, requested));
  if (!file.startsWith(root + path.sep)) {
    res.writeHead(403).end();
    return;
  }
  fs.stat(file, (err, stat) => {
    if (err || !stat.isFile()) {
      res.writeHead(404).end();
      return;
    }
    const headers = {
      'Content-Type': TYPES[path.extname(file).toLowerCase()] || 'application/octet-stream',
      'Access-Control-Allow-Origin': '*',
      'Accept-Ranges': 'bytes',
    };
    const range = /bytes=(\d*)-(\d*)/.exec(req.headers.range || '');
    if (range) {
      const start = range[1] ? Number(range[1]) : 0;
      const end = range[2] ? Number(range[2]) : stat.size - 1;
      res.writeHead(206, { ...headers, 'Content-Range': `bytes ${start}-${end}/${stat.size}`, 'Content-Length': end - start + 1 });
      fs.createReadStream(file, { start, end }).pipe(res);
      return;
    }
    res.writeHead(200, { ...headers, 'Content-Length': stat.size });
    fs.createReadStream(file).pipe(res);
  });
});

await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
const assetBase = `http://127.0.0.1:${server.address().port}`;

try {
  await ensureBrowser();
  console.log('Bundling...');
  const serveUrl = await bundle({ entryPoint: path.join(here, 'src', 'index.jsx') });
  const inputProps = { edl, assetBase };
  const composition = await selectComposition({ serveUrl, id: compositionId, inputProps });
  console.log(`Rendering ${composition.durationInFrames} frames at ${composition.fps} fps`);
  let lastReported = -1;
  await renderMedia({
    composition,
    serveUrl,
    codec: 'h264',
    crf: 18,
    outputLocation: outPath,
    inputProps,
    concurrency,
    onProgress: ({ progress }) => {
      const pct = Math.floor(progress * 100);
      if (pct >= lastReported + 5) {
        lastReported = pct;
        console.log(`progress ${pct}%`);
      }
    },
  });
  console.log('Render done');
} catch (error) {
  console.error(error && error.stack ? error.stack : error);
  process.exitCode = 1;
} finally {
  server.close();
}
