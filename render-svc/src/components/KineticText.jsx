import React from 'react';
import { AbsoluteFill, interpolate, spring, useCurrentFrame, useVideoConfig } from 'remotion';
import { BRAND, extrude, headline } from '../brand';
import { EASE, EXIT_FRAMES, SPRING, tween } from '../motion';

/** About 0.1 s between words: a quick ripple, still read one by one. */
const STAGGER_FRAMES = 3;

/**
 * Key phrase in BiPath block letters: words rise in one by one (numbers in sky blue), the line keeps a slight drift
 * while it is read, and it leaves quickly just before the cut. durationInFrames: the frames the card is visible.
 */
export const KineticText = ({ text, durationInFrames = 150 }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const words = text.split(/\s+/).filter(Boolean);
  const size = words.length > 6 ? 78 : 104;
  const drift = interpolate(frame, [0, Math.max(1, durationInFrames)], [1, 1.03], {
    extrapolateLeft: 'clamp', extrapolateRight: 'clamp', easing: EASE.move,
  });
  const leave = tween(frame, durationInFrames - EXIT_FRAMES, durationInFrames - 1, EASE.exit);
  return (
    <AbsoluteFill style={{
      alignItems: 'center', justifyContent: 'center', padding: '0 160px',
      opacity: 1 - leave, transform: `translateY(${-leave * 36}px) scale(${drift})`,
    }}>
      <div style={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'center', columnGap: Math.round(size * 0.32), rowGap: 10, maxWidth: 1560 }}>
        {words.map((word, i) => {
          const progress = spring({ frame: frame - i * STAGGER_FRAMES, fps, config: SPRING.settle });
          return (
            <span
              key={`${word}-${i}`}
              style={{
                fontFamily: headline,
                fontSize: size,
                color: /\d/.test(word) ? BRAND.sky : BRAND.white,
                textTransform: 'uppercase',
                textShadow: extrude(Math.round(size / 12)),
                opacity: Math.min(1, progress * 1.4),
                transform: `translateY(${(1 - progress) * 46}px) scale(${0.85 + progress * 0.15})`,
                display: 'inline-block',
              }}
            >
              {word}
            </span>
          );
        })}
      </div>
      <div style={{
        marginTop: 34, height: 6, borderRadius: 3, backgroundColor: BRAND.sky, boxShadow: `0 0 18px ${BRAND.sky}`,
        width: `${Math.min(1, spring({ frame: frame - 6, fps, config: { damping: 30 } })) * 300}px`,
      }} />
    </AbsoluteFill>
  );
};
