import React from 'react';
import { AbsoluteFill, Easing, interpolate, random, useCurrentFrame } from 'remotion';

/**
 * Transitions as the YouTube editors do them: hard cuts by default, and on the cuts that matter a whip, a zoom-through,
 * a white flash, a glitch, a film burn, a camera-shake impact or a dip to black. The orchestrator plans which one goes
 * where (and the matching sound), so every component here only draws it.
 */
export const OVERLAP_FRAMES = 10;
export const TRANSITION_FRAMES = 20;

const clamp = { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' };
const fast = { ...clamp, easing: Easing.out(Easing.cubic) };

/** Incoming side of a transition (first frames of a shot). */
export const enterStyle = (kind, frame) => {
  if (frame >= TRANSITION_FRAMES) return {};
  if (kind === 'whip') {
    return {
      transform: `translateX(${interpolate(frame, [0, 9], [100, 0], fast)}%)`,
      filter: `blur(${interpolate(frame, [0, 9], [26, 0], fast)}px)`,
    };
  }
  if (kind === 'zoom') {
    return {
      opacity: interpolate(frame, [0, 6], [0, 1], clamp),
      transform: `scale(${interpolate(frame, [0, 10], [1.32, 1], fast)})`,
      filter: `blur(${interpolate(frame, [0, 10], [16, 0], fast)}px)`,
    };
  }
  if (kind === 'fade') return { opacity: interpolate(frame, [0, OVERLAP_FRAMES], [0, 1], clamp) };
  if (kind === 'impact') {
    const amplitude = interpolate(frame, [0, 12], [26, 0], clamp);
    const dx = (random(`shake-x-${frame}`) - 0.5) * 2 * amplitude;
    const dy = (random(`shake-y-${frame}`) - 0.5) * 2 * amplitude;
    return { transform: `translate(${dx}px, ${dy}px) scale(${interpolate(frame, [0, 8], [1.1, 1.0], fast)})` };
  }
  if (kind === 'glitch' && frame < 7) {
    const dx = (random(`glitch-${frame}`) - 0.5) * 9;
    return {
      transform: `translateX(${dx}%) skewX(${(random(`skew-${frame}`) - 0.5) * 8}deg)`,
      filter: frame % 2 === 0 ? 'hue-rotate(95deg) saturate(2.2) contrast(1.5)' : 'contrast(1.3) brightness(1.2)',
    };
  }
  if (kind === 'burn') {
    return { filter: `brightness(${interpolate(frame, [0, 4, 14], [2.4, 1.6, 1], clamp)}) saturate(1.1)` };
  }
  return {};
};

/** Outgoing side: the last OVERLAP_FRAMES of a shot, under the next one. */
export const exitStyle = (nextKind, frame, durationInFrames) => {
  const local = frame - (durationInFrames - OVERLAP_FRAMES);
  if (local < 0) return {};
  if (nextKind === 'whip') {
    return {
      transform: `translateX(${interpolate(local, [0, 9], [0, -100], fast)}%)`,
      filter: `blur(${interpolate(local, [0, 9], [0, 26], fast)}px)`,
    };
  }
  if (nextKind === 'zoom') {
    return { transform: `scale(${interpolate(local, [0, 10], [1, 1.45], fast)})` };
  }
  return {};
};

const Flash = ({ frame, peak = 0.7 }) => (
  <AbsoluteFill style={{
    backgroundColor: '#e3f1ff', mixBlendMode: 'screen', pointerEvents: 'none',
    opacity: interpolate(frame, [0, 2, 12], [0, peak, 0], clamp),
  }} />
);

const GlitchBars = ({ frame }) => (
  <AbsoluteFill style={{ pointerEvents: 'none', mixBlendMode: 'screen' }}>
    {[0, 1, 2, 3, 4, 5].map((i) => {
      const top = random(`bar-top-${frame}-${i}`) * 100;
      const height = 1 + random(`bar-h-${frame}-${i}`) * 7;
      const shift = (random(`bar-x-${frame}-${i}`) - 0.5) * 30;
      return (
        <div key={i} style={{
          position: 'absolute', left: `${shift}%`, top: `${top}%`, width: '100%', height: `${height}%`,
          background: i % 2 === 0 ? 'rgba(0,255,240,0.55)' : 'rgba(255,0,170,0.5)',
        }} />
      );
    })}
  </AbsoluteFill>
);

const LightLeak = ({ frame }) => {
  const x = interpolate(frame, [0, 18], [-30, 110], clamp);
  const opacity = interpolate(frame, [0, 3, 9, 18], [0, 0.95, 0.8, 0], clamp);
  return (
    <AbsoluteFill style={{
      pointerEvents: 'none', mixBlendMode: 'screen', opacity,
      background: `radial-gradient(ellipse 60% 120% at ${x}% 55%, rgba(255,236,190,1) 0%, rgba(255,150,40,0.85) 28%, `
        + `rgba(220,60,10,0.45) 55%, rgba(0,0,0,0) 80%)`,
    }} />
  );
};

/** Drawn over the incoming shot during its first frames. */
export const TransitionOverlay = ({ kind }) => {
  const frame = useCurrentFrame();
  if (frame >= TRANSITION_FRAMES) return null;
  if (kind === 'flash') return frame < 14 ? <Flash frame={frame} /> : null;
  if (kind === 'impact') return frame < 10 ? <Flash frame={frame} peak={0.35} /> : null;
  if (kind === 'glitch') return frame < 7 ? <GlitchBars frame={frame} /> : null;
  if (kind === 'burn') return <LightLeak frame={frame} />;
  if (kind === 'dip') {
    return <AbsoluteFill style={{ backgroundColor: '#000', opacity: interpolate(frame, [0, 4, 16], [1, 1, 0], clamp) }} />;
  }
  return null;
};

/**
 * Punch-in: from punch.at (seconds) the same picture is cropped tighter around (x, y) with a tiny camera flash,
 * the "frame switch" of current YouTube edits. shotStart is the shot's start in the same time base.
 */
export const PunchIn = ({ punch, shotStart, fps, children }) => {
  const frame = useCurrentFrame();
  if (!punch) return children;
  const at = Math.round((punch.at - shotStart) * fps);
  const local = frame - at;
  const on = local >= 0;
  const zoom = on ? punch.zoom * interpolate(local, [0, 120], [1, 1.05], clamp) : 1;
  return (
    <AbsoluteFill style={{ transform: `scale(${zoom})`, transformOrigin: `${punch.x * 100}% ${punch.y * 100}%` }}>
      {children}
      {on && local < 3 && (
        <AbsoluteFill style={{ backgroundColor: '#fff', opacity: local === 0 ? 0.3 : 0.12, pointerEvents: 'none' }} />
      )}
    </AbsoluteFill>
  );
};
