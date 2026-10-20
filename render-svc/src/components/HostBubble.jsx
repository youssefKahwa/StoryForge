import React from 'react';
import { AbsoluteFill, Img, interpolate, spring, useCurrentFrame, useVideoConfig } from 'remotion';
import { BRAND } from '../brand';

const SIZE = 230;

/** The channel's talking logo in a round bubble; the mouth frame follows the narration loudness. */
export const HostBubble = ({ appearance, frameUrl, palette, durationInFrames }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const accent = BRAND.sky;
  const enter = spring({ frame, fps, config: { damping: 13, mass: 0.7 } });
  const exit = interpolate(frame, [durationInFrames - 9, durationInFrames - 1], [1, 0], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  const frames = appearance.frames || [];
  const index = frames.length ? frames[Math.min(frame, frames.length - 1)] : 0;
  const visible = Math.min(enter, exit);
  return (
    <AbsoluteFill style={{ pointerEvents: 'none' }}>
      <div
        style={{
          position: 'absolute',
          right: 72,
          bottom: 72,
          width: SIZE,
          height: SIZE,
          borderRadius: '50%',
          overflow: 'hidden',
          backgroundColor: '#000',
          border: `4px solid ${accent}`,
          boxShadow: `0 0 26px ${BRAND.sky}88, 0 12px 44px rgba(0,0,0,0.65)`,
          opacity: visible,
          transform: `scale(${0.55 + 0.45 * visible}) translateY(${(1 - enter) * 40}px)`,
        }}
      >
        <Img src={frameUrl(index)} style={{ width: '100%', height: '100%' }} />
      </div>
    </AbsoluteFill>
  );
};
