import React from 'react';
import { AbsoluteFill, Easing, Img, interpolate, spring, useCurrentFrame, useVideoConfig } from 'remotion';
import { BRAND } from '../brand';
import { LogoMouth } from './LogoMouth';

const SIZE = 240;
const RING = 18;
const clamp = { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' };

/**
 * The channel's talking logo in a round bubble, bottom right; the mouth frame follows the narration loudness.
 * It pops in with a little tilt, floats while it talks (a blue glow pulses with the mouth, a dotted ring turns
 * slowly) and pops out.
 */
export const HostBubble = ({ appearance, frameUrl, durationInFrames, logo }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const vector = logo && appearance.open && appearance.open.length > 0;
  // The vector mouth's openness stands in for "mouth frames" in the glow logic below.
  const frames = vector ? appearance.open.map((o) => Math.round(o * 6)) : (appearance.frames || []);
  const at = (f) => (frames.length ? frames[Math.max(0, Math.min(f, frames.length - 1))] : 0);
  const value = (list, f) => (list && list.length ? list[Math.max(0, Math.min(f, list.length - 1))] : 0);
  const index = at(frame);
  const enter = spring({ frame, fps, config: { damping: 9, stiffness: 170, mass: 0.6 } });
  const exit = interpolate(frame, [durationInFrames - 9, durationInFrames - 1], [0, 1], {
    ...clamp, easing: Easing.in(Easing.back(2)),
  });
  // Talking = the mouth changed shape in the last few frames; the glow follows it smoothly.
  let moves = 0;
  for (let f = frame - 5; f < frame; f += 1) {
    if (at(f) !== at(f + 1)) moves += 1;
  }
  const talk = Math.min(1, moves / 3);
  const glow = 0.45 + 0.55 * talk;
  const scale = enter * (1 - exit);
  const tilt = interpolate(enter, [0, 1], [-14, 0]) + Math.sin(frame / 18) * 1.6;
  const bob = Math.sin(frame / 12) * 4;
  const outer = SIZE + RING * 2;
  return (
    <AbsoluteFill style={{ pointerEvents: 'none' }}>
      <div style={{
        position: 'absolute', right: 64 - RING, bottom: 64 - RING, width: outer, height: outer,
        transform: `translateY(${bob}px) scale(${scale}) rotate(${tilt}deg)`, transformOrigin: '50% 60%',
        opacity: Math.min(1, enter * 1.4) * (1 - exit * 0.6),
      }}>
        <svg width={outer} height={outer} style={{ position: 'absolute', inset: 0, overflow: 'visible' }}>
          <defs>
            <linearGradient id="hostRing" x1="0" y1="0" x2="1" y2="1">
              <stop offset="0%" stopColor="#ffffff" />
              <stop offset="55%" stopColor={BRAND.sky} />
              <stop offset="100%" stopColor="#2f6f9a" />
            </linearGradient>
          </defs>
          <circle cx={outer / 2} cy={outer / 2} r={SIZE / 2 + 5} fill="none" stroke="url(#hostRing)" strokeWidth={6}
            style={{ filter: `drop-shadow(0 0 ${8 + 16 * glow}px ${BRAND.sky})` }} />
          <circle cx={outer / 2} cy={outer / 2} r={SIZE / 2 + 13} fill="none" stroke="rgba(240,240,240,0.75)"
            strokeWidth={2.5} strokeDasharray="2 11" strokeLinecap="round"
            transform={`rotate(${frame * 0.8} ${outer / 2} ${outer / 2})`} />
        </svg>
        <div style={{
          position: 'absolute', left: RING, top: RING, width: SIZE, height: SIZE, borderRadius: '50%',
          overflow: 'hidden', backgroundColor: '#fff',
          boxShadow: `0 16px 40px rgba(0,0,0,0.6), 0 0 ${20 + 30 * glow}px ${BRAND.sky}66`,
        }}>
          {vector
            ? <LogoMouth logo={logo} style="classic" open={value(appearance.open, frame)}
              shape={value(appearance.shape, frame)} frame={frame} size={SIZE} />
            : <Img src={frameUrl(index)} style={{ width: '100%', height: '100%' }} />}
        </div>
      </div>
    </AbsoluteFill>
  );
};
