import React from 'react';
import { AbsoluteFill, Easing, Img, interpolate, useCurrentFrame } from 'remotion';

/** Camera moves on a still: a confident push, pull or drift with a hint of rotation, eased like a gimbal move. */
const MOTIONS = {
  push_in: { scale: [1.02, 1.16], x: [0, 0.6], y: [0, -1.2], rotate: [0, 0.6] },
  pull_out: { scale: [1.16, 1.03], x: [0.8, 0], y: [-1.0, 0], rotate: [-0.5, 0] },
  pan_left: { scale: [1.12, 1.14], x: [3.5, -3.5], y: [0, -0.4], rotate: [0.3, -0.3] },
  pan_right: { scale: [1.12, 1.14], x: [-3.5, 3.5], y: [0, 0.4], rotate: [-0.3, 0.3] },
  none: { scale: [1.04, 1.06], x: [0, 0], y: [0, 0], rotate: [0, 0] },
};

export const KenBurnsImage = ({ src, motion, durationInFrames, dimmed = false }) => {
  const frame = useCurrentFrame();
  const move = MOTIONS[motion] || MOTIONS.push_in;
  const range = [0, Math.max(1, durationInFrames)];
  const options = { extrapolateRight: 'clamp', easing: Easing.inOut(Easing.cubic) };
  const scale = interpolate(frame, range, move.scale, options);
  const x = interpolate(frame, range, move.x, options);
  const y = interpolate(frame, range, move.y, options);
  const rotate = interpolate(frame, range, move.rotate, options);
  return (
    <AbsoluteFill style={{ overflow: 'hidden' }}>
      <Img
        src={src}
        style={{
          width: '100%',
          height: '100%',
          objectFit: 'cover',
          transform: `translate(${x}%, ${y}%) scale(${scale}) rotate(${rotate}deg)`,
          filter: dimmed ? 'blur(14px) brightness(0.45) saturate(0.8)' : 'saturate(0.95) contrast(1.06)',
        }}
      />
    </AbsoluteFill>
  );
};
