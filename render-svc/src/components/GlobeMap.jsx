import React from 'react';
import { AbsoluteFill, Easing, interpolate, spring, useCurrentFrame, useVideoConfig } from 'remotion';
import { geoGraticule10, geoOrthographic, geoPath } from 'd3-geo';
import { feature, mesh } from 'topojson-client';
import land50 from 'world-atlas/land-50m.json';
import countries50 from 'world-atlas/countries-50m.json';
import { BRAND, Snow, body, extrude, headline } from '../brand';

// Natural Earth 1:50m (public domain) via world-atlas; computed once per render tab.
const LAND = feature(land50, land50.objects.land);
const BORDERS = mesh(countries50, countries50.objects.countries, (a, b) => a !== b);
const GRATICULE = geoGraticule10();
const SPHERE = { type: 'Sphere' };

const ease = { extrapolateLeft: 'clamp', extrapolateRight: 'clamp', easing: Easing.inOut(Easing.cubic) };

const coords = (lat, lon) => `${Math.abs(lat).toFixed(2)}° ${lat >= 0 ? 'N' : 'S'} · ${Math.abs(lon).toFixed(2)}° ${lon >= 0 ? 'E' : 'W'}`;

/** Animated globe: turns to the place, zooms in, drops an evil-eye pin with the name in block letters. */
export const GlobeMap = ({ map, durationInFrames }) => {
  const frame = useCurrentFrame();
  const { width, height, fps } = useVideoConfig();
  const turn = interpolate(frame, [0, durationInFrames * 0.5], [0, 1], ease);
  const zoom = interpolate(frame, [durationInFrames * 0.3, durationInFrames * 0.78], [0, 1], ease);
  const startLon = map.lon + 75;
  const startLat = Math.max(-40, Math.min(40, map.lat - 25));
  const lon = startLon + (map.lon - startLon) * turn;
  const lat = startLat + (map.lat - startLat) * turn;
  const radius = Math.min(width, height) * 0.4;
  const projection = geoOrthographic()
    .scale(radius * Math.pow(7, zoom))
    .translate([width / 2, height / 2])
    .rotate([-lon, -lat])
    .clipAngle(90);
  const path = geoPath(projection);
  const point = projection([map.lon, map.lat]);
  const pinIn = spring({ frame: frame - Math.round(durationInFrames * 0.55), fps, config: { damping: 11, mass: 0.6 } });
  const labelIn = spring({ frame: frame - Math.round(durationInFrames * 0.62), fps, config: { damping: 18 } });
  const pulse = (frame % 45) / 45;
  return (
    <AbsoluteFill style={{ background: `radial-gradient(circle at 50% 50%, #152238 0%, ${BRAND.night} 70%)` }}>
      <Snow count={35} opacity={0.35} />
      <svg width={width} height={height} style={{ position: 'absolute', inset: 0 }}>
        <defs>
          <radialGradient id="ocean" cx="45%" cy="40%" r="65%">
            <stop offset="0%" stopColor="#1c3352" />
            <stop offset="100%" stopColor="#0c1626" />
          </radialGradient>
        </defs>
        <path d={path(SPHERE)} fill="url(#ocean)" stroke={BRAND.sky} strokeOpacity={0.35} strokeWidth={2} />
        <path d={path(GRATICULE)} fill="none" stroke={BRAND.slate} strokeOpacity={0.55} strokeWidth={0.8} />
        <path d={path(LAND)} fill="#2a4560" stroke={BRAND.sky} strokeOpacity={0.55} strokeWidth={1.2} />
        <path d={path(BORDERS)} fill="none" stroke={BRAND.white} strokeOpacity={0.18} strokeWidth={0.8} />
        {point && pinIn > 0.01 && (
          <g transform={`translate(${point[0]}, ${point[1] - (1 - pinIn) * 60})`} opacity={Math.min(1, pinIn * 1.5)}>
            <circle r={18 + pulse * 46} fill="none" stroke={BRAND.sky} strokeWidth={3} opacity={1 - pulse} />
            <circle r={18} fill={BRAND.navy} stroke={BRAND.sky} strokeWidth={6} />
            <circle r={6} fill={BRAND.white} />
          </g>
        )}
      </svg>
      {point && (
        <div style={{
          position: 'absolute',
          left: Math.min(width - 700, point[0] + 46),
          top: point[1] - 70,
          opacity: labelIn,
          transform: `translateX(${(1 - labelIn) * -30}px)`,
        }}>
          <div style={{ fontFamily: headline, fontSize: 58, color: BRAND.white, textShadow: extrude(5), textTransform: 'uppercase', whiteSpace: 'nowrap' }}>
            {map.label}
          </div>
          <div style={{ fontFamily: body, fontWeight: 700, fontSize: 24, color: BRAND.sky, letterSpacing: '0.25em', marginTop: 8 }}>
            {coords(map.lat, map.lon)}
          </div>
        </div>
      )}
      <div style={{
        position: 'absolute', bottom: 28, right: 40, fontFamily: body, fontSize: 16, color: 'rgba(240,240,240,0.55)',
      }}>
        Map: Natural Earth · Wikidata
      </div>
    </AbsoluteFill>
  );
};
