import React from 'react';
import { AbsoluteFill, useCurrentFrame, useVideoConfig } from 'remotion';
import { loadFont } from '@remotion/google-fonts/Inter';

const { fontFamily } = loadFont('normal', { weights: ['600'], subsets: ['latin'] });

export const Captions = ({ captions }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const time = frame / fps;
  const current = captions.find((caption) => time >= caption.start && time <= caption.end);
  if (!current) return null;
  return (
    <AbsoluteFill style={{ justifyContent: 'flex-end', alignItems: 'center', paddingBottom: 70 }}>
      <div
        style={{
          fontFamily,
          fontSize: 44,
          fontWeight: 600,
          color: '#fff',
          background: 'rgba(0,0,0,0.55)',
          padding: '10px 26px',
          borderRadius: 10,
          maxWidth: 1500,
          textAlign: 'center',
        }}
      >
        {current.text}
      </div>
    </AbsoluteFill>
  );
};
