import React from 'react';
import { AbsoluteFill, Easing, Img, Sequence, interpolate, spring, useCurrentFrame, useVideoConfig } from 'remotion';
import { BRAND, body, extrude, headline } from '../brand';

/**
 * A small "subscribe" moment in the bottom-right corner, in one of five styles (the owner picks one):
 * glass, neon, pop, card, minimal. Each lasts NUDGE_FRAMES and animates in, does its thing and leaves.
 */
export const NUDGE_FRAMES = 135;

const clamp = { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' };
const RED = '#e5252a';
const CLICK = 66;

const Bell = ({ size = 26, color = '#fff', swing = 0 }) => (
  <svg width={size} height={size} viewBox="0 0 24 24"
    style={{ transform: `rotate(${swing}deg)`, transformOrigin: '50% 12%', flexShrink: 0 }}>
    <path d="M12 2a1.5 1.5 0 0 1 1.5 1.5v.6A6 6 0 0 1 18 10v4.2l1.6 2.3a.8.8 0 0 1-.7 1.3H5.1a.8.8 0 0 1-.7-1.3L6 14.2V10a6 6 0 0 1 4.5-5.9v-.6A1.5 1.5 0 0 1 12 2Z" fill={color} />
    <path d="M9.5 19.4a2.5 2.5 0 0 0 5 0Z" fill={color} />
  </svg>
);

const Check = ({ size = 20, color = '#fff' }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" style={{ flexShrink: 0 }}>
    <path d="M5 12.5l4.5 4.5L19 7.5" fill="none" stroke={color} strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);

/** Arrow cursor whose tip is at (tipRight, tipTop) measured from the parent's top-right corner. */
const Cursor = ({ tipRight, tipTop, scale = 1, opacity = 1 }) => (
  <svg width={34} height={34} viewBox="0 0 24 24" style={{
    position: 'absolute', right: tipRight - 28, top: tipTop - 2, opacity,
    transform: `scale(${scale})`, transformOrigin: '18% 8%', filter: 'drop-shadow(0 3px 6px rgba(0,0,0,0.55))',
  }}>
    <path d="M4 2l15 10-6.5 1.2L16 20l-2.6 1.3-3.4-6.6L5 19z" fill="#fff" stroke="#111" strokeWidth="1.2" strokeLinejoin="round" />
  </svg>
);

/** Bell ring after the click: a quick swing that dies out. */
const ringAt = (frame, start) => (frame > start ? Math.sin((frame - start) * 0.9) * 24 * Math.exp(-(frame - start) / 14) : 0);

/** Cursor glides in from off-screen, presses on CLICK, then fades. */
const useCursor = (target) => {
  const frame = useCurrentFrame();
  const t = interpolate(frame, [30, CLICK - 2], [0, 1], { ...clamp, easing: Easing.out(Easing.cubic) });
  return {
    tipRight: interpolate(t, [0, 1], [-90, target.right]),
    tipTop: interpolate(t, [0, 1], [170, target.top]),
    scale: interpolate(frame, [CLICK - 3, CLICK, CLICK + 4], [1, 0.82, 1], clamp),
    opacity: interpolate(frame, [30, 36, CLICK + 18, CLICK + 28], [0, 1, 1, 0], clamp),
  };
};

const exitOf = (frame) => interpolate(frame, [NUDGE_FRAMES - 20, NUDGE_FRAMES - 1], [0, 1], {
  ...clamp, easing: Easing.in(Easing.cubic),
});

/** Size of the chosen glass style (1 = the first preview; the owner asked for it a bit bigger). */
const K = 1.3;

/** 1. Frosted-glass pill: channel logo, channel name, red button that a cursor clicks; the bell rings. */
const Glass = ({ logo }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const enter = spring({ frame, fps, config: { damping: 15, stiffness: 110 } });
  const exit = exitOf(frame);
  const pressed = frame >= CLICK;
  const press = interpolate(frame, [CLICK - 3, CLICK, CLICK + 5], [1, 0.9, 1], clamp);
  const ripple = interpolate(frame, [CLICK, CLICK + 16], [0, 1], clamp);
  const cursor = useCursor({ right: 150 * K, top: 46 * K });
  return (
    <div style={{ position: 'absolute', right: 56, bottom: 60, transform: `translateX(${(1 - enter + exit) * 640 * K}px)` }}>
      <div style={{
        display: 'flex', alignItems: 'center', gap: 18 * K, padding: `${12 * K}px ${22 * K}px ${12 * K}px ${12 * K}px`,
        borderRadius: 999, background: 'rgba(16,20,28,0.55)', backdropFilter: 'blur(18px)',
        border: '1px solid rgba(255,255,255,0.18)', boxShadow: '0 18px 40px rgba(0,0,0,0.45)',
      }}>
        <Img src={logo} style={{
          width: 68 * K, height: 68 * K, borderRadius: 999, objectFit: 'cover', background: '#fff',
          border: '2px solid rgba(255,255,255,0.85)',
        }} />
        <div style={{ fontFamily: body, color: '#fff', lineHeight: 1.15, marginRight: 4 * K }}>
          <div style={{ fontWeight: 700, fontSize: 25 * K }}>BiPath</div>
          <div style={{ fontWeight: 500, fontSize: 17 * K, opacity: 0.75 }}>New stories every week</div>
        </div>
        <div style={{
          position: 'relative', overflow: 'hidden', display: 'flex', alignItems: 'center', gap: 8 * K,
          padding: `${14 * K}px ${24 * K}px`, borderRadius: 999, background: pressed ? 'rgba(255,255,255,0.16)' : RED,
          color: '#fff', fontFamily: body, fontWeight: 700, fontSize: 20 * K, letterSpacing: '0.06em',
          transform: `scale(${press})`,
        }}>
          {pressed ? <><Check size={20 * K} />SUBSCRIBED</> : 'SUBSCRIBE'}
          {ripple > 0 && ripple < 1 && (
            <div style={{
              position: 'absolute', left: '50%', top: '50%', width: 280 * K * ripple, height: 280 * K * ripple,
              marginLeft: -140 * K * ripple, marginTop: -140 * K * ripple, borderRadius: 999,
              background: `rgba(255,255,255,${0.45 * (1 - ripple)})`,
            }} />
          )}
        </div>
        <Bell size={30 * K} swing={ringAt(frame, CLICK + 6)} />
      </div>
      <Cursor {...cursor} />
    </div>
  );
};

/** 2. Neon outline: a glowing line draws the box, the letters light up one by one, the bell wiggles. */
const Neon = () => {
  const frame = useCurrentFrame();
  const W = 470;
  const H = 104;
  const perimeter = 2 * (W - H) + Math.PI * H;
  const draw = interpolate(frame, [0, 24, NUDGE_FRAMES - 22, NUDGE_FRAMES - 4], [perimeter, 0, 0, perimeter], {
    ...clamp, easing: Easing.inOut(Easing.cubic),
  });
  const fill = interpolate(frame, [16, 30, NUDGE_FRAMES - 26, NUDGE_FRAMES - 14], [0, 1, 1, 0], clamp);
  const word = 'SUBSCRIBE';
  const pulse = 0.55 + 0.45 * Math.abs(Math.sin(frame / 7));
  return (
    <div style={{ position: 'absolute', right: 60, bottom: 66, width: W, height: H }}>
      <div style={{
        position: 'absolute', inset: 4, borderRadius: H, background: 'rgba(4,10,22,0.62)', backdropFilter: 'blur(10px)',
        opacity: fill,
      }} />
      <svg width={W} height={H} style={{ position: 'absolute', inset: 0, overflow: 'visible' }}>
        <rect x={2} y={2} width={W - 4} height={H - 4} rx={(H - 4) / 2} fill="none" stroke={BRAND.sky} strokeWidth={3}
          strokeDasharray={perimeter} strokeDashoffset={draw}
          style={{ filter: `drop-shadow(0 0 6px ${BRAND.sky}) drop-shadow(0 0 14px ${BRAND.sky})` }} />
      </svg>
      <div style={{
        position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 18,
        opacity: fill,
      }}>
        <div style={{ width: 14, height: 14, borderRadius: 14, background: RED, opacity: pulse, boxShadow: `0 0 14px ${RED}` }} />
        <div style={{ fontFamily: headline, fontSize: 40, color: '#fff', letterSpacing: '0.08em', display: 'flex' }}>
          {word.split('').map((letter, i) => {
            const on = interpolate(frame, [22 + i * 3, 30 + i * 3], [0, 1], clamp);
            return (
              <span key={i} style={{
                opacity: 0.15 + 0.85 * on, transform: `translateY(${(1 - on) * 8}px)`, display: 'inline-block',
                textShadow: on > 0.5 ? `0 0 10px ${BRAND.sky}, 0 0 22px ${BRAND.sky}` : 'none',
              }}>{letter}</span>
            );
          })}
        </div>
        <Bell size={32} color={BRAND.sky} swing={ringAt(frame, 62)} />
      </div>
    </div>
  );
};

/** 3. Logo pop: the round channel logo bounces in, a red pill slides out of it, a click bursts into sparks. */
const Pop = ({ logo }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const pop = spring({ frame, fps, config: { damping: 8, stiffness: 160 } });
  const out = spring({ frame: frame - (NUDGE_FRAMES - 14), fps, config: { damping: 12, stiffness: 180 } });
  const open = spring({ frame: frame - 14, fps, config: { damping: 14, stiffness: 120 } });
  const close = spring({ frame: frame - (NUDGE_FRAMES - 34), fps, config: { damping: 14, stiffness: 140 } });
  const width = 96 + (330 - 96) * Math.max(0, open - close);
  const pressed = frame >= CLICK;
  const burst = interpolate(frame, [CLICK, CLICK + 18], [0, 1], { ...clamp, easing: Easing.out(Easing.cubic) });
  const cursor = useCursor({ right: 210, top: 48 });
  return (
    <div style={{ position: 'absolute', right: 60, bottom: 60, height: 96, width: 340, transform: `scale(${pop * (1 - out)})`, transformOrigin: '90% 50%' }}>
      <div style={{
        position: 'absolute', right: 0, top: 0, height: 96, width, borderRadius: 96,
        background: pressed ? '#2a2f3a' : `linear-gradient(90deg, #ff3b3f, ${RED})`, boxShadow: '0 14px 30px rgba(0,0,0,0.45)',
        display: 'flex', alignItems: 'center', paddingLeft: 30, overflow: 'hidden',
      }}>
        <div style={{
          fontFamily: body, fontWeight: 700, fontSize: 24, color: '#fff', letterSpacing: '0.08em', whiteSpace: 'nowrap',
          opacity: interpolate(width, [200, 300], [0, 1], clamp), display: 'flex', alignItems: 'center', gap: 8,
        }}>
          {pressed ? <><Check size={22} />SUBSCRIBED</> : 'SUBSCRIBE'}
        </div>
      </div>
      {Array.from({ length: 10 }).map((_, i) => {
        const angle = (i / 10) * Math.PI * 2;
        return burst > 0 && burst < 1 ? (
          <div key={i} style={{
            position: 'absolute', right: 210 - Math.cos(angle) * 90 * burst - 5, top: 48 + Math.sin(angle) * 70 * burst - 5,
            width: 10, height: 10, borderRadius: 10, background: i % 2 ? BRAND.sky : '#fff', opacity: 1 - burst,
          }} />
        ) : null;
      })}
      <Img src={logo} style={{
        position: 'absolute', right: 0, top: 0, width: 96, height: 96, borderRadius: 96, background: '#fff',
        border: '3px solid #fff', boxShadow: '0 10px 24px rgba(0,0,0,0.5)',
        transform: `rotate(${interpolate(pop, [0, 1], [-90, 0])}deg)`,
      }} />
      <Cursor {...cursor} />
    </div>
  );
};

/** 4. Brand card: the masked host, SUBSCRIBE in the channel's block letters, a blue underline sweep. */
const Card = ({ face }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const enter = spring({ frame, fps, config: { damping: 16, stiffness: 120 } });
  const exit = exitOf(frame);
  const sweep = interpolate(frame, [20, 44], [0, 1], { ...clamp, easing: Easing.inOut(Easing.cubic) });
  return (
    <div style={{
      position: 'absolute', right: 60, bottom: 60, display: 'flex', alignItems: 'center', gap: 20, padding: '16px 28px 16px 16px',
      borderRadius: 22, background: `linear-gradient(135deg, ${BRAND.night} 0%, #182638 100%)`,
      border: `1px solid rgba(112,192,224,0.35)`, boxShadow: '0 20px 44px rgba(0,0,0,0.55)',
      transform: `translateY(${(1 - enter + exit) * 220}px)`, opacity: 1 - exit,
    }}>
      <Img src={face} style={{ width: 86, height: 86, borderRadius: 18, objectFit: 'cover', border: `2px solid ${BRAND.sky}` }} />
      <div>
        <div style={{ fontFamily: headline, fontSize: 40, color: BRAND.white, textShadow: extrude(4), lineHeight: 1.05 }}>
          SUBSCRIBE
        </div>
        <div style={{ height: 4, width: `${sweep * 100}%`, background: BRAND.sky, borderRadius: 4, margin: '8px 0 8px' }} />
        <div style={{ fontFamily: body, fontWeight: 600, fontSize: 18, color: 'rgba(240,240,240,0.85)', display: 'flex', alignItems: 'center', gap: 8 }}>
          <Bell size={20} color={BRAND.sky} swing={ringAt(frame, 50)} />
          Hit the bell so you never miss a story
        </div>
      </div>
    </div>
  );
};

/** 5. Minimal kinetic text: a question, then "Subscribe" with a red highlight growing behind it. */
const Minimal = () => {
  const frame = useCurrentFrame();
  const exit = exitOf(frame);
  const q = interpolate(frame, [4, 18, 44, 54], [0, 1, 1, 0], clamp);
  const qUp = interpolate(frame, [44, 54], [0, -30], clamp);
  const s = interpolate(frame, [50, 62], [0, 1], { ...clamp, easing: Easing.out(Easing.cubic) });
  const mark = interpolate(frame, [56, 70], [0, 1], { ...clamp, easing: Easing.inOut(Easing.cubic) });
  const bar = interpolate(frame, [0, 16], [0, 1], { ...clamp, easing: Easing.out(Easing.cubic) });
  const bob = Math.sin(frame / 5) * 5;
  return (
    <div style={{
      position: 'absolute', right: 64, bottom: 70, height: 96, width: 520, textAlign: 'right', fontFamily: body,
      color: '#fff', clipPath: `inset(0 0 0 ${exit * 100}%)`,
    }}>
      <div style={{ position: 'absolute', right: 0, top: 8, width: 5, height: 80 * bar, background: RED, borderRadius: 4 }} />
      <div style={{
        position: 'absolute', right: 20, top: 26, fontSize: 32, fontWeight: 600, opacity: q, transform: `translateY(${qUp}px)`,
        textShadow: '0 2px 12px rgba(0,0,0,0.7)', clipPath: `inset(${(1 - q) * 100}% 0 0 0)`,
      }}>
        Enjoying the story?
      </div>
      <div style={{
        position: 'absolute', right: 20, top: 22, fontSize: 36, fontWeight: 700, opacity: s,
        transform: `translateY(${(1 - s) * 30}px)`, display: 'flex', alignItems: 'center', gap: 12, justifyContent: 'flex-end',
      }}>
        <span style={{ position: 'relative', padding: '2px 12px' }}>
          <span style={{
            position: 'absolute', inset: 0, background: RED, borderRadius: 8, transform: `scaleX(${mark})`, transformOrigin: 'left',
          }} />
          <span style={{ position: 'relative' }}>Subscribe</span>
        </span>
        <span style={{ textShadow: '0 2px 12px rgba(0,0,0,0.7)' }}>to BiPath</span>
        <span style={{ transform: `translateX(${bob}px)`, color: RED, fontSize: 34 }}>&#10140;</span>
      </div>
    </div>
  );
};

const VARIANTS = { glass: Glass, neon: Neon, pop: Pop, card: Card, minimal: Minimal };

export const SubscribeNudge = ({ variant = 'glass', face, logo }) => {
  const Variant = VARIANTS[variant] || Glass;
  return (
    <AbsoluteFill style={{ pointerEvents: 'none' }}>
      <Variant face={face} logo={logo} />
    </AbsoluteFill>
  );
};

/** Preview for choosing a style: the nudge over a frame from a real video. */
export const SubscribeDemo = ({ variant, bg, face, logo }) => (
  <AbsoluteFill style={{ backgroundColor: '#000' }}>
    {bg ? <Img src={bg} style={{ width: '100%', height: '100%', objectFit: 'cover' }} /> : null}
    <Sequence from={10} durationInFrames={NUDGE_FRAMES}>
      <SubscribeNudge variant={variant} face={face} logo={logo} />
    </Sequence>
  </AbsoluteFill>
);
