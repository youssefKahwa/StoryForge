// Usage: node render.mjs <edl.json> <runDir> <out.mp4> [compositionId]
import { bundle } from '@remotion/bundler';
import { ensureBrowser, renderMedia, selectComposition } from '@remotion/renderer';
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const [edlPath, runDir, outPath, requestedId = 'LongForm'] = process.argv.slice(2);
if (!edlPath || !runDir || !outPath) {
  console.error('Usage: node render.mjs <edl.json> <runDir> <out.mp4>');
  process.exit(2);
}

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(runDir);
const edl = JSON.parse(fs.readFileSync(edlPath, 'utf8'));
const concurrency = Number(process.env.RENDER_CONCURRENCY) || null;
// GPU (ANGLE) is the fastest here (~12 frames/s vs ~9 default, ~1 software "swangle"); the orchestrator falls
// back to RENDER_GL=default if a GPU render crashes or stalls.
const glMode = process.env.RENDER_GL || 'angle';
const chromiumOptions = glMode === 'default' ? {} : { gl: glMode };
// The long video's soundtrack is one finished mix: every render tab downloading it (100+ MB) stalled the asset
// server, so the picture is rendered silent and the mix is added afterwards with FFmpeg (seconds, no re-encode).
// Fast lane: only these frame ranges ([[from, to (exclusive)], ...]) of the long video, back to back in one silent
// file (the "Designed" composition), film look added later by FFmpeg.
const ranges = process.env.RENDER_RANGES ? JSON.parse(fs.readFileSync(process.env.RENDER_RANGES, 'utf8')) : null;
const compositionId = ranges ? 'Designed' : requestedId;
const tracks = edl.audio || [];
const muxAfter = compositionId === 'LongForm' && tracks.length === 1 && !tracks[0].start && !tracks[0].gainDb;
const renderEdl = muxAfter ? { ...edl, audio: [] } : edl;
const progressScale = Number(process.env.RENDER_PROGRESS_SCALE || 1);
const videoOut = muxAfter ? outPath.replace(/\.mp4$/i, '') + '.video.mp4' : outPath;

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
  const inputProps = ranges ? { edl: { ...edl, audio: [] }, assetBase, ranges } : { edl: renderEdl, assetBase };
  const composition = await selectComposition({ serveUrl, id: compositionId, inputProps, chromiumOptions });
  console.log(`Rendering ${composition.durationInFrames} frames at ${composition.fps} fps`);
  let lastReported = -1;
  if (ranges) {
    const totalFrames = ranges.reduce((sum, [from, to]) => sum + (to - from), 0);
    await renderMedia({
      composition, serveUrl, codec: 'h264', crf: 16, outputLocation: outPath, inputProps, concurrency,
      chromiumOptions, muted: true, frameRange: [0, totalFrames - 1],
      timeoutInMilliseconds: Number(process.env.RENDER_TIMEOUT_MS || 120000),
      onProgress: ({ progress }) => {
        const pct = Math.floor(progress * 100 * progressScale);
        if (pct >= lastReported + 2) {
          lastReported = pct;
          console.log(`progress ${pct}%`);
        }
      },
    });
    console.log('Ranges done');
    process.exit(0);
  }
  await renderMedia({
    composition,
    serveUrl,
    codec: 'h264',
    crf: 18,
    outputLocation: videoOut,
    muted: muxAfter,
    inputProps,
    concurrency,
    chromiumOptions,
    // Slow machines (laptop on battery) can need much longer than the 30 s default per frame.
    timeoutInMilliseconds: Number(process.env.RENDER_TIMEOUT_MS || 120000),
    onProgress: ({ progress }) => {
      const pct = Math.floor(progress * 100);
      if (pct >= lastReported + 5) {
        lastReported = pct;
        console.log(`progress ${pct}%`);
      }
    },
  });
  if (muxAfter) {
    console.log('Adding the soundtrack');
    const mux = spawnSync('ffmpeg', ['-y', '-loglevel', 'error', '-i', videoOut, '-i', path.join(root, tracks[0].file),
      '-map', '0:v:0', '-map', '1:a:0', '-c:v', 'copy', '-c:a', 'aac', '-b:a', '192k', '-shortest', outPath],
    { stdio: 'inherit' });
    if (mux.status !== 0) throw new Error('Adding the soundtrack failed');
    fs.unlinkSync(videoOut);
  }
  console.log('Render done');
} catch (error) {
  console.error(error && error.stack ? error.stack : error);
  process.exitCode = 1;
} finally {
  server.close();
}
