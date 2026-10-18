// Usage: node still.mjs <props.json> <out.png>
// props.json: { "background": "<absolute image path or null>", "text": "...", "palette": [...] }
import { bundle } from '@remotion/bundler';
import { ensureBrowser, renderStill, selectComposition } from '@remotion/renderer';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const [propsPath, outPath] = process.argv.slice(2);
if (!propsPath || !outPath) {
  console.error('Usage: node still.mjs <props.json> <out.png>');
  process.exit(2);
}

const here = path.dirname(fileURLToPath(import.meta.url));
const props = JSON.parse(fs.readFileSync(propsPath, 'utf8'));
const backgroundFile = props.background ? path.resolve(props.background) : null;

const server = http.createServer((req, res) => {
  if (!backgroundFile || req.url !== '/background') {
    res.writeHead(404).end();
    return;
  }
  res.writeHead(200, { 'Content-Type': backgroundFile.endsWith('.jpg') ? 'image/jpeg' : 'image/png' });
  fs.createReadStream(backgroundFile).pipe(res);
});
await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));

try {
  await ensureBrowser();
  const serveUrl = await bundle({ entryPoint: path.join(here, 'src', 'index.jsx') });
  const inputProps = {
    ...props,
    background: backgroundFile ? `http://127.0.0.1:${server.address().port}/background` : null,
  };
  const composition = await selectComposition({ serveUrl, id: 'Thumbnail', inputProps });
  await renderStill({ composition, serveUrl, output: outPath, inputProps, imageFormat: 'png' });
  console.log('Still done');
} catch (error) {
  console.error(error && error.stack ? error.stack : error);
  process.exitCode = 1;
} finally {
  server.close();
}
