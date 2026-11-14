import React from 'react';
import { AbsoluteFill, spring, useCurrentFrame, useVideoConfig } from 'remotion';
import { BRAND, body, extrude, headline } from '../brand';
import { EASE, ENTER_FRAMES, EXIT_FRAMES, presence, tween } from '../motion';

/** Eased in, and out in about half the time: a graphic that leaves slowly looks like it is stuck. */
const useFade = (durationInFrames, enterFrames = ENTER_FRAMES, exitFrames = EXIT_FRAMES) => {
  const frame = useCurrentFrame();
  return presence(frame, durationInFrames, enterFrames, exitFrames);
};

/** Lower third with place and date: evil-eye pin, block letters, sky-blue date. */
export const LocationTag = ({ title, subtitle, durationInFrames }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const opacity = useFade(durationInFrames);
  const line = spring({ frame, fps, config: { damping: 30 } });
  const text = spring({ frame: frame - 6, fps, config: { damping: 20 } });
  return (
    <AbsoluteFill style={{ justifyContent: 'flex-end', padding: '0 0 110px 110px', opacity }}>
      <div style={{
        position: 'absolute', inset: 0,
        background: `linear-gradient(to top, ${BRAND.night}cc 0%, rgba(0,0,0,0) 38%)`,
      }} />
      <div style={{ position: 'relative', display: 'flex', alignItems: 'center', gap: 18 }}>
        <div style={{
          width: 20, height: 20, borderRadius: 10, backgroundColor: BRAND.navy, border: `5px solid ${BRAND.sky}`,
          boxShadow: `0 0 16px ${BRAND.sky}`,
        }} />
        <div style={{ height: 4, width: 110 * line, backgroundColor: BRAND.sky, borderRadius: 2 }} />
      </div>
      <div style={{ position: 'relative', marginTop: 16, opacity: text, transform: `translateY(${(1 - text) * 18}px)` }}>
        <div style={{
          fontFamily: headline, fontSize: 50, color: BRAND.white, textTransform: 'uppercase', textShadow: extrude(5),
        }}>
          {title}
        </div>
        {subtitle && (
          <div style={{ fontFamily: body, fontWeight: 700, fontSize: 28, color: BRAND.sky, letterSpacing: '0.32em', marginTop: 10 }}>
            {subtitle.toUpperCase()}
          </div>
        )}
      </div>
    </AbsoluteFill>
  );
};

/** Quotation card: dark blurred backdrop, big sky-blue block quote mark, words rising in one by one. */
export const QuoteCard = ({ title, subtitle, durationInFrames }) => {
  const frame = useCurrentFrame();
  const opacity = useFade(durationInFrames, 10);
  const words = title.split(/\s+/).filter(Boolean);
  // About 0.1 s apart, faster for a long quote, so the whole quote is up by 45% of the card and can be read.
  const perWord = Math.max(1, Math.min(3, Math.floor((durationInFrames * 0.45) / Math.max(1, words.length))));
  const size = words.length > 22 ? 50 : words.length > 12 ? 60 : 70;
  return (
    <AbsoluteFill style={{ opacity, alignItems: 'center', justifyContent: 'center', padding: '0 220px' }}>
      <div style={{
        position: 'absolute', inset: 0, backgroundColor: 'rgba(8,10,18,0.76)', backdropFilter: 'blur(10px)',
      }} />
      <div style={{ position: 'relative', maxWidth: 1400, textAlign: 'center' }}>
        <div style={{ fontFamily: headline, fontSize: 170, lineHeight: 0.7, color: BRAND.sky, textShadow: extrude(6, BRAND.navy) }}>“</div>
        <div style={{
          fontFamily: body, fontWeight: 600, fontSize: size, lineHeight: 1.3, color: BRAND.white,
          textShadow: '0 4px 24px rgba(0,0,0,0.9)', marginTop: 24,
        }}>
          {words.map((word, i) => {
            const rise = tween(frame, i * perWord, i * perWord + 10);
            return (
              <React.Fragment key={`${word}-${i}`}>
                <span style={{ display: 'inline-block', opacity: rise, transform: `translateY(${(1 - rise) * 22}px)` }}>
                  {word}
                </span>
                {' '}
              </React.Fragment>
            );
          })}
        </div>
        {subtitle && (
          <div style={{
            fontFamily: body, fontWeight: 700, fontSize: 28, letterSpacing: '0.3em', color: BRAND.sky, marginTop: 36,
            opacity: tween(frame, words.length * perWord, words.length * perWord + 12, EASE.enter),
          }}>
            {`— ${subtitle.toUpperCase()}`}
          </div>
        )}
      </div>
    </AbsoluteFill>
  );
};

/** Big year marker at a jump in time, in block letters. */
export const YearMarker = ({ title, durationInFrames }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const opacity = useFade(durationInFrames, 8);
  const enter = spring({ frame, fps, config: { damping: 18 } });
  return (
    <AbsoluteFill style={{ padding: '90px 0 0 110px', opacity }}>
      <div style={{ transform: `translateX(${(1 - enter) * -50}px)`, display: 'flex', alignItems: 'center', gap: 28 }}>
        <div style={{ width: 8, height: 130, backgroundColor: BRAND.sky, borderRadius: 4, boxShadow: `0 0 18px ${BRAND.sky}` }} />
        <div style={{ fontFamily: headline, fontSize: 140, color: BRAND.white, textShadow: extrude(10) }}>
          {title}
        </div>
      </div>
    </AbsoluteFill>
  );
};

export const OverlayLayer = ({ overlay, durationInFrames }) => {
  const props = { title: overlay.title || '', subtitle: overlay.subtitle, durationInFrames };
  if (overlay.kind === 'location') return <LocationTag {...props} />;
  if (overlay.kind === 'quote') return <QuoteCard {...props} />;
  if (overlay.kind === 'year') return <YearMarker {...props} />;
  return null;
};
