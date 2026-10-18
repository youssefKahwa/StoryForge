import React from 'react';
import { AbsoluteFill, Img } from 'remotion';
import { loadFont } from '@remotion/google-fonts/Anton';

const { fontFamily } = loadFont('normal', { weights: ['400'], subsets: ['latin'] });

export const Thumbnail = ({ background, text, palette }) => {
  const accent = (palette && palette[2]) || '#f2c94c';
  const words = (text || '').toUpperCase().split(/\s+/).filter(Boolean).slice(0, 4);
  const size = words.join(' ').length > 16 ? 120 : 150;
  return (
    <AbsoluteFill style={{ backgroundColor: '#111' }}>
      {background && (
        <Img src={background} style={{ width: '100%', height: '100%', objectFit: 'cover', filter: 'contrast(1.12) saturate(1.1)' }} />
      )}
      <AbsoluteFill style={{ background: 'linear-gradient(to right, rgba(0,0,0,0.78) 0%, rgba(0,0,0,0.35) 55%, rgba(0,0,0,0) 80%)' }} />
      <AbsoluteFill style={{ justifyContent: 'center', padding: '0 0 0 64px' }}>
        <div style={{ width: 120, height: 10, backgroundColor: accent, marginBottom: 22 }} />
        {words.map((word, i) => (
          <div
            key={`${word}-${i}`}
            style={{
              fontFamily,
              fontSize: size,
              lineHeight: 0.98,
              color: i === words.length - 1 ? accent : '#ffffff',
              WebkitTextStroke: '3px #000',
              textShadow: '0 8px 24px rgba(0,0,0,0.85)',
              maxWidth: 780,
            }}
          >
            {word}
          </div>
        ))}
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
