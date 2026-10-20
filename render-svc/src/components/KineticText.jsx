import React from 'react';
import { AbsoluteFill, spring, useCurrentFrame, useVideoConfig } from 'remotion';
import { BRAND, extrude, headline } from '../brand';

const STAGGER_FRAMES = 4;

/** Key phrase in BiPath block letters: words pop in one by one, numbers in sky blue. */
export const KineticText = ({ text }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const words = text.split(/\s+/).filter(Boolean);
  const size = words.length > 6 ? 78 : 104;
  return (
    <AbsoluteFill style={{ alignItems: 'center', justifyContent: 'center', padding: '0 160px' }}>
      <div style={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'center', columnGap: Math.round(size * 0.32), rowGap: 10, maxWidth: 1560 }}>
        {words.map((word, i) => {
          const progress = spring({ frame: frame - i * STAGGER_FRAMES, fps, config: { damping: 14, mass: 0.6 } });
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
