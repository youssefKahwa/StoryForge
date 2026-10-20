import React from 'react';
import { AbsoluteFill, Audio, Sequence, interpolate, spring, useCurrentFrame, useVideoConfig } from 'remotion';
import { loadFont as loadAnton } from '@remotion/google-fonts/Anton';
import { loadFont as loadInter } from '@remotion/google-fonts/Inter';
import { MediaShot } from './components/MediaShot';
import { FilmLook } from './components/FilmLook';

const anton = loadAnton('normal', { weights: ['400'], subsets: ['latin'] }).fontFamily;
const inter = loadInter('normal', { weights: ['700'], subsets: ['latin'] }).fontFamily;

const END_CARD_SECONDS = 2.5;
const CROSSFADE_FRAMES = 8;

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
          fontSize: 92,
          lineHeight: 1.02,
          color: '#fff',
          textAlign: 'center',
          textTransform: 'uppercase',
          padding: '18px 40px',
          maxWidth: 960,
          background: 'rgba(0,0,0,0.55)',
          borderBottom: `8px solid ${accent}`,
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
              fontSize: 84,
              textTransform: 'uppercase',
              color: groupStart + i === index ? accent : '#fff',
              WebkitTextStroke: '4px #000',
              paintOrder: 'stroke fill',
              textShadow: '0 6px 18px rgba(0,0,0,0.8)',
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
    <AbsoluteFill style={{ backgroundColor: `rgba(0,0,0,${0.7 * opacity})`, justifyContent: 'center', alignItems: 'center' }}>
      <div style={{ opacity, textAlign: 'center' }}>
        <div style={{ fontFamily: anton, fontSize: 110, color: '#fff', textTransform: 'uppercase', lineHeight: 1.05 }}>
          Full story
        </div>
        <div style={{ fontFamily: anton, fontSize: 110, color: accent, textTransform: 'uppercase', lineHeight: 1.05 }}>
          on the channel
        </div>
        <div style={{ fontFamily: inter, fontWeight: 700, fontSize: 44, color: '#fff', marginTop: 30 }}>▶ watch now</div>
      </div>
    </AbsoluteFill>
  );
};

export const Short = ({ edl, assetBase }) => {
  const { fps, durationInFrames } = useVideoConfig();
  const accent = (edl.style && edl.style.palette && edl.style.palette[2]) || '#f2c94c';
  const shots = edl.shots || [];
  const endFrames = Math.round(END_CARD_SECONDS * fps);
  return (
    <AbsoluteFill style={{ backgroundColor: '#000' }}>
      {shots.map((shot, index) => {
        const from = Math.round(shot.start * fps);
        const last = index === shots.length - 1;
        const length = Math.max(1, Math.round(shot.end * fps) - from + (last ? 0 : CROSSFADE_FRAMES));
        return (
          <Sequence key={shot.id} from={from} durationInFrames={Math.min(length, durationInFrames - from)}>
            <AbsoluteFill>
              <MediaShot shot={shot} src={assetUrl(assetBase, shot.asset)} durationInFrames={length} />
            </AbsoluteFill>
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
