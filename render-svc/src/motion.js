import { Easing, interpolate } from 'remotion';

/**
 * One set of motion curves for every graphic (from the claude-motion and claude-remotion-skill design rules):
 * entrances decelerate into place, exits accelerate away and take about half as long, nothing that starts or stops
 * moves linearly.
 */
export const EASE = {
  enter: Easing.bezier(0.16, 1, 0.3, 1),
  exit: Easing.bezier(0.6, 0, 0.9, 0.35),
  move: Easing.bezier(0.65, 0, 0.35, 1),
};

export const SPRING = {
  settle: { damping: 14, stiffness: 140, mass: 0.8 },
  pop: { damping: 10, stiffness: 215, mass: 0.7 },
};

export const ENTER_FRAMES = 14;
export const EXIT_FRAMES = 7;

const clamp = { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' };

/** 0 -> 1 over the entrance, 1 -> 0 over the (shorter) exit at the end of durationInFrames. */
export const presence = (frame, durationInFrames, enterFrames = ENTER_FRAMES, exitFrames = EXIT_FRAMES) => {
  const enter = interpolate(frame, [0, enterFrames], [0, 1], { ...clamp, easing: EASE.enter });
  const exit = interpolate(frame, [durationInFrames - exitFrames, durationInFrames - 1], [1, 0],
    { ...clamp, easing: EASE.exit });
  return Math.min(enter, exit);
};

/** Eased progress of a tween from frame `from` to frame `to`. */
export const tween = (frame, from, to, easing = EASE.enter) => interpolate(frame, [from, to], [0, 1],
  { ...clamp, easing });
