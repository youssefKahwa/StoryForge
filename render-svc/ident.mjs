// Usage: node ident.mjs <props.json> <assetDir> <out.mp4>
// Renders the channel intro or outro (ChannelIdent.jsx, composition "IdentDemo") silently; the orchestrator adds
// the voice, the sounds and the music. props: {kind, variant, durationInFrames, beats?}; assetDir holds logo.jpg.
import { bundle } from '@remotion/bundler';
import { ensureBrowser, renderMedia, selectComposition } from '@remotion/renderer';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const [propsPath, assetDir, outPath] = process.argv.slice(2);
if (!propsPath || !assetDir || !outPath) {
  console.error('Usage: node ident.mjs <props.json> <assetDir> <out.mp4>');
  process.exit(2);
}
const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(assetDir);
const server = http.createServer((req, res) => {
  const file = path.join(root, path.basename(decodeURIComponent(new URL(req.url, 'http://local').pathname)));
  if (!fs.existsSync(file)) {
    res.writeHead(404).end();
    return;
  }
  res.writeHead(200, { 'Content-Type': file.endsWith('.png') ? 'image/png' : 'image/jpeg', 'Access-Control-Allow-Origin': '*' });
  fs.createReadStream(file).pipe(res);
});
await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
const base = `http://127.0.0.1:${server.address().port}`;
try {
  await ensureBrowser();
  const props = JSON.parse(fs.readFileSync(propsPath, 'utf8'));
  const inputProps = { ...props, logo: `${base}/logo.jpg`, mouthPattern: '', mouth: [0], hostClip: '' };
  const glMode = process.env.RENDER_GL || 'angle';
  const chromiumOptions = glMode === 'default' ? {} : { gl: glMode };
  const serveUrl = await bundle({ entryPoint: path.join(here, 'src', 'index.jsx') });
  const composition = await selectComposition({ serveUrl, id: 'IdentDemo', inputProps, chromiumOptions });
  let lastReported = -1;
  await renderMedia({
    composition, serveUrl, codec: 'h264', crf: 16, outputLocation: outPath, inputProps, chromiumOptions, muted: true,
    concurrency: Number(process.env.RENDER_CONCURRENCY) || null,
    onProgress: ({ progress }) => {
      const pct = Math.floor(progress * 100);
      if (pct >= lastReported + 10) {
        lastReported = pct;
        console.log(`progress ${pct}%`);
      }
    },
  });
  console.log('Ident done');
} catch (error) {
  console.error(error && error.stack ? error.stack : error);
  process.exitCode = 1;
} finally {
  server.close();
}
