import React from 'react';
import {
  AbsoluteFill, Easing, Img, OffthreadVideo, Sequence, interpolate, random, spring, useCurrentFrame, useVideoConfig,
} from 'remotion';
import { BRAND, Snow, body, extrude, headline } from '../brand';
import { NUDGE_FRAMES, SubscribeNudge } from './SubscribeNudge';

/**
 * The channel's intro and outro (end screen), in several styles for the owner to choose from.
 * Intros: logo_reveal, two_paths, talking_logo, masked_host, glitch.
 * Outros: end_screen, talking_logo, masked_host, cinematic. Outros keep clear space for YouTube's end-screen
 * elements: two "next video" boxes and the subscribe circle (SLOTS / SUB below).
 */
const clamp = { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' };
const ease = { ...clamp, easing: Easing.out(Easing.cubic) };
const TAGLINE = 'Every story has two paths';

const Night = ({ snow = 60 }) => (
  <AbsoluteFill style={{ background: `radial-gradient(ellipse at 50% 45%, #18263a 0%, ${BRAND.night} 62%, #05070b 100%)` }}>
    <Snow count={snow} opacity={0.55} />
  </AbsoluteFill>
);

const fadeOut = (frame, total, length = 15) => interpolate(frame, [total - length, total - 1], [1, 0], clamp);

/** The round logo with the channel's blue ring and a slowly turning dotted ring. */
const LogoDisc = ({ logo, size, frame, glow = 0.6, src }) => {
  const outer = size + 44;
  return (
    <div style={{ position: 'relative', width: outer, height: outer }}>
      <svg width={outer} height={outer} style={{ position: 'absolute', inset: 0, overflow: 'visible' }}>
        <circle cx={outer / 2} cy={outer / 2} r={size / 2 + 6} fill="none" stroke={BRAND.sky} strokeWidth={6}
          style={{ filter: `drop-shadow(0 0 ${10 + 18 * glow}px ${BRAND.sky})` }} />
        <circle cx={outer / 2} cy={outer / 2} r={size / 2 + 17} fill="none" stroke="rgba(240,240,240,0.7)" strokeWidth={3}
          strokeDasharray="2 12" strokeLinecap="round" transform={`rotate(${frame * 0.7} ${outer / 2} ${outer / 2})`} />
      </svg>
      <Img src={src || logo} style={{
        position: 'absolute', left: 22, top: 22, width: size, height: size, borderRadius: '50%', objectFit: 'cover',
        background: '#fff', boxShadow: '0 20px 60px rgba(0,0,0,0.6)',
      }} />
    </div>
  );
};

/** "BiPath" in the logo's block letters; the letters drop in one by one from {@code start}. */
const Title = ({ size = 120, start = 0, frame, fps }) => (
  <div style={{ display: 'flex', fontFamily: headline, fontSize: size, color: BRAND.white, letterSpacing: '0.02em' }}>
    {'BiPath'.split('').map((letter, i) => {
      const s = spring({ frame: frame - start - i * 3, fps, config: { damping: 11, stiffness: 160 } });
      return (
        <span key={i} style={{
          display: 'inline-block', textShadow: extrude(Math.round(size / 15)), opacity: Math.min(1, s * 1.5),
          transform: `translateY(${(1 - s) * -80}px)`,
        }}>{letter}</span>
      );
    })}
  </div>
);

const Tagline = ({ frame, start, size = 30 }) => (
  <div style={{
    fontFamily: body, fontWeight: 600, fontSize: size, color: 'rgba(240,240,240,0.85)', letterSpacing: '0.32em',
    textTransform: 'uppercase', opacity: interpolate(frame, [start, start + 15], [0, 1], clamp),
    transform: `translateY(${interpolate(frame, [start, start + 15], [16, 0], ease)}px)`,
  }}>{TAGLINE}</div>
);

/* ------------------------------------------------------------------ intros */

const LogoReveal = ({ logo }) => {
  const frame = useCurrentFrame();
  const { fps, durationInFrames } = useVideoConfig();
  const draw = interpolate(frame, [0, 30], [1, 0], { ...clamp, easing: Easing.inOut(Easing.cubic) });
  const pop = spring({ frame: frame - 16, fps, config: { damping: 12, stiffness: 120 } });
  const glint = interpolate(frame, [40, 50, 62], [0, 1, 0], clamp);
  const r = 172;
  const out = fadeOut(frame, durationInFrames);
  const zoom = interpolate(frame, [durationInFrames - 15, durationInFrames], [1, 1.08], clamp);
  return (
    <AbsoluteFill style={{ opacity: out, transform: `scale(${zoom})` }}>
      <Night />
      <AbsoluteFill style={{ alignItems: 'center', justifyContent: 'center', flexDirection: 'column', gap: 26 }}>
        <div style={{ position: 'relative', width: 2 * r + 40, height: 2 * r + 40 }}>
          <svg width={2 * r + 40} height={2 * r + 40} style={{ position: 'absolute', inset: 0, overflow: 'visible' }}>
            <circle cx={r + 20} cy={r + 20} r={r} fill="none" stroke={BRAND.sky} strokeWidth={7}
              strokeDasharray={2 * Math.PI * r} strokeDashoffset={draw * 2 * Math.PI * r}
              style={{ filter: `drop-shadow(0 0 14px ${BRAND.sky})` }} transform={`rotate(-90 ${r + 20} ${r + 20})`} />
          </svg>
          <Img src={logo} style={{
            position: 'absolute', left: 20 + r - 160, top: 20 + r - 160, width: 320, height: 320, borderRadius: '50%',
            objectFit: 'cover', background: '#fff', opacity: pop, transform: `scale(${0.6 + 0.4 * pop}) rotate(${(1 - pop) * -40}deg)`,
          }} />
          <div style={{
            position: 'absolute', left: 20 + r + 52, top: 20 + r - 30, width: 60, height: 60, borderRadius: 60,
            background: `radial-gradient(circle, rgba(255,255,255,${0.95 * glint}) 0%, rgba(112,192,224,${0.6 * glint}) 40%, rgba(112,192,224,0) 70%)`,
          }} />
        </div>
        <Title frame={frame} fps={fps} start={44} />
        <Tagline frame={frame} start={78} />
      </AbsoluteFill>
    </AbsoluteFill>
  );
};

const TwoPaths = ({ logo }) => {
  const frame = useCurrentFrame();
  const { fps, durationInFrames } = useVideoConfig();
  const fork = [960, 540];
  const trunk = interpolate(frame, [0, 28], [0, 1], { ...clamp, easing: Easing.inOut(Easing.cubic) });
  const split = interpolate(frame, [26, 60], [0, 1], { ...clamp, easing: Easing.inOut(Easing.cubic) });
  const pop = spring({ frame: frame - 52, fps, config: { damping: 10, stiffness: 150 } });
  const upper = `M ${fork[0]} ${fork[1]} C 1220 540, 1330 250, 1960 190`;
  const lower = `M ${fork[0]} ${fork[1]} C 1220 540, 1330 830, 1960 890`;
  const len = 1400;
  // Once the paths and the logo are drawn, the whole drawing glides up to make room for the name below it.
  const rise = interpolate(frame, [60, 84], [0, -150], { ...clamp, easing: Easing.inOut(Easing.cubic) });
  return (
    <AbsoluteFill style={{ opacity: fadeOut(frame, durationInFrames) }}>
      <Night />
      <AbsoluteFill style={{ transform: `translateY(${rise}px)` }}>
      <svg width={1920} height={1080} style={{ position: 'absolute', inset: 0 }}>
        <line x1={-20} y1={fork[1]} x2={-20 + (fork[0] + 20) * trunk} y2={fork[1]} stroke="#fff" strokeWidth={8}
          strokeLinecap="round" style={{ filter: 'drop-shadow(0 0 10px rgba(255,255,255,0.8))' }} />
        <path d={upper} fill="none" stroke={BRAND.sky} strokeWidth={8} strokeLinecap="round" strokeDasharray={len}
          strokeDashoffset={(1 - split) * len} style={{ filter: `drop-shadow(0 0 12px ${BRAND.sky})` }} />
        <path d={lower} fill="none" stroke="#fff" strokeWidth={8} strokeLinecap="round" strokeDasharray={len}
          strokeDashoffset={(1 - split) * len} style={{ filter: 'drop-shadow(0 0 12px rgba(255,255,255,0.8))' }} />
      </svg>
      <div style={{ position: 'absolute', left: fork[0] - 131, top: fork[1] - 131, transform: `scale(${pop})` }}>
        <LogoDisc logo={logo} size={218} frame={frame} />
      </div>
      </AbsoluteFill>
      <div style={{
        position: 'absolute', left: 0, bottom: 130, width: '100%', display: 'flex', flexDirection: 'column',
        alignItems: 'center', gap: 18,
      }}>
        <Title frame={frame} fps={fps} start={74} size={120} />
        <Tagline frame={frame} start={90} size={26} />
      </div>
    </AbsoluteFill>
  );
};

/** The talking logo, big and centred, lip-synced to the voice (mouth = mouth frame per video frame). */
const BigTalker = ({ mouthPattern, mouth, frame, fps, size = 440, start = 0 }) => {
  const at = (f) => mouth[Math.max(0, Math.min(f, mouth.length - 1))];
  const index = at(frame);
  let moves = 0;
  for (let f = frame - 5; f < frame; f += 1) if (at(f) !== at(f + 1)) moves += 1;
  const glow = 0.4 + 0.6 * Math.min(1, moves / 3);
  const pop = spring({ frame: frame - start, fps, config: { damping: 9, stiffness: 150, mass: 0.7 } });
  const bob = Math.sin(frame / 12) * 6;
  const src = mouthPattern.replace('%04d', String(index + 1).padStart(4, '0'));
  return (
    <div style={{ transform: `translateY(${bob}px) scale(${pop}) rotate(${(1 - pop) * -16}deg)` }}>
      <LogoDisc src={src} size={size} frame={frame} glow={glow} />
    </div>
  );
};

const TalkingIntro = ({ mouthPattern, mouth }) => {
  const frame = useCurrentFrame();
  const { fps, durationInFrames } = useVideoConfig();
  const exit = interpolate(frame, [durationInFrames - 12, durationInFrames - 1], [1, 0], clamp);
  const tag = spring({ frame: frame - 18, fps, config: { damping: 14 } });
  return (
    <AbsoluteFill style={{ opacity: exit }}>
      <Night />
      <AbsoluteFill style={{ alignItems: 'center', justifyContent: 'center', flexDirection: 'column', gap: 10, marginTop: -40 }}>
        <BigTalker mouthPattern={mouthPattern} mouth={mouth} frame={frame} fps={fps} />
        <div style={{
          padding: '12px 34px', borderRadius: 999, background: 'rgba(16,24,36,0.8)', border: `2px solid ${BRAND.sky}`,
          fontFamily: headline, fontSize: 54, color: BRAND.white, textShadow: extrude(4),
          transform: `translateY(${(1 - tag) * 60}px)`, opacity: tag,
        }}>BiPath</div>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};

const MaskedHostIntro = ({ logo, hostClip }) => {
  const frame = useCurrentFrame();
  const { fps, durationInFrames } = useVideoConfig();
  const jitter = frame < 8 ? (random(`j${frame}`) - 0.5) * 60 : 0;
  const flash = interpolate(frame, [0, 2, 7], [0.9, 0.5, 0], clamp);
  const slide = spring({ frame: frame - 20, fps, config: { damping: 15, stiffness: 120 } });
  const push = interpolate(frame, [0, durationInFrames], [1.04, 1.12]);
  return (
    <AbsoluteFill style={{ backgroundColor: '#000', opacity: fadeOut(frame, durationInFrames) }}>
      <AbsoluteFill style={{ transform: `translateX(${jitter}px) scale(${push})` }}>
        <OffthreadVideo src={hostClip} muted style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
      </AbsoluteFill>
      <AbsoluteFill style={{ background: 'linear-gradient(0deg, rgba(5,7,11,0.85) 0%, rgba(5,7,11,0) 45%)' }} />
      <AbsoluteFill style={{ backgroundColor: '#fff', opacity: flash }} />
      <div style={{
        position: 'absolute', left: 90, bottom: 90, display: 'flex', alignItems: 'center', gap: 26,
        transform: `translateX(${(1 - slide) * -700}px)`,
      }}>
        <Img src={logo} style={{ width: 130, height: 130, borderRadius: '50%', objectFit: 'cover', background: '#fff', border: `4px solid ${BRAND.sky}` }} />
        <div>
          <div style={{ fontFamily: headline, fontSize: 84, color: BRAND.white, textShadow: extrude(6), lineHeight: 1 }}>BiPath</div>
          <div style={{ height: 5, width: `${slide * 100}%`, background: BRAND.sky, borderRadius: 4, margin: '10px 0' }} />
          <div style={{ fontFamily: body, fontWeight: 600, fontSize: 30, color: 'rgba(240,240,240,0.9)' }}>
            where every story has two sides
          </div>
        </div>
      </div>
    </AbsoluteFill>
  );
};

const GlitchIntro = ({ logo }) => {
  const frame = useCurrentFrame();
  const { fps, durationInFrames } = useVideoConfig();
  const glitching = frame < 36;
  const smash = spring({ frame: frame - 36, fps, config: { damping: 9, stiffness: 200 } });
  const bars = glitching ? Array.from({ length: 7 }).map((_, i) => ({
    top: random(`t${frame}-${i}`) * 1080, h: 6 + random(`h${frame}-${i}`) * 50, x: (random(`x${frame}-${i}`) - 0.5) * 200,
    c: i % 3 === 0 ? 'rgba(255,40,80,0.55)' : i % 3 === 1 ? 'rgba(80,220,255,0.55)' : 'rgba(255,255,255,0.35)',
  })) : [];
  const shake = glitching ? (random(`s${frame}`) - 0.5) * 40 : 0;
  const visible = glitching ? (random(`v${frame}`) > 0.35 ? 1 : 0.15) : 1;
  return (
    <AbsoluteFill style={{ backgroundColor: '#05070b', opacity: fadeOut(frame, durationInFrames, 12) }}>
      <AbsoluteFill style={{ alignItems: 'center', justifyContent: 'center', flexDirection: 'column', gap: 30 }}>
        <div style={{ position: 'relative', width: 300, height: 300, opacity: visible, transform: `translateX(${shake}px) scale(${glitching ? 1 : 1.25 - 0.25 * smash})` }}>
          {glitching && <Img src={logo} style={{ position: 'absolute', inset: 0, width: 300, height: 300, borderRadius: '50%', objectFit: 'cover', transform: 'translateX(-10px)', opacity: 0.6, filter: 'sepia(1) saturate(8) hue-rotate(-50deg)' }} />}
          {glitching && <Img src={logo} style={{ position: 'absolute', inset: 0, width: 300, height: 300, borderRadius: '50%', objectFit: 'cover', transform: 'translateX(10px)', opacity: 0.6, filter: 'sepia(1) saturate(8) hue-rotate(160deg)' }} />}
          <Img src={logo} style={{ position: 'absolute', inset: 0, width: 300, height: 300, borderRadius: '50%', objectFit: 'cover', background: '#fff' }} />
        </div>
        <div style={{
          fontFamily: headline, fontSize: 120, color: BRAND.white, textShadow: extrude(8),
          opacity: glitching ? 0 : 1, transform: `scale(${1.5 - 0.5 * smash})`,
        }}>BiPath</div>
      </AbsoluteFill>
      {bars.map((b, i) => (
        <div key={i} style={{ position: 'absolute', left: b.x, top: b.top, width: 1920, height: b.h, background: b.c, mixBlendMode: 'screen' }} />
      ))}
    </AbsoluteFill>
  );
};

/* ------------------------------------------------------------------ outros */

/** YouTube end-screen places: two next-video boxes and the subscribe circle (kept empty of anything important). */
const SLOTS = [{ x: 208, y: 300, w: 704, h: 396 }, { x: 1008, y: 300, w: 704, h: 396 }];
const SUB = { x: 960, y: 860, r: 95 };

const Slot = ({ slot, frame, fps, delay, label }) => {
  const s = spring({ frame: frame - delay, fps, config: { damping: 15, stiffness: 110 } });
  const shimmer = (frame * 6) % 1400;
  return (
    <div style={{
      position: 'absolute', left: slot.x, top: slot.y, width: slot.w, height: slot.h, borderRadius: 18,
      border: '2px solid rgba(112,192,224,0.55)', overflow: 'hidden', opacity: s,
      transform: `translateY(${(1 - s) * 80}px)`, background: 'rgba(10,16,26,0.55)', boxShadow: '0 20px 50px rgba(0,0,0,0.5)',
    }}>
      <div style={{
        position: 'absolute', top: 0, left: shimmer - 300, width: 200, height: '100%',
        background: 'linear-gradient(90deg, rgba(255,255,255,0) 0%, rgba(255,255,255,0.06) 50%, rgba(255,255,255,0) 100%)',
      }} />
      <div style={{
        position: 'absolute', left: 22, bottom: 18, fontFamily: body, fontWeight: 700, fontSize: 22,
        color: 'rgba(240,240,240,0.55)', letterSpacing: '0.2em',
      }}>{label}</div>
    </div>
  );
};

const EndLayout = ({ logo, frame, fps, start = 0, heading = 'Keep watching' }) => {
  const f = frame - start;
  const pop = spring({ frame: f - 18, fps, config: { damping: 11, stiffness: 140 } });
  const head = interpolate(f, [0, 15], [0, 1], clamp);
  return (
    <>
      <div style={{
        position: 'absolute', top: 150, width: '100%', textAlign: 'center', fontFamily: headline, fontSize: 64,
        color: BRAND.white, textShadow: extrude(5), opacity: head, transform: `translateY(${(1 - head) * -30}px)`,
      }}>{heading}</div>
      <Slot slot={SLOTS[0]} frame={f} fps={fps} delay={4} label="NEXT STORY" />
      <Slot slot={SLOTS[1]} frame={f} fps={fps} delay={10} label="YOU'LL LIKE THIS" />
      <div style={{ position: 'absolute', left: SUB.x - SUB.r - 22, top: SUB.y - SUB.r - 22, transform: `scale(${pop})` }}>
        <LogoDisc logo={logo} size={SUB.r * 2} frame={frame} />
      </div>
    </>
  );
};

const EndScreenOutro = ({ logo }) => {
  const frame = useCurrentFrame();
  const { fps, durationInFrames } = useVideoConfig();
  return (
    <AbsoluteFill style={{ opacity: fadeOut(frame, durationInFrames, 20) }}>
      <Night snow={80} />
      <EndLayout logo={logo} frame={frame} fps={fps} />
    </AbsoluteFill>
  );
};

/**
 * The outro: the intro's two paths come back from the left and meet in one point, the meeting explodes (flash,
 * shockwaves, sparks) and the logo is born from it; the subscribe pill is clicked under it; then the logo flies into
 * the end-screen circle. No talking mouth. beats (video frames, from the voice): bang = the paths meet,
 * subscribe = the pill comes in, fly = the logo goes to the end screen.
 */
const PathsOutro = ({ logo, beats }) => {
  const frame = useCurrentFrame();
  const { fps, durationInFrames } = useVideoConfig();
  const b = { bang: 150, subscribe: 171, fly: 312, ...beats };
  const M = [960, 450];
  const glide = { ...clamp, easing: Easing.inOut(Easing.cubic) };
  // The paths draw in two moves: halfway while "that's it for today", the rest into the meeting point.
  const draw = interpolate(frame, [4, 48], [0, 0.55], glide) + interpolate(frame, [b.bang - 48, b.bang - 6], [0, 0.45], glide);
  const len = 1250;
  const upper = `M -40 170 C 420 170, 640 ${M[1]}, ${M[0]} ${M[1]}`;
  const lower = `M -40 910 C 420 910, 640 ${M[1]}, ${M[0]} ${M[1]}`;
  const pathsOut = interpolate(frame, [b.bang, b.bang + 10], [1, 0], clamp);
  const pulse = (phase) => 0.5 + 0.5 * Math.max(0, Math.sin(frame / 9 + phase));
  const labels = interpolate(frame, [56, 70], [0, 1], clamp) * pathsOut;
  // Light gathers where the paths meet, then the bang.
  const charge = interpolate(frame, [b.bang - 14, b.bang], [0, 1], { ...clamp, easing: Easing.in(Easing.quad) }) * pathsOut;
  const t = frame - b.bang;
  const flash = interpolate(t, [0, 2, 14], [0, 0.95, 0], clamp);
  const shake = t >= 0 && t < 12 ? (random(`k${frame}`) - 0.5) * 34 * (1 - t / 12) : 0;
  const born = spring({ frame: t - 2, fps, config: { damping: 9, stiffness: 120, mass: 0.8 } });
  const fly = interpolate(frame, [b.fly, b.fly + 24], [0, 1], glide);
  const logoSize = 340 - (340 - SUB.r * 2) * fly;
  const lx = M[0];
  const ly = M[1] + (SUB.y - M[1]) * fly;
  const float = Math.sin(frame / 14) * 5 * (1 - fly);
  return (
    <AbsoluteFill style={{ opacity: fadeOut(frame, durationInFrames, 20) }}>
      <Night snow={80} />
      <AbsoluteFill style={{ transform: `translate(${shake}px, ${shake * 0.6}px)` }}>
        <svg width={1920} height={1080} style={{ position: 'absolute', inset: 0, overflow: 'visible' }}>
          <g opacity={pathsOut}>
            <path d={upper} fill="none" stroke={BRAND.sky} strokeWidth={8} strokeLinecap="round" strokeDasharray={len}
              strokeDashoffset={(1 - draw) * len} style={{ filter: `drop-shadow(0 0 ${8 + 16 * pulse(0)}px ${BRAND.sky})` }} />
            <path d={lower} fill="none" stroke="#fff" strokeWidth={8} strokeLinecap="round" strokeDasharray={len}
              strokeDashoffset={(1 - draw) * len}
              style={{ filter: `drop-shadow(0 0 ${8 + 16 * pulse(Math.PI)}px rgba(255,255,255,0.9))` }} />
          </g>
          {charge > 0 && (
            <circle cx={M[0]} cy={M[1]} r={10 + 60 * charge} fill="#fff" opacity={0.9 * charge}
              style={{ filter: `drop-shadow(0 0 ${30 * charge}px ${BRAND.sky}) drop-shadow(0 0 ${60 * charge}px #fff)` }} />
          )}
          {[0, 5, 11].map((delay, i) => {
            const k = interpolate(t - delay, [0, 34], [0, 1], { ...clamp, easing: Easing.out(Easing.cubic) });
            return t - delay >= 0 && k < 1 ? (
              <circle key={i} cx={M[0]} cy={M[1]} r={40 + 1000 * k} fill="none"
                stroke={i === 1 ? BRAND.sky : '#fff'} strokeWidth={14 * (1 - k) + 1} opacity={0.85 * (1 - k)} />
            ) : null;
          })}
          {Array.from({ length: 46 }).map((_, i) => {
            const angle = random(`a${i}`) * Math.PI * 2;
            const speed = 300 + random(`v${i}`) * 700;
            const k = interpolate(t, [0, 40], [0, 1], { ...clamp, easing: Easing.out(Easing.cubic) });
            const r = 2 + random(`r${i}`) * 5;
            return t >= 0 && k < 1 ? (
              <circle key={`p${i}`} cx={M[0] + Math.cos(angle) * speed * k} cy={M[1] + Math.sin(angle) * speed * k}
                r={r * (1 - 0.6 * k)} fill={i % 3 === 0 ? BRAND.sky : '#fff'} opacity={1 - k} />
            ) : null;
          })}
        </svg>
        {['PATH 1', 'PATH 2'].map((text, i) => (
          <div key={text} style={{
            position: 'absolute', left: 70, top: i === 0 ? 92 : 945, padding: '8px 20px', borderRadius: 999,
            fontFamily: headline, fontSize: 30, color: '#0b0f17', background: i === 0 ? BRAND.sky : '#f0f0f0',
            opacity: labels, boxShadow: `0 0 ${10 + 24 * pulse(i * Math.PI)}px ${i === 0 ? BRAND.sky : '#ffffff'}`,
          }}>{text}</div>
        ))}
        {t >= 0 && fly < 1 && (
          <div style={{
            position: 'absolute', left: lx - logoSize / 2 - 22, top: ly - logoSize / 2 - 22 + float,
            transform: `scale(${born})`,
          }}>
            <LogoDisc logo={logo} size={logoSize} frame={frame} glow={1 - fly * 0.6} />
          </div>
        )}
      </AbsoluteFill>
      <Sequence from={b.subscribe} durationInFrames={NUDGE_FRAMES} layout="none">
        <AbsoluteFill style={{ transform: 'translate(-500px, -175px)' }}>
          <SubscribeNudge variant="glass" logo={logo} />
        </AbsoluteFill>
      </Sequence>
      {frame >= b.fly && (
        <EndLayout logo={logo} frame={frame} fps={fps} start={b.fly + 10} heading="See you on the next path" />
      )}
      <AbsoluteFill style={{ backgroundColor: '#fff', opacity: flash, pointerEvents: 'none' }} />
    </AbsoluteFill>
  );
};

const MaskedHostOutro = ({ logo, hostClip }) => {
  const frame = useCurrentFrame();
  const { fps, durationInFrames } = useVideoConfig();
  const panel = spring({ frame, fps, config: { damping: 16, stiffness: 100 } });
  const left = [{ x: 110, y: 150, w: 640, h: 360 }, { x: 110, y: 570, w: 640, h: 360 }];
  const pop = spring({ frame: frame - 24, fps, config: { damping: 11, stiffness: 140 } });
  return (
    <AbsoluteFill style={{ opacity: fadeOut(frame, durationInFrames, 20) }}>
      <Night snow={70} />
      <div style={{
        position: 'absolute', left: 840, top: 150, width: 970, height: 546, borderRadius: 24, overflow: 'hidden',
        border: `3px solid ${BRAND.sky}`, boxShadow: '0 24px 60px rgba(0,0,0,0.6)',
        transform: `translateX(${(1 - panel) * 900}px)`,
      }}>
        <OffthreadVideo src={hostClip} muted style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
      </div>
      <Slot slot={left[0]} frame={frame} fps={fps} delay={8} label="NEXT STORY" />
      <Slot slot={left[1]} frame={frame} fps={fps} delay={14} label="YOU'LL LIKE THIS" />
      <div style={{ position: 'absolute', left: 1325 - SUB.r - 22, top: 850 - SUB.r - 22, transform: `scale(${pop})` }}>
        <LogoDisc logo={logo} size={SUB.r * 2} frame={frame} />
      </div>
    </AbsoluteFill>
  );
};

const CinematicOutro = ({ logo }) => {
  const frame = useCurrentFrame();
  const { fps, durationInFrames } = useVideoConfig();
  const lines = [{ text: 'Every story has two paths.', at: 15 }, { text: 'Thanks for walking this one with me.', at: 72 }];
  const textOut = interpolate(frame, [150, 168], [1, 0], clamp);
  const layout = 160;
  return (
    <AbsoluteFill style={{ backgroundColor: '#000', opacity: fadeOut(frame, durationInFrames, 20) }}>
      {frame >= layout && <AbsoluteFill style={{ opacity: interpolate(frame, [layout, layout + 20], [0, 1], clamp) }}><Night snow={60} /></AbsoluteFill>}
      <AbsoluteFill style={{ alignItems: 'center', justifyContent: 'center', flexDirection: 'column', gap: 24, opacity: textOut }}>
        {lines.map((line) => {
          const shown = Math.floor(interpolate(frame, [line.at, line.at + line.text.length * 1.4], [0, line.text.length], clamp));
          return (
            <div key={line.text} style={{ fontFamily: body, fontWeight: 600, fontSize: 52, color: BRAND.white, letterSpacing: '0.02em', minHeight: 64 }}>
              {line.text.slice(0, shown)}
              {shown > 0 && shown < line.text.length && <span style={{ color: BRAND.sky }}>|</span>}
            </div>
          );
        })}
      </AbsoluteFill>
      {frame >= layout && <EndLayout logo={logo} frame={frame} fps={fps} start={layout} heading="See you on the next path" />}
    </AbsoluteFill>
  );
};

const INTROS = { logo_reveal: LogoReveal, two_paths: TwoPaths, talking_logo: TalkingIntro, masked_host: MaskedHostIntro, glitch: GlitchIntro };
const OUTROS = { end_screen: EndScreenOutro, paths: PathsOutro, masked_host: MaskedHostOutro, cinematic: CinematicOutro };

export const ChannelIdent = ({ kind = 'intro', variant, ...props }) => {
  const Variant = (kind === 'outro' ? OUTROS : INTROS)[variant] || (kind === 'outro' ? EndScreenOutro : LogoReveal);
  return <Variant {...props} />;
};
