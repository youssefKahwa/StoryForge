/** "1 h 05 min", "12 min", "40 s". */
export const duration = (seconds) => {
  if (seconds == null) return '–';
  const s = Math.max(0, Math.round(seconds));
  if (s < 60) return `${s} s`;
  const m = Math.round(s / 60);
  if (m < 60) return `${m} min`;
  return `${Math.floor(m / 60)} h ${String(m % 60).padStart(2, '0')} min`;
};

/** "just now", "12 min ago", "3 h ago", "yesterday", "4 days ago", then the date. */
export const relativeTime = (iso) => {
  if (!iso) return '';
  const then = new Date(iso);
  const seconds = (Date.now() - then.getTime()) / 1000;
  if (seconds < 60) return 'just now';
  if (seconds < 3600) return `${Math.round(seconds / 60)} min ago`;
  if (seconds < 86400) return `${Math.round(seconds / 3600)} h ago`;
  if (seconds < 172800) return 'yesterday';
  if (seconds < 7 * 86400) return `${Math.round(seconds / 86400)} days ago`;
  return then.toLocaleDateString([], { day: 'numeric', month: 'short', year: 'numeric' });
};

export const minutes = (targetSeconds) => (targetSeconds ? `${Math.round(targetSeconds / 60)} min` : '');

export const greeting = () => {
  const h = new Date().getHours();
  if (h < 5) return 'Working late';
  if (h < 12) return 'Good morning';
  if (h < 18) return 'Good afternoon';
  return 'Good evening';
};
