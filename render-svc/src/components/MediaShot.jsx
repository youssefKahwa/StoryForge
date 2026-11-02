import React from 'react';
import { AbsoluteFill, Easing, Img, OffthreadVideo, interpolate, useCurrentFrame } from 'remotion';
import { KenBurnsImage } from './KenBurnsImage';
import { GlobeMap } from './GlobeMap';
import { body as fontFamily } from '../brand';

const Credit = ({ text }) => (text ? (
  <div style={{
    position: 'absolute', bottom: 30, left: 44, fontFamily, fontSize: 17, color: 'rgba(245,240,230,0.7)',
    textShadow: '0 1px 6px rgba(0,0,0,0.95)', letterSpacing: '0.04em',
  }}>
    {text}
  </div>
) : null);

const DIRECTIONS = [[-1.6, -0.8], [1.6, -0.6], [-1.2, 0.9], [1.4, 0.8]];

const direction = (id) => {
  let hash = 0;
  for (let i = 0; i < id.length; i += 1) hash = (hash * 31 + id.charCodeAt(i)) % 997;
  return DIRECTIONS[hash % DIRECTIONS.length];
};

/**
 * A real photograph, full screen: a blurred, darkened copy fills the frame and the photo itself sits on top
 * edge to edge (no print border), drifting like a camera move.
 */
const Photo = ({ shot, src, durationInFrames }) => {
  const frame = useCurrentFrame();
  const range = [0, Math.max(1, durationInFrames)];
  const ease = { extrapolateRight: 'clamp', easing: Easing.inOut(Easing.cubic) };
  const [dx, dy] = direction(shot.id);
  const scale = interpolate(frame, range, [1.1, 1.2], ease);
  return (
    <AbsoluteFill style={{ overflow: 'hidden', backgroundColor: '#0b0b0c' }}>
      <Img src={src} style={{
        position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover',
        filter: 'blur(30px) brightness(0.4) saturate(0.8)', transform: 'scale(1.2)',
      }} />
      <Img src={src} style={{
        position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'contain',
        filter: 'saturate(0.9) contrast(1.06)',
        transform: `translate(${interpolate(frame, range, [0, dx], ease)}%, ${interpolate(frame, range, [0, dy], ease)}%) `
          + `scale(${scale})`,
      }} />
      <Credit text={shot.credit} />
    </AbsoluteFill>
  );
};

/** Real stock footage, muted (the soundtrack is ours), graded to sit with the illustrations. */
const Footage = ({ shot, src, durationInFrames }) => {
  const frame = useCurrentFrame();
  const scale = interpolate(frame, [0, Math.max(1, durationInFrames)], [1.02, 1.1], { extrapolateRight: 'clamp' });
  return (
    <AbsoluteFill style={{ overflow: 'hidden' }}>
      <OffthreadVideo src={src} muted style={{
        width: '100%', height: '100%', objectFit: 'cover', transform: `scale(${scale})`,
        filter: 'saturate(0.85) contrast(1.08) brightness(0.95)',
      }} />
      <Credit text={shot.credit} />
    </AbsoluteFill>
  );
};

/** Fair-use trailer moment: full screen like a film channel, muted, slowly pushed in, credited in the corner. */
const FilmClip = ({ shot, src, durationInFrames }) => {
  const frame = useCurrentFrame();
  const scale = interpolate(frame, [0, Math.max(1, durationInFrames)], [1.0, 1.08], {
    extrapolateRight: 'clamp', easing: Easing.inOut(Easing.quad),
  });
  return (
    <AbsoluteFill style={{ overflow: 'hidden', backgroundColor: '#000' }}>
      <OffthreadVideo src={src} muted style={{
        width: '100%', height: '100%', objectFit: 'cover', transform: `scale(${scale})`,
        filter: 'contrast(1.08) saturate(0.95)',
      }} />
      <Credit text={shot.credit} />
    </AbsoluteFill>
  );
};

/** The channel's masked host: already cut and framed by the orchestrator, played full screen with a soft push. */
const Host = ({ src, durationInFrames }) => {
  const frame = useCurrentFrame();
  const scale = interpolate(frame, [0, Math.max(1, durationInFrames)], [1.0, 1.05], { extrapolateRight: 'clamp' });
  return (
    <AbsoluteFill style={{ overflow: 'hidden', backgroundColor: '#000' }}>
      <OffthreadVideo src={src} muted style={{
        width: '100%', height: '100%', objectFit: 'cover', transform: `scale(${scale})`,
        filter: 'contrast(1.05) saturate(1.02)',
      }} />
    </AbsoluteFill>
  );
};

export const MediaShot = ({ shot, src, durationInFrames }) => {
  if (shot.media === 'host') return <Host src={src} durationInFrames={durationInFrames} />;
  if (shot.media === 'map' && shot.map) return <GlobeMap map={shot.map} durationInFrames={durationInFrames} />;
  if (shot.media === 'clip') return <FilmClip shot={shot} src={src} durationInFrames={durationInFrames} />;
  if (shot.media === 'footage') return <Footage shot={shot} src={src} durationInFrames={durationInFrames} />;
  if (shot.media === 'photo') return <Photo shot={shot} src={src} durationInFrames={durationInFrames} />;
  return <KenBurnsImage src={src} motion={shot.motion || 'push_in'} durationInFrames={durationInFrames} />;
};
