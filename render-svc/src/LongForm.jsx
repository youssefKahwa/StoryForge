import React from 'react';
import { AbsoluteFill, Audio, OffthreadVideo, Sequence, interpolate, useCurrentFrame, useVideoConfig } from 'remotion';
import { EASE } from './motion';
import { KenBurnsImage } from './components/KenBurnsImage';
import { MediaShot } from './components/MediaShot';
import { KineticText } from './components/KineticText';
import { ChapterCard } from './components/ChapterCard';
import { FilmLook } from './components/FilmLook';
import { Captions } from './components/Captions';
import { OverlayLayer } from './components/Overlays';
import { SubscribeNudge } from './components/SubscribeNudge';
import { HostBubble } from './components/HostBubble';
import { OVERLAP_FRAMES, PunchIn, TransitionOverlay, enterStyle, exitStyle } from './components/Transitions';

const CHAPTER_FRAMES = 105;
const OUTRO_FADE_FRAMES = 12;

const isGraphic = (type) => ['kinetic_text', 'map', 'timeline', 'chart'].includes(type);

const assetUrl = (base, file) => `${base}/${file.split('/').map(encodeURIComponent).join('/')}`;

const isMoving = (shot) => ['footage', 'clip'].includes(shot.media);
const canBack = (shot) => shot && shot.asset && !isGraphic(shot.type) && !['map', 'host'].includes(shot.media);

/**
 * What a text card sits on: the picture just before it (film footage included), else the one just after. On a film
 * video almost every shot is footage, so a card over stills only would sit on black.
 */
const backdrop = (shots, index) => {
  for (let i = index - 1; i >= 0; i -= 1) {
    if (canBack(shots[i])) return shots[i];
  }
  return shots.slice(index + 1).find(canBack) || null;
};

/** The footage behind a text card: half speed, blurred and dimmed, slowly pushed in; it holds its last frame. */
const MovingBackdrop = ({ src, durationInFrames }) => {
  const frame = useCurrentFrame();
  const scale = interpolate(frame, [0, Math.max(1, durationInFrames)], [1.06, 1.14], {
    extrapolateLeft: 'clamp', extrapolateRight: 'clamp', easing: EASE.move,
  });
  return (
    <AbsoluteFill style={{ overflow: 'hidden', backgroundColor: '#000' }}>
      <OffthreadVideo src={src} muted playbackRate={0.5} style={{
        width: '100%', height: '100%', objectFit: 'cover', transform: `scale(${scale})`,
        filter: 'blur(14px) brightness(0.45) saturate(0.8)',
      }} />
    </AbsoluteFill>
  );
};

const Backdrop = ({ shot, assetBase, durationInFrames }) => {
  if (!shot) return null;
  const src = assetUrl(assetBase, shot.asset);
  return isMoving(shot)
    ? <MovingBackdrop src={src} durationInFrames={durationInFrames} />
    : <KenBurnsImage src={src} motion="push_in" durationInFrames={durationInFrames} dimmed />;
};

const Shot = ({ shot, index, shots, assetBase, durationInFrames }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const kind = index === 0 ? 'cut' : (shot.transition || 'cut');
  const next = shots[index + 1];
  const style = { ...exitStyle(next && next.transition, frame, durationInFrames), ...enterStyle(kind, frame) };
  const content = isGraphic(shot.type) ? (
    <>
      <Backdrop shot={backdrop(shots, index)} assetBase={assetBase} durationInFrames={durationInFrames} />
      <KineticText text={shot.text || ''} durationInFrames={next ? durationInFrames - OVERLAP_FRAMES : durationInFrames} />
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

export const LongForm = ({ edl, assetBase, filmLook = true }) => {
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
      {filmLook && <FilmLook />}
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
            {overlay.kind === 'subscribe'
              ? <SubscribeNudge variant="glass" logo={assetUrl(assetBase, overlay.title)} />
              : <OverlayLayer overlay={overlay} palette={palette} durationInFrames={length} />}
          </Sequence>
        );
      })}
      {edl.host && (edl.host.appearances || []).map((appearance, index) => {
        const from = Math.round(appearance.start * fps);
        const length = Math.max(1, Math.min(Math.round((appearance.end - appearance.start) * fps), durationInFrames - from));
        const frameUrl = (i) => (edl.host.framePattern
          ? assetUrl(assetBase, edl.host.framePattern.replace('%04d', String(i + 1).padStart(4, '0'))) : null);
        return (
          <Sequence key={`host-${index}`} from={from} durationInFrames={length}>
            <HostBubble appearance={appearance} frameUrl={frameUrl} palette={palette} durationInFrames={length}
              logo={edl.host.logo ? assetUrl(assetBase, edl.host.logo) : null} />
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
