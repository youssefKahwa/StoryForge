import React from 'react';
import { AbsoluteFill, Audio, Sequence, interpolate, spring, useCurrentFrame, useVideoConfig } from 'remotion';
import { BRAND, Snow, body, extrude, headline } from './brand';
import { MediaShot } from './components/MediaShot';
import { FilmLook } from './components/FilmLook';
import { OVERLAP_FRAMES, PunchIn, TransitionOverlay, enterStyle, exitStyle } from './components/Transitions';

const anton = headline;
const inter = body;

const END_CARD_SECONDS = 2.5;

const assetUrl = (base, file) => `${base}/${file.split('/').map(encodeURIComponent).join('/')}`;

const Title = ({ text, accent }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const enter = spring({ frame, fps, config: { damping: 18 } });
  return (
    <AbsoluteFill style={{ justifyContent: 'flex-start', alignItems: 'center', paddingTop: 210 }}>
      <div
        style={{
          fontFamily: anton,
          fontSize: 80,
          lineHeight: 1.08,
          color: BRAND.white,
          textAlign: 'center',
          textTransform: 'uppercase',
          padding: '22px 40px',
          maxWidth: 980,
          background: `${BRAND.night}cc`,
          borderBottom: `8px solid ${accent}`,
          textShadow: extrude(6),
          transform: `scale(${0.9 + enter * 0.1})`,
          opacity: enter,
        }}
      >
        {text}
      </div>
    </AbsoluteFill>
  );
};

const Captions = ({ words, accent }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const t = frame / fps;
  const index = words.findIndex((w) => t >= w.start && t <= w.end + 0.15);
  if (index < 0) return null;
  const groupStart = index - (index % 3);
  const group = words.slice(groupStart, groupStart + 3);
  return (
    <AbsoluteFill style={{ justifyContent: 'flex-end', alignItems: 'center', paddingBottom: 520 }}>
      <div style={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'center', columnGap: 26, maxWidth: 960 }}>
        {group.map((w, i) => (
          <span
            key={`${w.start}-${i}`}
            style={{
              fontFamily: inter,
              fontWeight: 700,
              fontSize: 80,
              textTransform: 'uppercase',
              color: groupStart + i === index ? accent : BRAND.white,
              textShadow: extrude(6),
            }}
          >
            {w.text.replace(/[.,;:!?"]/g, '')}
          </span>
        ))}
      </div>
    </AbsoluteFill>
  );
};

const EndCard = ({ accent }) => {
  const frame = useCurrentFrame();
  const opacity = interpolate(frame, [0, 10], [0, 1], { extrapolateRight: 'clamp' });
  return (
    <AbsoluteFill style={{ backgroundColor: `rgba(11,15,23,${0.85 * opacity})`, justifyContent: 'center', alignItems: 'center' }}>
      <Snow count={60} opacity={opacity} />
      <div style={{ opacity, textAlign: 'center' }}>
        <div style={{ fontFamily: anton, fontSize: 104, color: BRAND.white, textTransform: 'uppercase', lineHeight: 1.1, textShadow: extrude(8) }}>
          Full story
        </div>
        <div style={{ fontFamily: anton, fontSize: 104, color: accent, textTransform: 'uppercase', lineHeight: 1.1, textShadow: extrude(8, BRAND.navy) }}>
          on the channel
        </div>
        <div style={{ fontFamily: inter, fontWeight: 700, fontSize: 44, color: '#fff', marginTop: 30 }}>▶ watch now</div>
      </div>
    </AbsoluteFill>
  );
};

const ShortShot = ({ shot, next, first, assetBase, length }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const kind = first ? 'cut' : (shot.transition || 'cut');
  const style = { ...exitStyle(next && next.transition, frame, length), ...enterStyle(kind, frame) };
  return (
    <AbsoluteFill style={{ overflow: 'hidden' }}>
      <AbsoluteFill style={style}>
        <PunchIn punch={shot.punch} shotStart={shot.start} fps={fps}>
          <MediaShot shot={shot} src={assetUrl(assetBase, shot.asset)} durationInFrames={length} />
        </PunchIn>
      </AbsoluteFill>
      <TransitionOverlay kind={kind} />
    </AbsoluteFill>
  );
};

export const Short = ({ edl, assetBase }) => {
  const { fps, durationInFrames } = useVideoConfig();
  const accent = BRAND.sky;
  const shots = edl.shots || [];
  const endFrames = Math.round(END_CARD_SECONDS * fps);
  return (
    <AbsoluteFill style={{ backgroundColor: '#000' }}>
      {shots.map((shot, index) => {
        const from = Math.round(shot.start * fps);
        const last = index === shots.length - 1;
        const length = Math.max(1, Math.round(shot.end * fps) - from + (last ? 0 : OVERLAP_FRAMES));
        return (
          <Sequence key={shot.id} from={from} durationInFrames={Math.min(length, durationInFrames - from)}>
            <ShortShot shot={shot} next={shots[index + 1]} first={index === 0} assetBase={assetBase} length={length} />
          </Sequence>
        );
      })}
      <FilmLook />
      <Sequence from={0} durationInFrames={Math.min(durationInFrames, Math.round(4 * fps))}>
        <Title text={edl.title || ''} accent={accent} />
      </Sequence>
      <Sequence from={0} durationInFrames={Math.max(1, durationInFrames - endFrames)}>
        <Captions words={edl.words || []} accent={accent} />
      </Sequence>
      <Sequence from={Math.max(0, durationInFrames - endFrames)} durationInFrames={endFrames}>
        <EndCard accent={accent} />
      </Sequence>
      {edl.audioFile && (
        <Sequence from={0} durationInFrames={Math.max(1, Math.round((edl.contentSec || edl.durationSec) * fps))}>
          <Audio src={assetUrl(assetBase, edl.audioFile)} trimBefore={Math.round((edl.audioStartSec || 0) * fps)} />
        </Sequence>
      )}
    </AbsoluteFill>
  );
};
