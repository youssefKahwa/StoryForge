import React from 'react';
import { AbsoluteFill, interpolate, spring, useCurrentFrame, useVideoConfig } from 'remotion';
import { BRAND, Snow, body, extrude, headline } from '../brand';

/** Chapter title in BiPath style: dark night gradient with snow, block letters with slate extrusion. */
export const ChapterCard = ({ title, number, durationInFrames }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const enter = spring({ frame, fps, config: { damping: 16 } });
  const exit = interpolate(frame, [durationInFrames - 15, durationInFrames - 1], [1, 0], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  return (
    <AbsoluteFill style={{ opacity: exit }}>
      <AbsoluteFill style={{
        background: `linear-gradient(to top right, ${BRAND.night}ee 0%, ${BRAND.night}99 40%, rgba(0,0,0,0) 70%)`,
      }} />
      <Snow count={40} opacity={0.6} />
      <AbsoluteFill style={{ justifyContent: 'flex-end', padding: '0 0 120px 110px' }}>
        <div style={{ transform: `translateX(${(1 - enter) * -70}px)`, opacity: enter }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
            <div style={{
              width: 22, height: 22, borderRadius: 11, backgroundColor: BRAND.navy, border: `5px solid ${BRAND.sky}`,
              boxShadow: `0 0 16px ${BRAND.sky}`,
            }} />
            <div style={{ fontFamily: body, fontWeight: 700, fontSize: 28, letterSpacing: '0.4em', color: BRAND.sky }}>
              {`CHAPTER ${number}`}
            </div>
          </div>
          <div style={{
            fontFamily: headline, fontSize: 76, color: BRAND.white, textTransform: 'uppercase', marginTop: 14,
            textShadow: extrude(7), maxWidth: 1400, lineHeight: 1.08,
          }}>
            {title}
          </div>
        </div>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
