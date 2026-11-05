import React from 'react';
import { AbsoluteFill, Composition, Freeze, Img, Still, useCurrentFrame } from 'remotion';
import { LongForm } from './LongForm';
import { NUDGE_FRAMES, SubscribeDemo } from './components/SubscribeNudge';
import { HostBubble } from './components/HostBubble';
import { Short } from './Short';
import { Thumbnail } from './Thumbnail';

const SAMPLE_EDL = {
  version: 2,
  width: 1920,
  height: 1080,
  fps: 30,
  durationSec: 6,
  shots: [
    { id: 'shot_001', type: 'kinetic_text', start: 0, end: 6, text: 'StoryForge preview', motion: 'none' },
  ],
  audio: [],
  captions: [],
  chapters: [{ start: 0, title: 'Preview' }],
  style: { fonts: ['Cinzel', 'Inter'], palette: ['#1f2a36', '#7a5c3e', '#c9a46b', '#e8dcc4'], burnCaptions: false },
};

/**
 * The fast lane's Remotion part: only the designed frame ranges of the long video ([[from, to), ...]), back to back.
 * Output frame i shows timeline frame ranges-mapped(i) (Freeze), so one render covers every range. The duration
 * stays the whole video's, so the timeline maths inside LongForm (outro fade, sequence lengths) is unchanged.
 */
const Designed = ({ edl, assetBase, ranges }) => {
  let index = useCurrentFrame();
  let frame = 0;
  for (const [from, to] of ranges) {
    if (index < to - from) {
      frame = from + index;
      break;
    }
    index -= to - from;
    frame = to - 1;
  }
  return (
    <Freeze frame={frame}>
      <LongForm edl={edl} assetBase={assetBase} filmLook={false} />
    </Freeze>
  );
};

const longFormMetadata = ({ props }) => ({
  durationInFrames: Math.max(1, Math.round(props.edl.durationSec * props.edl.fps)),
  fps: props.edl.fps,
  width: props.edl.width,
  height: props.edl.height,
});

/** Preview of the talking logo over a still: props.framePattern is a URL with %04d, props.frames the mouth frames. */
const HostBubbleDemo = ({ bg, framePattern, frames }) => (
  <AbsoluteFill style={{ backgroundColor: '#000' }}>
    {bg ? <Img src={bg} style={{ width: '100%', height: '100%', objectFit: 'cover' }} /> : null}
    <HostBubble appearance={{ frames }} durationInFrames={frames.length}
      frameUrl={(i) => framePattern.replace('%04d', String(i + 1).padStart(4, '0'))} />
  </AbsoluteFill>
);

export const Root = () => (
  <>
    <Composition
      id="Designed"
      component={Designed}
      width={1920}
      height={1080}
      fps={30}
      durationInFrames={180}
      defaultProps={{ edl: SAMPLE_EDL, assetBase: '', ranges: [[0, 180]] }}
      calculateMetadata={longFormMetadata}
    />
    <Composition
      id="LongForm"
      component={LongForm}
      width={1920}
      height={1080}
      fps={30}
      durationInFrames={180}
      defaultProps={{ edl: SAMPLE_EDL, assetBase: '' }}
      calculateMetadata={({ props }) => ({
        durationInFrames: Math.max(1, Math.round(props.edl.durationSec * props.edl.fps)),
        fps: props.edl.fps,
        width: props.edl.width,
        height: props.edl.height,
      })}
    />
    <Composition
      id="Short"
      component={Short}
      width={1080}
      height={1920}
      fps={30}
      durationInFrames={300}
      defaultProps={{ edl: { ...SAMPLE_EDL, width: 1080, height: 1920, title: 'Preview', words: [] }, assetBase: '' }}
      calculateMetadata={({ props }) => ({
        durationInFrames: Math.max(1, Math.round(props.edl.durationSec * props.edl.fps)),
        fps: props.edl.fps,
      })}
    />
    <Composition
      id="SubscribeDemo"
      component={SubscribeDemo}
      width={1920}
      height={1080}
      fps={30}
      durationInFrames={NUDGE_FRAMES + 20}
      defaultProps={{ variant: 'glass', bg: null, face: null, logo: null }}
    />
    <Composition
      id="HostBubbleDemo"
      component={HostBubbleDemo}
      width={1920}
      height={1080}
      fps={30}
      durationInFrames={60}
      defaultProps={{ bg: null, framePattern: '', frames: [0] }}
      calculateMetadata={({ props }) => ({ durationInFrames: Math.max(1, props.frames.length) })}
    />
    <Still
      id="Thumbnail"
      component={Thumbnail}
      width={1280}
      height={720}
      defaultProps={{ background: null, text: 'Story preview', palette: SAMPLE_EDL.style.palette }}
    />
  </>
);
