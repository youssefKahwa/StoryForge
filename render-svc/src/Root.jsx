import React from 'react';
import { Composition, Still } from 'remotion';
import { LongForm } from './LongForm';
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

export const Root = () => (
  <>
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
    <Still
      id="Thumbnail"
      component={Thumbnail}
      width={1280}
      height={720}
      defaultProps={{ background: null, text: 'Story preview', palette: SAMPLE_EDL.style.palette }}
    />
  </>
);
