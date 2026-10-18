import React from 'react';
import { AbsoluteFill, interpolate, spring, useCurrentFrame, useVideoConfig } from 'remotion';
import { loadFont as loadCinzel } from '@remotion/google-fonts/Cinzel';
import { loadFont as loadInter } from '@remotion/google-fonts/Inter';

const cinzel = loadCinzel('normal', { weights: ['700'], subsets: ['latin'] }).fontFamily;
const inter = loadInter('normal', { weights: ['500'], subsets: ['latin'] }).fontFamily;

export const ChapterCard = ({ title, number, palette, durationInFrames }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const enter = spring({ frame, fps, config: { damping: 20 } });
  const exit = interpolate(frame, [durationInFrames - 15, durationInFrames - 1], [1, 0], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  const accent = palette[2] || '#c9a46b';
  return (
    <AbsoluteFill
      style={{
        justifyContent: 'flex-end',
        padding: '0 0 120px 110px',
        opacity: exit,
        background: 'linear-gradient(to top right, rgba(0,0,0,0.6) 0%, rgba(0,0,0,0.25) 35%, rgba(0,0,0,0) 60%)',
      }}
    >
      <div style={{ transform: `translateX(${(1 - enter) * -60}px)`, opacity: enter }}>
        <div style={{ fontFamily: inter, fontSize: 26, letterSpacing: '0.35em', color: accent, textTransform: 'uppercase' }}>
          {`Chapter ${number}`}
        </div>
        <div
          style={{
            fontFamily: cinzel,
            fontSize: 64,
            fontWeight: 700,
            color: '#f5f0e6',
            textShadow: '0 4px 24px rgba(0,0,0,0.7)',
            marginTop: 8,
            borderLeft: `6px solid ${accent}`,
            paddingLeft: 24,
          }}
        >
          {title}
        </div>
      </div>
    </AbsoluteFill>
  );
};
