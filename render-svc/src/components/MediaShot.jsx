import React from 'react';
import { AbsoluteFill, Easing, Img, OffthreadVideo, interpolate, useCurrentFrame } from 'remotion';
import { loadFont } from '@remotion/google-fonts/Inter';
import { KenBurnsImage } from './KenBurnsImage';

const { fontFamily } = loadFont('normal', { weights: ['500'], subsets: ['latin'] });

const Credit = ({ text }) => (text ? (
  <div style={{
    position: 'absolute', top: 34, right: 44, fontFamily, fontSize: 18, color: 'rgba(245,240,230,0.78)',
    textShadow: '0 1px 6px rgba(0,0,0,0.9)', letterSpacing: '0.04em',
  }}>
    {text}
  </div>
) : null);

const tilt = (id) => {
  let hash = 0;
  for (let i = 0; i < id.length; i += 1) hash = (hash * 31 + id.charCodeAt(i)) % 997;
  return ((hash % 7) - 3) * 0.45;
};

/** A real photograph shown like a print on a table: blurred copy behind, white border, slow push. */
const ArchivalPhoto = ({ shot, src, durationInFrames }) => {
  const frame = useCurrentFrame();
  const range = [0, Math.max(1, durationInFrames)];
  const scale = interpolate(frame, range, [1.0, 1.06], { extrapolateRight: 'clamp', easing: Easing.inOut(Easing.quad) });
  return (
    <AbsoluteFill style={{ overflow: 'hidden', backgroundColor: '#0b0b0c' }}>
      <Img src={src} style={{
        position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover',
        filter: 'blur(28px) brightness(0.35) saturate(0.7)', transform: 'scale(1.15)',
      }} />
      <AbsoluteFill style={{ alignItems: 'center', justifyContent: 'center' }}>
        <Img src={src} style={{
          maxWidth: '74%', maxHeight: '80%', objectFit: 'contain', border: '12px solid #f1ebdf',
          boxShadow: '0 30px 80px rgba(0,0,0,0.75)', filter: 'saturate(0.85) contrast(1.05)',
          transform: `rotate(${tilt(shot.id)}deg) scale(${scale})`,
        }} />
      </AbsoluteFill>
      <Credit text={shot.credit} />
    </AbsoluteFill>
  );
};

/** Real stock footage, muted (the soundtrack is ours), graded to sit with the illustrations. */
const Footage = ({ shot, src, durationInFrames }) => {
  const frame = useCurrentFrame();
  const scale = interpolate(frame, [0, Math.max(1, durationInFrames)], [1.0, 1.05], { extrapolateRight: 'clamp' });
  return (
    <AbsoluteFill style={{ overflow: 'hidden' }}>
      <OffthreadVideo src={src} muted style={{
        width: '100%', height: '100%', objectFit: 'cover', transform: `scale(${scale})`,
        filter: 'saturate(0.82) contrast(1.06) brightness(0.96)',
      }} />
      <Credit text={shot.credit} />
    </AbsoluteFill>
  );
};

export const MediaShot = ({ shot, src, durationInFrames }) => {
  if (shot.media === 'footage') return <Footage shot={shot} src={src} durationInFrames={durationInFrames} />;
  if (shot.media === 'photo') return <ArchivalPhoto shot={shot} src={src} durationInFrames={durationInFrames} />;
  return <KenBurnsImage src={src} motion={shot.motion || 'push_in'} durationInFrames={durationInFrames} />;
};
