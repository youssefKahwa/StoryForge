import React from 'react';
import { AbsoluteFill, random, useCurrentFrame } from 'remotion';

const TILE = 192;
let grainTile = null;

const makeGrainTile = () => {
  if (grainTile || typeof document === 'undefined') return grainTile;
  const canvas = document.createElement('canvas');
  canvas.width = TILE;
  canvas.height = TILE;
  const context = canvas.getContext('2d');
  const image = context.createImageData(TILE, TILE);
  for (let i = 0; i < TILE * TILE; i += 1) {
    const value = Math.floor(random(`grain-${i}`) * 255);
    image.data.set([value, value, value, 255], i * 4);
  }
  context.putImageData(image, 0, 0);
  grainTile = canvas.toDataURL('image/png');
  return grainTile;
};

export const FilmLook = () => {
  const frame = useCurrentFrame();
  const tile = makeGrainTile();
  const x = Math.floor(random(`gx-${frame}`) * TILE);
  const y = Math.floor(random(`gy-${frame}`) * TILE);
  return (
    <AbsoluteFill style={{ pointerEvents: 'none' }}>
      <AbsoluteFill style={{ background: 'radial-gradient(ellipse at center, rgba(0,0,0,0) 55%, rgba(0,0,0,0.55) 100%)' }} />
      {tile && (
        <AbsoluteFill
          style={{
            backgroundImage: `url(${tile})`,
            backgroundPosition: `${x}px ${y}px`,
            opacity: 0.06,
            mixBlendMode: 'overlay',
          }}
        />
      )}
    </AbsoluteFill>
  );
};
