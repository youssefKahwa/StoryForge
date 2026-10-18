import React from 'react';
import { AbsoluteFill, Easing, Img, interpolate, useCurrentFrame } from 'remotion';

const MOTIONS = {
  push_in: { scale: [1.0, 1.14], x: [0, 0], y: [0, -1] },
  pull_out: { scale: [1.14, 1.0], x: [0, 0], y: [-1, 0] },
  pan_left: { scale: [1.12, 1.12], x: [3, -3], y: [0, 0] },
  pan_right: { scale: [1.12, 1.12], x: [-3, 3], y: [0, 0] },
  none: { scale: [1.04, 1.04], x: [0, 0], y: [0, 0] },
};

export const KenBurnsImage = ({ src, motion, durationInFrames, dimmed = false }) => {
  const frame = useCurrentFrame();
  const move = MOTIONS[motion] || MOTIONS.push_in;
  const range = [0, Math.max(1, durationInFrames)];
  const options = { extrapolateRight: 'clamp', easing: Easing.inOut(Easing.quad) };
  const scale = interpolate(frame, range, move.scale, options);
  const x = interpolate(frame, range, move.x, options);
  const y = interpolate(frame, range, move.y, options);
  return (
    <AbsoluteFill style={{ overflow: 'hidden' }}>
      <Img
        src={src}
        style={{
          width: '100%',
          height: '100%',
          objectFit: 'cover',
          transform: `translate(${x}%, ${y}%) scale(${scale})`,
          filter: dimmed ? 'blur(14px) brightness(0.45) saturate(0.8)' : 'saturate(0.95) contrast(1.04)',
        }}
      />
    </AbsoluteFill>
  );
};
