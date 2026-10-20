import React from 'react';
import { AbsoluteFill, random, useCurrentFrame, useVideoConfig } from 'remotion';
import { loadFont as loadBungee } from '@remotion/google-fonts/Bungee';
import { loadFont as loadInter } from '@remotion/google-fonts/Inter';

/** BiPath channel look, sampled from the logo: white block letters, slate-blue 3D extrusion, evil-eye blues. */
export const BRAND = {
  white: '#f0f0f0',
  slate: '#304860',
  navy: '#100080',
  sky: '#70c0e0',
  night: '#0b0f17',
  black: '#000000',
};

export const headline = loadBungee('normal', { weights: ['400'], subsets: ['latin'] }).fontFamily;
export const body = loadInter('normal', { weights: ['500', '600', '700'], subsets: ['latin'] }).fontFamily;

/** The logo's block shadow: a solid slate extrusion down-right, then a soft drop shadow. */
export const extrude = (depth = 8, color = BRAND.slate) => {
  const steps = [];
  for (let i = 1; i <= depth; i += 1) steps.push(`${i}px ${i}px 0 ${color}`);
  steps.push(`${depth + 6}px ${depth + 10}px 26px rgba(0,0,0,0.55)`);
  return steps.join(', ');
};

/** Slow falling snow like the logo background; deterministic so every render is identical. */
export const Snow = ({ count = 70, opacity = 0.8 }) => {
  const frame = useCurrentFrame();
  const { width, height, fps } = useVideoConfig();
  const flakes = [];
  for (let i = 0; i < count; i += 1) {
    const size = 2 + random(`s${i}`) * 5;
    const speed = (18 + random(`v${i}`) * 40) * (size / 5);
    const x = random(`x${i}`) * width + Math.sin((frame / fps) * 0.6 + i) * 14;
    const y = (random(`y${i}`) * height + (frame / fps) * speed) % height;
    flakes.push(
      <div key={i} style={{
        position: 'absolute', left: x, top: y, width: size, height: size, borderRadius: size,
        backgroundColor: BRAND.white, opacity: (0.35 + random(`o${i}`) * 0.65) * opacity,
        filter: size > 5 ? 'blur(1.5px)' : 'none',
      }} />,
    );
  }
  return <AbsoluteFill style={{ pointerEvents: 'none' }}>{flakes}</AbsoluteFill>;
};
