import React from 'react';
import { Img, random } from 'remotion';
import { BRAND } from '../brand';

/**
 * The channel logo with a mouth drawn in vector over it, driven by the voice: open (0..1, how loud) and shape
 * (-1 round "oh" .. 1 wide "ee"). At rest every style is the logo exactly as drawn (the black beak with its white
 * slit). Coordinates are the logo's own pixels (logochannel.jpg, 1536 x 1536).
 * Styles: classic, slot (blue glow), bars (voice bars), jaw (dark jaw).
 */
const L = [594, 914];
const R = [966, 914];
const CX = 780;
/** The lower half of the beak (the top half stays exactly as the logo draws it). */
const beak = (lowerCtrl) => `M ${L[0]} ${L[1]} Q ${CX} ${lowerCtrl} ${R[0]} ${R[1]} Z`;
const lens = (x1, x2, y, top, bottom) => `M ${x1} ${y} Q ${CX} ${top} ${x2} ${y} Q ${CX} ${bottom} ${x1} ${y} Z`;
const SLIT = lens(640, 920, 916, 870, 924);

const Classic = ({ o, s }) => {
  const w = 1 + 0.1 * s;
  const tall = 1 - 0.25 * s;
  const x1 = CX - (140 + 14 * o) * w;
  const x2 = CX + (140 + 14 * o) * w;
  const bottom = 924 + 190 * o * tall;
  return (
    <>
      <path d={beak(1090 + 80 * o * tall)} fill="#000" />
      <path d={lens(x1, x2, 916, 870 - 20 * o, bottom)} fill="#fff" />
      {o > 0.04 && (
        <path d={lens(x1 + 14, x2 - 14, 918, 870 - 20 * o + 34, bottom - 10)} fill="url(#mouthInside)" />
      )}
    </>
  );
};

const Slot = ({ o, s }) => {
  const w = 1 + 0.12 * s;
  const x1 = CX - 135 * w;
  const x2 = CX + 135 * w;
  const glow = Math.min(1, o * 1.6);
  return (
    <>
      <path d={beak(1090)} fill="#000" />
      <path d={lens(x1, x2, 916, 870 - 110 * o, 924 + 150 * o)} fill={o > 0.05 ? '#03101c' : '#fff'}
        stroke={BRAND.sky} strokeWidth={12} strokeOpacity={glow}
        style={{ filter: `drop-shadow(0 0 ${24 * glow}px ${BRAND.sky}) drop-shadow(0 0 ${10 * glow}px ${BRAND.sky})` }} />
      {o > 0.05 && (
        <>
          <line x1={x1 + 36} y1={916} x2={x2 - 36} y2={916} stroke={BRAND.sky} strokeWidth={14 + 26 * o}
            strokeLinecap="round" opacity={0.55 * glow} style={{ filter: `blur(${10 * glow}px)` }} />
          <line x1={x1 + 50} y1={916} x2={x2 - 50} y2={916} stroke="#e8f8ff" strokeWidth={5 + 7 * o}
            strokeLinecap="round" opacity={glow} />
        </>
      )}
    </>
  );
};

const Bars = ({ o, frame }) => {
  const on = Math.min(1, o * 4);
  return (
    <>
      <path d={beak(1090)} fill="#000" />
      <path d={SLIT} fill="#fff" opacity={1 - on} />
      {Array.from({ length: 11 }).map((_, i) => {
        const env = Math.sin((Math.PI * (i + 1)) / 12);
        const jitter = 0.55 + 0.45 * random(`b${i}-${Math.floor(frame / 2)}`);
        const h = 8 + o * 150 * env * jitter;
        return (
          <rect key={i} x={660 + i * 24 - 6} y={916 - h / 2} width={12} height={h} rx={6} fill="#fff" opacity={on} />
        );
      })}
    </>
  );
};

const Jaw = ({ o, s }) => {
  const tall = 1 - 0.25 * s;
  const w = 1 + 0.08 * s;
  const bottom = 924 + 260 * o * tall;
  return (
    <>
      <path d={beak(1090 + 170 * o * tall)} fill="#000" />
      <path d={lens(CX - 140 * w, CX + 140 * w, 916, 870 - 10 * o, bottom)} fill="url(#jawInside)" />
      <path d={SLIT} fill="#fff" opacity={Math.max(0, 1 - o * 3)} />
    </>
  );
};

const STYLES = { classic: Classic, slot: Slot, bars: Bars, jaw: Jaw };

export const LogoMouth = ({ logo, style = 'classic', open = 0, shape = 0, frame = 0, size = 400 }) => {
  const Mouth = STYLES[style] || Classic;
  return (
    <div style={{ position: 'relative', width: size, height: size, borderRadius: '50%', overflow: 'hidden', background: '#fff' }}>
      <Img src={logo} style={{ position: 'absolute', inset: 0, width: size, height: size }} />
      <svg viewBox="0 0 1536 1536" width={size} height={size} style={{ position: 'absolute', inset: 0 }}>
        <defs>
          <radialGradient id="mouthInside" cx="50%" cy="35%" r="70%">
            <stop offset="0%" stopColor="#5a121c" />
            <stop offset="100%" stopColor="#120407" />
          </radialGradient>
          <radialGradient id="jawInside" cx="50%" cy="30%" r="75%">
            <stop offset="0%" stopColor="#2a070c" />
            <stop offset="100%" stopColor="#000000" />
          </radialGradient>
        </defs>
        {/* Fill the drawn slit, erase the drawn lower half, then draw the talking mouth in their place. */}
        <path d={SLIT} fill="#000" stroke="#000" strokeWidth={8} />
        <path d={beak(1090)} fill="#fff" />
        <path d={`M ${L[0]} ${L[1]} Q ${CX} 1090 ${R[0]} ${R[1]}`} fill="none" stroke="#fff" strokeWidth={14} />
        <Mouth o={open} s={shape} frame={frame} />
      </svg>
    </div>
  );
};
