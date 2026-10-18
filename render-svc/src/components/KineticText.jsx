import React from 'react';
import { AbsoluteFill, spring, useCurrentFrame, useVideoConfig } from 'remotion';
import { loadFont } from '@remotion/google-fonts/Cinzel';

const { fontFamily } = loadFont('normal', { weights: ['700'], subsets: ['latin'] });

const STAGGER_FRAMES = 4;

export const KineticText = ({ text, palette }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const accent = palette[2] || '#c9a46b';
  const ink = palette[3] || '#f5f0e6';
  const words = text.split(/\s+/).filter(Boolean);
  const size = words.length > 6 ? 84 : 110;
  return (
    <AbsoluteFill style={{ alignItems: 'center', justifyContent: 'center', padding: '0 160px' }}>
      <div style={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'center', columnGap: Math.round(size * 0.28), maxWidth: 1500 }}>
        {words.map((word, i) => {
          const progress = spring({ frame: frame - i * STAGGER_FRAMES, fps, config: { damping: 18, mass: 0.6 } });
          return (
            <span
              key={`${word}-${i}`}
              style={{
                fontFamily,
                fontSize: size,
                fontWeight: 700,
                color: /\d/.test(word) ? accent : ink,
                letterSpacing: '0.02em',
                textShadow: '0 6px 30px rgba(0,0,0,0.65)',
                opacity: progress,
                transform: `translateY(${(1 - progress) * 40}px) scale(${0.92 + progress * 0.08})`,
                display: 'inline-block',
              }}
            >
              {word}
            </span>
          );
        })}
      </div>
      <div
        style={{
          marginTop: 36,
          height: 4,
          width: `${Math.min(1, spring({ frame: frame - 6, fps, config: { damping: 30 } })) * 280}px`,
          backgroundColor: accent,
          borderRadius: 2,
        }}
      />
    </AbsoluteFill>
  );
};
