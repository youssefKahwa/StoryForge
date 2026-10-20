import React from 'react';
import { AbsoluteFill, Img } from 'remotion';
import { BRAND, extrude, headline } from './brand';

/** YouTube thumbnail in BiPath style: block letters with slate extrusion, last word in sky blue. */
export const Thumbnail = ({ background, text }) => {
  const words = (text || '').toUpperCase().split(/\s+/).filter(Boolean).slice(0, 4);
  const size = words.join(' ').length > 16 ? 112 : 140;
  return (
    <AbsoluteFill style={{ backgroundColor: BRAND.night }}>
      {background && (
        <Img src={background} style={{ width: '100%', height: '100%', objectFit: 'cover', filter: 'contrast(1.12) saturate(1.1)' }} />
      )}
      <AbsoluteFill style={{ background: `linear-gradient(to right, ${BRAND.night}e6 0%, ${BRAND.night}66 55%, rgba(0,0,0,0) 80%)` }} />
      <AbsoluteFill style={{ justifyContent: 'center', padding: '0 0 0 64px' }}>
        <div style={{ width: 130, height: 12, borderRadius: 6, backgroundColor: BRAND.sky, marginBottom: 26, boxShadow: `0 0 20px ${BRAND.sky}` }} />
        {words.map((word, i) => (
          <div
            key={`${word}-${i}`}
            style={{
              fontFamily: headline,
              fontSize: size,
              lineHeight: 1.04,
              color: i === words.length - 1 ? BRAND.sky : BRAND.white,
              textShadow: extrude(Math.round(size / 11), i === words.length - 1 ? BRAND.navy : BRAND.slate),
              maxWidth: 820,
            }}
          >
            {word}
          </div>
        ))}
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
