import React from 'react';
import { AbsoluteFill, interpolate, spring, useCurrentFrame, useVideoConfig } from 'remotion';
import { loadFont as loadCinzel } from '@remotion/google-fonts/Cinzel';
import { loadFont as loadInter } from '@remotion/google-fonts/Inter';
import { loadFont as loadCormorant } from '@remotion/google-fonts/CormorantGaramond';

const cinzel = loadCinzel('normal', { weights: ['700'], subsets: ['latin'] }).fontFamily;
const inter = loadInter('normal', { weights: ['500', '600'], subsets: ['latin'] }).fontFamily;
const cormorant = loadCormorant('italic', { weights: ['500'], subsets: ['latin'] }).fontFamily;

const useFade = (durationInFrames, fadeFrames = 12) => {
  const frame = useCurrentFrame();
  return interpolate(frame, [0, fadeFrames, durationInFrames - fadeFrames, durationInFrames - 1], [0, 1, 1, 0], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
};

/** Lower third with place and date, like a documentary location super. */
export const LocationTag = ({ title, subtitle, palette, durationInFrames }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const opacity = useFade(durationInFrames);
  const line = spring({ frame, fps, config: { damping: 30 } });
  const text = spring({ frame: frame - 6, fps, config: { damping: 22 } });
  const accent = palette[2] || '#c9a46b';
  return (
    <AbsoluteFill style={{ justifyContent: 'flex-end', padding: '0 0 110px 110px', opacity }}>
      <div style={{
        position: 'absolute', inset: 0,
        background: 'linear-gradient(to top, rgba(0,0,0,0.55) 0%, rgba(0,0,0,0) 38%)',
      }} />
      <div style={{ position: 'relative', display: 'flex', alignItems: 'center', gap: 22 }}>
        <div style={{ width: 14, height: 14, borderRadius: 7, backgroundColor: accent, boxShadow: `0 0 18px ${accent}` }} />
        <div style={{ height: 2, width: 90 * line, backgroundColor: accent }} />
      </div>
      <div style={{ position: 'relative', marginTop: 14, opacity: text, transform: `translateY(${(1 - text) * 16}px)` }}>
        <div style={{
          fontFamily: inter, fontWeight: 600, fontSize: 44, color: '#f5f0e6', letterSpacing: '0.12em',
          textTransform: 'uppercase', textShadow: '0 3px 18px rgba(0,0,0,0.8)',
        }}>
          {title}
        </div>
        {subtitle && (
          <div style={{ fontFamily: inter, fontWeight: 500, fontSize: 28, color: accent, letterSpacing: '0.3em', marginTop: 6 }}>
            {subtitle.toUpperCase()}
          </div>
        )}
      </div>
    </AbsoluteFill>
  );
};

/** Full-frame quotation card: words fade in one by one over a darkened picture. */
export const QuoteCard = ({ title, subtitle, palette, durationInFrames }) => {
  const frame = useCurrentFrame();
  const opacity = useFade(durationInFrames, 10);
  const accent = palette[2] || '#c9a46b';
  const words = title.split(/\s+/).filter(Boolean);
  const perWord = Math.max(1, Math.min(5, Math.floor((durationInFrames * 0.45) / Math.max(1, words.length))));
  const size = words.length > 22 ? 52 : words.length > 12 ? 62 : 74;
  return (
    <AbsoluteFill style={{ opacity, alignItems: 'center', justifyContent: 'center', padding: '0 220px' }}>
      <div style={{
        position: 'absolute', inset: 0, backgroundColor: 'rgba(8,8,10,0.74)', backdropFilter: 'blur(10px)',
      }} />
      <div style={{ position: 'relative', maxWidth: 1400, textAlign: 'center' }}>
        <div style={{ fontFamily: cinzel, fontSize: 150, lineHeight: 0.6, color: accent, opacity: 0.85 }}>“</div>
        <div style={{
          fontFamily: cormorant, fontStyle: 'italic', fontSize: size, lineHeight: 1.25, color: '#f5f0e6',
          textShadow: '0 4px 24px rgba(0,0,0,0.9)',
        }}>
          {words.map((word, i) => (
            <span
              key={`${word}-${i}`}
              style={{
                opacity: interpolate(frame, [i * perWord, i * perWord + 8], [0, 1], { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' }),
              }}
            >
              {`${word} `}
            </span>
          ))}
        </div>
        {subtitle && (
          <div style={{
            fontFamily: inter, fontWeight: 500, fontSize: 28, letterSpacing: '0.28em', color: accent, marginTop: 36,
            opacity: interpolate(frame, [words.length * perWord, words.length * perWord + 12], [0, 1], { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' }),
          }}>
            {`— ${subtitle.toUpperCase()}`}
          </div>
        )}
      </div>
    </AbsoluteFill>
  );
};

/** Big year marker at a jump in time. */
export const YearMarker = ({ title, palette, durationInFrames }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const opacity = useFade(durationInFrames, 8);
  const enter = spring({ frame, fps, config: { damping: 24 } });
  const accent = palette[2] || '#c9a46b';
  return (
    <AbsoluteFill style={{ padding: '90px 0 0 110px', opacity }}>
      <div style={{ transform: `translateX(${(1 - enter) * -40}px)`, display: 'flex', alignItems: 'center', gap: 26 }}>
        <div style={{ width: 6, height: 120, backgroundColor: accent }} />
        <div style={{
          fontFamily: cinzel, fontWeight: 700, fontSize: 132, color: '#f5f0e6', letterSpacing: '0.06em',
          textShadow: '0 6px 30px rgba(0,0,0,0.7)',
        }}>
          {title}
        </div>
      </div>
    </AbsoluteFill>
  );
};

export const OverlayLayer = ({ overlay, palette, durationInFrames }) => {
  const props = { title: overlay.title || '', subtitle: overlay.subtitle, palette, durationInFrames };
  if (overlay.kind === 'location') return <LocationTag {...props} />;
  if (overlay.kind === 'quote') return <QuoteCard {...props} />;
  if (overlay.kind === 'year') return <YearMarker {...props} />;
  return null;
};
