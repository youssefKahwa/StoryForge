import React from 'react';
import { AbsoluteFill, interpolate, random, spring, useCurrentFrame, useVideoConfig } from 'remotion';
import { BRAND, body, extrude, headline } from '../../brand';
import { EASE, EXIT_FRAMES, SPRING, tween } from '../../motion';

/**
 * The motion kit: animated explainers in the BiPath look (white block letters with the slate extrusion, sky blue,
 * night), drawn over the blurred footage of the moment. Claude picks the template and the words; every item appears
 * at its `at` (seconds in the video), on the word the narrator says it.
 */

const RED = '#e0483c';
const STRING_RED = '#c8322a';
const PAPER = '#efe9dd';
const clamp = { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' };

const accentOf = (graphic) => (graphic.accent === 'red' ? RED : BRAND.sky);

/** Frame (inside the shot) at which item i appears. */
const atFrame = (item, shotStart, fps, fallback) => (item && item.at != null
  ? Math.max(0, Math.round((item.at - shotStart) * fps)) : fallback);

/** 0 -> 1 spring from a frame; 0 before it. */
const pop = (frame, from, fps, config = SPRING.settle) => (frame < from ? 0
  : Math.min(1, spring({ frame: frame - from, fps, config })));

/** The dimmed stage every template sits on: the footage stays, darker at the edges. */
const Stage = ({ children, leave }) => (
  <AbsoluteFill style={{
    background: 'radial-gradient(ellipse at 50% 45%, rgba(11,15,23,0.45) 0%, rgba(11,15,23,0.82) 70%, rgba(11,15,23,0.92) 100%)',
    opacity: 1 - leave,
  }}>
    {children}
  </AbsoluteFill>
);

const Title = ({ text, frame, fps, accent, top = 96 }) => {
  if (!text) return null;
  const p = pop(frame, 0, fps);
  return (
    <div style={{ position: 'absolute', top, left: 0, right: 0, textAlign: 'center' }}>
      <div style={{
        display: 'inline-block', fontFamily: headline, fontSize: 64, color: BRAND.white, letterSpacing: 2,
        textTransform: 'uppercase', textShadow: extrude(5), opacity: p, transform: `translateY(${(1 - p) * -30}px)`,
      }}>{text}</div>
      <div style={{
        margin: '18px auto 0', height: 5, borderRadius: 3, backgroundColor: accent, boxShadow: `0 0 16px ${accent}`,
        width: pop(frame, 6, fps, { damping: 30 }) * 220,
      }} />
    </div>
  );
};

// ---- versus: two sides --------------------------------------------------------------------------------------

const Versus = ({ graphic, frame, fps, shotStart }) => {
  const accent = accentOf(graphic);
  const [a, b] = graphic.items;
  const fa = atFrame(a, shotStart, fps, 8);
  const fb = atFrame(b, shotStart, fps, fa + 20);
  const divider = pop(frame, Math.max(0, fa - 4), fps, { damping: 24 });
  const badge = pop(frame, fb, fps, SPRING.pop);
  const side = (item, from, x, color, dir) => {
    const p = pop(frame, from, fps);
    const lines = (item.detail || '').split('|').map((s) => s.trim()).filter(Boolean);
    return (
      <div style={{
        position: 'absolute', top: 330, left: x - 380, width: 760, textAlign: 'center', opacity: p,
        transform: `translateX(${(1 - p) * dir * 70}px)`,
      }}>
        <div style={{ fontFamily: headline, fontSize: 92, color, textTransform: 'uppercase', textShadow: extrude(7) }}>
          {item.label}
        </div>
        {lines.map((line, i) => {
          const q = pop(frame, from + 8 + i * 6, fps);
          return (
            <div key={line} style={{
              fontFamily: body, fontWeight: 600, fontSize: 40, color: BRAND.white, marginTop: i === 0 ? 34 : 14,
              opacity: q, transform: `translateY(${(1 - q) * 18}px)`,
            }}>{line}</div>
          );
        })}
      </div>
    );
  };
  return (
    <>
      <Title text={graphic.title} frame={frame} fps={fps} accent={accent} />
      <div style={{
        position: 'absolute', left: 958, top: 300, width: 4, height: 520 * divider, borderRadius: 2,
        backgroundColor: 'rgba(240,240,240,0.55)',
      }} />
      {side(a, fa, 520, BRAND.white, -1)}
      {side(b, fb, 1400, accent, 1)}
      <div style={{
        position: 'absolute', left: 960 - 56, top: 500, width: 112, height: 112, borderRadius: 56,
        backgroundColor: BRAND.night, border: `4px solid ${accent}`, boxShadow: `0 0 30px ${accent}`,
        display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: headline, fontSize: 42,
        color: BRAND.white, transform: `scale(${badge})`,
      }}>VS</div>
    </>
  );
};

// ---- chain: cause -> effect ---------------------------------------------------------------------------------

const Chain = ({ graphic, frame, fps, shotStart }) => {
  const accent = accentOf(graphic);
  const items = graphic.items;
  const n = items.length;
  const gap = 1640 / n;
  const width = Math.min(320, gap - 70);
  const xs = items.map((_, i) => 140 + gap * i + gap / 2);
  const froms = items.map((item, i) => atFrame(item, shotStart, fps, 8 + i * 22));
  const y = 560;
  return (
    <>
      <Title text={graphic.title} frame={frame} fps={fps} accent={accent} />
      <svg width={1920} height={1080} style={{ position: 'absolute', inset: 0 }}>
        {items.slice(1).map((_, k) => {
          const i = k + 1;
          const x1 = xs[i - 1] + width / 2 + 10;
          const x2 = xs[i] - width / 2 - 18;
          const draw = interpolate(frame, [froms[i] - 10, froms[i]], [0, 1], { ...clamp, easing: EASE.move });
          const tip = x1 + (x2 - x1) * draw;
          return (
            <g key={`arrow-${i}`} opacity={draw > 0 ? 1 : 0}>
              <line x1={x1} y1={y} x2={tip} y2={y} stroke={accent} strokeWidth={6} strokeLinecap="round" />
              <polygon points={`${tip},${y - 16} ${tip + 22},${y} ${tip},${y + 16}`} fill={accent}
                opacity={draw >= 1 ? 1 : 0} />
            </g>
          );
        })}
      </svg>
      {items.map((item, i) => {
        const p = pop(frame, froms[i], fps);
        const latest = froms.filter((f) => f <= frame).length - 1 === i;
        return (
          <div key={`${item.label}-${i}`} style={{
            position: 'absolute', left: xs[i] - width / 2, top: y - 80, width, minHeight: 160, boxSizing: 'border-box',
            padding: '22px 18px', borderRadius: 18, backgroundColor: 'rgba(11,15,23,0.78)',
            border: `3px solid ${latest ? accent : 'rgba(112,192,224,0.45)'}`,
            boxShadow: latest ? `0 0 34px ${accent}` : '0 10px 30px rgba(0,0,0,0.5)',
            display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', textAlign: 'center',
            opacity: p, transform: `translateY(${(1 - p) * 40}px) scale(${0.9 + p * 0.1})`,
          }}>
            <div style={{
              fontFamily: body, fontWeight: 800, fontSize: n > 4 ? 32 : 38, color: BRAND.white,
              textTransform: 'uppercase', lineHeight: 1.15,
            }}>{item.label}</div>
            {item.detail ? (
              <div style={{ fontFamily: body, fontWeight: 500, fontSize: 24, color: BRAND.sky, marginTop: 10 }}>
                {item.detail}
              </div>
            ) : null}
          </div>
        );
      })}
    </>
  );
};

// ---- list: rules, points -------------------------------------------------------------------------------------

const Mark = ({ kind, progress, x, y }) => {
  if (kind !== 'check' && kind !== 'cross') return null;
  const color = kind === 'check' ? BRAND.sky : RED;
  const length = 90;
  const dash = length * (1 - progress);
  return (
    <svg width={70} height={70} style={{ position: 'absolute', left: x, top: y }}>
      {kind === 'check'
        ? <polyline points="10,38 28,56 60,16" fill="none" stroke={color} strokeWidth={9} strokeLinecap="round"
          strokeLinejoin="round" strokeDasharray={length} strokeDashoffset={dash} />
        : (
          <>
            <line x1={14} y1={14} x2={56} y2={56} stroke={color} strokeWidth={9} strokeLinecap="round"
              strokeDasharray={60} strokeDashoffset={60 * (1 - Math.min(1, progress * 2))} />
            <line x1={56} y1={14} x2={14} y2={56} stroke={color} strokeWidth={9} strokeLinecap="round"
              strokeDasharray={60} strokeDashoffset={60 * (1 - Math.max(0, progress * 2 - 1))} />
          </>
        )}
    </svg>
  );
};

const List = ({ graphic, frame, fps, shotStart }) => {
  const accent = accentOf(graphic);
  const items = graphic.items;
  const rowHeight = items.length > 4 ? 112 : 128;
  const top = 540 - (items.length * rowHeight) / 2 + 40;
  return (
    <>
      <Title text={graphic.title} frame={frame} fps={fps} accent={accent} />
      {items.map((item, i) => {
        const from = atFrame(item, shotStart, fps, 8 + i * 18);
        const p = pop(frame, from, fps);
        const mark = tween(frame, from + 8, from + 20, EASE.enter);
        return (
          <div key={`${item.label}-${i}`} style={{
            position: 'absolute', left: 330, top: top + i * rowHeight, width: 1260, height: rowHeight - 24,
            display: 'flex', alignItems: 'center', opacity: p, transform: `translateX(${(1 - p) * -60}px)`,
          }}>
            <div style={{
              width: 76, height: 76, borderRadius: 38, flexShrink: 0, backgroundColor: accent,
              display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: headline, fontSize: 40,
              color: BRAND.night, boxShadow: `0 0 22px ${accent}`,
            }}>{i + 1}</div>
            <div style={{
              position: 'relative', marginLeft: 36, fontFamily: body, fontWeight: 700, fontSize: 52,
              color: BRAND.white, textShadow: '0 4px 18px rgba(0,0,0,0.6)',
            }}>
              {item.label}
              {item.detail === 'cross' ? (
                <div style={{
                  position: 'absolute', left: 0, top: '52%', height: 6, borderRadius: 3, backgroundColor: RED,
                  width: `${mark * 100}%`,
                }} />
              ) : null}
            </div>
            <div style={{ position: 'relative', marginLeft: 26, width: 70, height: 70 }}>
              <Mark kind={item.detail} progress={mark} x={0} y={0} />
            </div>
          </div>
        );
      })}
    </>
  );
};

// ---- stat: a number that counts ---------------------------------------------------------------------------------

const countUp = (label, progress) => {
  const m = /^([^\d]*)([\d][\d.,]*)(.*)$/.exec(label || '');
  if (!m) return label;
  const raw = m[2].replace(/,/g, '');
  const value = parseFloat(raw);
  if (Number.isNaN(value)) return label;
  const decimals = raw.includes('.') ? raw.split('.')[1].length : 0;
  const isYear = decimals === 0 && value >= 1000 && value <= 2100 && !m[2].includes(',');
  if (isYear) return label;
  const shown = (value * progress).toFixed(decimals);
  const withCommas = m[2].includes(',') ? Number(shown).toLocaleString('en-US', { minimumFractionDigits: decimals }) : shown;
  return `${m[1]}${withCommas}${m[3]}`;
};

const Stat = ({ graphic, frame, fps, shotStart }) => {
  const accent = accentOf(graphic);
  const items = graphic.items;
  const xs = items.length === 1 ? [960] : [600, 1320];
  return (
    <>
      <Title text={graphic.title} frame={frame} fps={fps} accent={accent} />
      {items.map((item, i) => {
        const from = atFrame(item, shotStart, fps, 8 + i * 24);
        const p = pop(frame, from, fps, SPRING.pop);
        const count = tween(frame, from, from + 26, EASE.enter);
        const ring = tween(frame, from, from + 30, EASE.enter);
        return (
          <div key={`${item.label}-${i}`} style={{
            position: 'absolute', left: xs[i] - 500, top: 300, width: 1000, textAlign: 'center', opacity: Math.min(1, p * 1.3),
          }}>
            <div style={{
              position: 'absolute', left: 500 - 230, top: 40, width: 460, height: 460, borderRadius: 230,
              border: `4px solid ${accent}`, opacity: (1 - ring) * 0.8, transform: `scale(${0.6 + ring * 0.7})`,
            }} />
            <div style={{
              fontFamily: headline, fontSize: items.length === 1 ? 250 : 190, color: BRAND.white, lineHeight: 1.05,
              textShadow: extrude(12), transform: `scale(${0.7 + p * 0.3})`,
            }}>{countUp(item.label, count)}</div>
            {item.detail ? (
              <div style={{
                fontFamily: body, fontWeight: 700, fontSize: 46, color: accent, textTransform: 'uppercase',
                letterSpacing: 3, marginTop: 18, opacity: tween(frame, from + 10, from + 24),
              }}>{item.detail}</div>
            ) : null}
          </div>
        );
      })}
      {items.length === 2 ? (
        <div style={{
          position: 'absolute', left: 930, top: 470, fontFamily: headline, fontSize: 54, color: BRAND.sky,
          opacity: pop(frame, atFrame(items[1], shotStart, fps, 32), fps),
        }}>VS</div>
      ) : null}
    </>
  );
};

// ---- timeline -------------------------------------------------------------------------------------------------

const Timeline = ({ graphic, frame, fps, shotStart }) => {
  const accent = accentOf(graphic);
  const items = graphic.items;
  const n = items.length;
  const spacing = n <= 4 ? 1400 / Math.max(1, n - 1) : 400;
  const xs = items.map((_, i) => (n <= 4 ? 260 + spacing * i : 360 + spacing * i));
  const froms = items.map((item, i) => atFrame(item, shotStart, fps, 8 + i * 20));
  const y = 590;
  const active = Math.max(0, froms.filter((f) => f <= frame).length - 1);
  // Long timelines pan so the newest point stays in view.
  let pan = 0;
  if (n > 4) {
    const target = (i) => Math.min(0, Math.max(-(xs[n - 1] - 1560), 960 - xs[i]));
    const prev = target(Math.max(0, active - 1));
    const next = target(active);
    pan = interpolate(frame, [froms[active], froms[active] + 14], [prev, next], { ...clamp, easing: EASE.move });
  }
  const lineTo = (() => {
    const i = active;
    const start = i === 0 ? xs[0] - 120 : xs[i - 1];
    return interpolate(frame, [froms[i] - 12, froms[i]], [start, xs[i]], { ...clamp, easing: EASE.move });
  })();
  return (
    <>
      <Title text={graphic.title} frame={frame} fps={fps} accent={accent} />
      <div style={{ position: 'absolute', inset: 0, transform: `translateX(${pan}px)` }}>
        <div style={{
          position: 'absolute', left: xs[0] - 120, top: y - 3, height: 6, borderRadius: 3,
          width: Math.max(0, lineTo - (xs[0] - 120)), backgroundColor: 'rgba(240,240,240,0.7)',
        }} />
        {items.map((item, i) => {
          const p = pop(frame, froms[i], fps, SPRING.pop);
          const q = pop(frame, froms[i] + 4, fps);
          return (
            <React.Fragment key={`${item.label}-${i}`}>
              <div style={{
                position: 'absolute', left: xs[i] - 17, top: y - 17, width: 34, height: 34, borderRadius: 17,
                backgroundColor: i === active ? accent : BRAND.white, boxShadow: `0 0 ${i === active ? 26 : 8}px ${accent}`,
                transform: `scale(${p})`,
              }} />
              <div style={{
                position: 'absolute', left: xs[i] - 190, top: y - 140, width: 380, textAlign: 'center',
                fontFamily: headline, fontSize: 56, color: i === active ? BRAND.white : 'rgba(240,240,240,0.75)',
                textShadow: extrude(5), opacity: q, transform: `translateY(${(1 - q) * 24}px)`,
              }}>{item.label}</div>
              {item.detail ? (
                <div style={{
                  position: 'absolute', left: xs[i] - 190, top: y + 44, width: 380, textAlign: 'center',
                  fontFamily: body, fontWeight: 600, fontSize: 32, color: BRAND.white, lineHeight: 1.25,
                  opacity: q * (i === active ? 1 : 0.7), transform: `translateY(${(1 - q) * -18}px)`,
                }}>{item.detail}</div>
              ) : null}
            </React.Fragment>
          );
        })}
      </div>
    </>
  );
};

// ---- evidence board: the twist's payoff -------------------------------------------------------------------------

/** Card centres on the board, by how many clues there are, so the board always looks full and balanced. */
const BOARDS = {
  3: [[470, 470], [960, 400], [1450, 480]],
  4: [[640, 360], [1290, 340], [620, 730], [1300, 740]],
  5: [[430, 340], [960, 310], [1490, 350], [700, 730], [1230, 740]],
  6: [[430, 330], [960, 300], [1490, 340], [520, 720], [1000, 740], [1430, 710]],
};

const Evidence = ({ graphic, frame, fps, shotStart }) => {
  const items = graphic.items;
  const froms = items.map((item, i) => atFrame(item, shotStart, fps, 8 + i * 18));
  const board = BOARDS[Math.min(6, Math.max(3, items.length))];
  const spots = items.map((_, i) => {
    const [x, y] = board[i % board.length];
    return [x + (random(`ex${i}`) - 0.5) * 60, y + (random(`ey${i}`) - 0.5) * 40, (random(`er${i}`) - 0.5) * 9];
  });
  const latest = froms.filter((f) => f <= frame).length - 1;
  return (
    <>
      <AbsoluteFill style={{
        background: 'repeating-linear-gradient(35deg, rgba(255,255,255,0.018) 0 2px, transparent 2px 9px), '
          + 'radial-gradient(ellipse at 50% 50%, rgba(48,72,96,0.35), rgba(11,15,23,0.75))',
      }} />
      <Title text={graphic.title} frame={frame} fps={fps} accent={RED} top={60} />
      <svg width={1920} height={1080} style={{ position: 'absolute', inset: 0 }}>
        {items.slice(1).map((_, k) => {
          const i = k + 1;
          const [x1, y1] = spots[i - 1];
          const [x2, y2] = spots[i];
          const draw = interpolate(frame, [froms[i] - 8, froms[i] + 4], [0, 1], { ...clamp, easing: EASE.move });
          const length = Math.hypot(x2 - x1, y2 - y1);
          return (
            <line key={`string-${i}`} x1={x1} y1={y1 - 78} x2={x2} y2={y2 - 78} stroke={STRING_RED} strokeWidth={4}
              strokeDasharray={length} strokeDashoffset={length * (1 - draw)} opacity={0.95} />
          );
        })}
      </svg>
      {items.map((item, i) => {
        const p = pop(frame, froms[i], fps, SPRING.pop);
        const [x, y, rot] = spots[i];
        const glow = i === latest ? tween(frame, froms[i], froms[i] + 10) : 0;
        return (
          <div key={`${item.label}-${i}`} style={{
            position: 'absolute', left: x - 200, top: y - 95, width: 400, minHeight: 170, boxSizing: 'border-box',
            padding: '40px 26px 26px', backgroundColor: PAPER, borderRadius: 4,
            boxShadow: `0 18px 40px rgba(0,0,0,0.55)${glow ? `, 0 0 ${40 * glow}px rgba(240,200,120,${0.8 * glow})` : ''}`,
            opacity: Math.min(1, p * 1.5), transform: `rotate(${rot}deg) scale(${1.25 - p * 0.25})`,
            display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', textAlign: 'center',
          }}>
            <div style={{
              position: 'absolute', top: 10, left: 186, width: 28, height: 28, borderRadius: 14,
              background: `radial-gradient(circle at 35% 35%, #ff7a6e, ${STRING_RED})`, boxShadow: '0 3px 6px rgba(0,0,0,0.5)',
            }} />
            <div style={{ fontFamily: body, fontWeight: 800, fontSize: 38, color: '#1b1b1b', textTransform: 'uppercase', lineHeight: 1.12 }}>
              {item.label}
            </div>
            {item.detail ? (
              <div style={{ fontFamily: body, fontWeight: 500, fontSize: 24, color: '#4a4a4a', marginTop: 8 }}>{item.detail}</div>
            ) : null}
          </div>
        );
      })}
    </>
  );
};

// ---- quote ----------------------------------------------------------------------------------------------------

const Quote = ({ graphic, frame, fps, shotStart, durationInFrames }) => {
  const accent = accentOf(graphic);
  const item = graphic.items[0];
  const from = atFrame(item, shotStart, fps, 8);
  const words = item.label.split(/\s+/).filter(Boolean);
  // Words at about the pace they are spoken (0.3 s each), done well before the end so the byline can be read.
  const room = Math.max(words.length * 3, durationInFrames - from - Math.round(2.2 * fps));
  const per = Math.min(Math.round(0.3 * fps), room / Math.max(1, words.length));
  const lastWord = from + per * words.length;
  const marks = pop(frame, Math.max(0, from - 6), fps, SPRING.pop);
  const byline = tween(frame, lastWord + 6, lastWord + 16);
  return (
    <AbsoluteFill style={{ alignItems: 'center', justifyContent: 'center' }}>
      <div style={{ position: 'relative', width: 1320 }}>
        <div style={{
          position: 'absolute', left: -90, top: -150, fontFamily: headline, fontSize: 260, color: accent,
          opacity: 0.85 * marks, transform: `scale(${marks})`, lineHeight: 1,
        }}>“</div>
        <div style={{
          fontFamily: body, fontWeight: 700, fontSize: words.length > 18 ? 54 : 66, lineHeight: 1.28,
          color: BRAND.white, textShadow: '0 4px 20px rgba(0,0,0,0.6)',
        }}>
          {words.map((word, i) => {
            const p = tween(frame, from + i * per, from + i * per + 6);
            return (
              <span key={`${word}-${i}`} style={{
                opacity: p, display: 'inline-block', marginRight: 16, transform: `translateY(${(1 - p) * 12}px)`,
              }}>{word}</span>
            );
          })}
          {item.detail ? (
            <div style={{
              marginTop: 34, fontSize: 40, fontWeight: 600, color: accent, opacity: byline,
              transform: `translateX(${(1 - byline) * -20}px)`,
            }}>— {item.detail}</div>
          ) : null}
        </div>
      </div>
    </AbsoluteFill>
  );
};

// ---- merge: two that are one ------------------------------------------------------------------------------------

const Merge = ({ graphic, frame, fps, shotStart }) => {
  const accent = accentOf(graphic);
  const [a, b, c] = graphic.items;
  const fa = atFrame(a, shotStart, fps, 8);
  const fb = atFrame(b, shotStart, fps, fa + 18);
  const fc = atFrame(c, shotStart, fps, fb + 24);
  const pa = pop(frame, fa, fps);
  const pb = pop(frame, fb, fps);
  const plus = pop(frame, fb + 6, fps, SPRING.pop);
  const slide = tween(frame, fc - 10, fc + 4, EASE.move);
  const flash = interpolate(frame, [fc + 2, fc + 6, fc + 18], [0, 1, 0], clamp);
  const result = pop(frame, fc + 4, fps, SPRING.pop);
  // As the two names meet they blur and fade into the flash, so they never sit on top of each other as text.
  const name = (item, p, side) => (
    <div style={{
      position: 'absolute', top: 470, left: 960 + side * (470 * (1 - slide)) - 450, width: 900, textAlign: 'center',
      fontFamily: headline, fontSize: 104, color: side < 0 ? BRAND.white : accent, textTransform: 'uppercase',
      textShadow: extrude(8), opacity: Math.min(p, 1 - result) * (1 - 0.85 * slide),
      filter: slide > 0 ? `blur(${slide * 10}px)` : 'none',
      transform: `translateX(${(1 - p) * side * 80}px) scale(${1 - 0.15 * slide})`,
    }}>{item.label}</div>
  );
  return (
    <>
      <Title text={graphic.title} frame={frame} fps={fps} accent={accent} />
      {name(a, pa, -1)}
      {name(b, pb, 1)}
      <div style={{
        position: 'absolute', left: 930, top: 470, fontFamily: headline, fontSize: 96, color: BRAND.sky,
        opacity: plus * (1 - slide), transform: `scale(${plus})`,
      }}>+</div>
      <AbsoluteFill style={{
        background: `radial-gradient(circle at 50% 52%, rgba(255,255,255,${0.85 * flash}) 0%, rgba(112,192,224,${0.4 * flash}) 25%, transparent 60%)`,
      }} />
      <div style={{
        position: 'absolute', top: 440, left: 0, right: 0, textAlign: 'center', fontFamily: headline, fontSize: 150,
        color: BRAND.white, textTransform: 'uppercase', textShadow: `${extrude(12)}, 0 0 60px ${accent}`,
        opacity: result, transform: `scale(${0.6 + result * 0.4})`,
      }}>{c.label}</div>
    </>
  );
};

const TEMPLATES = {
  versus: Versus, chain: Chain, list: List, stat: Stat, timeline: Timeline, evidence: Evidence, quote: Quote, merge: Merge,
};

export const isMotion = (graphic) => Boolean(graphic && TEMPLATES[graphic.template]
  && Array.isArray(graphic.items) && graphic.items.length > 0);

/** shotStart: the shot's start in seconds (items carry absolute times). */
export const MotionGraphic = ({ graphic, shotStart = 0, durationInFrames }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const Template = TEMPLATES[graphic.template];
  const leave = tween(frame, durationInFrames - EXIT_FRAMES, durationInFrames - 1, EASE.exit);
  return (
    <Stage leave={leave}>
      <Template graphic={graphic} frame={frame} fps={fps} shotStart={shotStart} durationInFrames={durationInFrames} />
    </Stage>
  );
};
