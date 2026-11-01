import React from 'react';
import { AbsoluteFill, Audio, Sequence, interpolate, useCurrentFrame, useVideoConfig } from 'remotion';
import { KenBurnsImage } from './components/KenBurnsImage';
import { MediaShot } from './components/MediaShot';
import { KineticText } from './components/KineticText';
import { ChapterCard } from './components/ChapterCard';
import { FilmLook } from './components/FilmLook';
import { Captions } from './components/Captions';
import { OverlayLayer } from './components/Overlays';
import { HostBubble } from './components/HostBubble';
import { OVERLAP_FRAMES, PunchIn, TransitionOverlay, enterStyle, exitStyle } from './components/Transitions';

const CHAPTER_FRAMES = 105;
const OUTRO_FADE_FRAMES = 12;

const isGraphic = (type) => ['kinetic_text', 'map', 'timeline', 'chart'].includes(type);

const assetUrl = (base, file) => `${base}/${file.split('/').map(encodeURIComponent).join('/')}`;

const previousImage = (shots, index) => {
  for (let i = index - 1; i >= 0; i -= 1) {
    if (!isGraphic(shots[i].type) && !['footage', 'clip', 'map', 'host'].includes(shots[i].media)) return shots[i].asset;
  }
  const next = shots.slice(index + 1).find((shot) => !isGraphic(shot.type) && !['footage', 'clip', 'map', 'host'].includes(shot.media));
  return next ? next.asset : null;
};

const Shot = ({ shot, index, shots, assetBase, durationInFrames }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const kind = index === 0 ? 'cut' : (shot.transition || 'cut');
  const next = shots[index + 1];
  const style = { ...exitStyle(next && next.transition, frame, durationInFrames), ...enterStyle(kind, frame) };
  const content = isGraphic(shot.type) ? (
    <>
      {previousImage(shots, index) && (
        <KenBurnsImage src={assetUrl(assetBase, previousImage(shots, index))} motion="push_in" durationInFrames={durationInFrames} dimmed />
      )}
      <KineticText text={shot.text || ''} />
    </>
  ) : <MediaShot shot={shot} src={assetUrl(assetBase, shot.asset)} durationInFrames={durationInFrames} />;
  return (
    <AbsoluteFill style={{ overflow: 'hidden' }}>
      <AbsoluteFill style={style}>
        <PunchIn punch={shot.punch} shotStart={shot.start} fps={fps}>{content}</PunchIn>
      </AbsoluteFill>
      <TransitionOverlay kind={kind} seed={index} />
    </AbsoluteFill>
  );
};

const OutroFade = () => {
  const frame = useCurrentFrame();
  const { durationInFrames } = useVideoConfig();
  const opacity = interpolate(frame, [durationInFrames - OUTRO_FADE_FRAMES, durationInFrames - 1], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  return <AbsoluteFill style={{ backgroundColor: '#000', opacity }} />;
};

export const LongForm = ({ edl, assetBase }) => {
  const { fps, durationInFrames } = useVideoConfig();
  const palette = (edl.style && edl.style.palette) || [];
  const shots = edl.shots || [];
  return (
    <AbsoluteFill style={{ backgroundColor: '#000' }}>
      {shots.map((shot, index) => {
        const from = Math.round(shot.start * fps);
        const last = index === shots.length - 1;
        const length = Math.max(1, Math.round(shot.end * fps) - from + (last ? 0 : OVERLAP_FRAMES));
        return (
          <Sequence key={shot.id} from={from} durationInFrames={Math.min(length, durationInFrames - from)}>
            <Shot
              shot={shot}
              index={index}
              shots={shots}
              assetBase={assetBase}
              durationInFrames={length}
            />
          </Sequence>
        );
      })}
      <FilmLook />
      {(edl.chapters || []).map((chapter, index) => {
        if (index === 0) return null;
        const from = Math.round(chapter.start * fps);
        return (
          <Sequence key={`chapter-${index}`} from={from} durationInFrames={CHAPTER_FRAMES}>
            <ChapterCard title={chapter.title} number={index + 1} palette={palette} durationInFrames={CHAPTER_FRAMES} />
          </Sequence>
        );
      })}
      {(edl.overlays || []).map((overlay, index) => {
        const from = Math.round(overlay.start * fps);
        const length = Math.max(1, Math.min(Math.round((overlay.end - overlay.start) * fps), durationInFrames - from));
        return (
          <Sequence key={`overlay-${index}`} from={from} durationInFrames={length}>
            <OverlayLayer overlay={overlay} palette={palette} durationInFrames={length} />
          </Sequence>
        );
      })}
      {edl.host && (edl.host.appearances || []).map((appearance, index) => {
        const from = Math.round(appearance.start * fps);
        const length = Math.max(1, Math.min(Math.round((appearance.end - appearance.start) * fps), durationInFrames - from));
        const frameUrl = (i) => assetUrl(assetBase, edl.host.framePattern.replace('%04d', String(i + 1).padStart(4, '0')));
        return (
          <Sequence key={`host-${index}`} from={from} durationInFrames={length}>
            <HostBubble appearance={appearance} frameUrl={frameUrl} palette={palette} durationInFrames={length} />
          </Sequence>
        );
      })}
      {edl.style && edl.style.burnCaptions && <Captions captions={edl.captions || []} />}
      {(edl.audio || []).map((track) => (
        <Audio key={track.file} src={assetUrl(assetBase, track.file)} />
      ))}
      <OutroFade />
    </AbsoluteFill>
  );
};
