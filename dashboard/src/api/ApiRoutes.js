// Served by the API itself (scripts/start.ps1, port 8080): same origin. `gatsby develop` (8000): the API URL.
const SAME_ORIGIN = typeof window !== 'undefined' && window.location.port === '8080';
const API_BASE = (SAME_ORIGIN ? '' : process.env.GATSBY_API_URL || 'http://localhost:8080').replace(/\/$/, '');

const ApiRoutes = {
  base: API_BASE,
  absolute: (path) => `${API_BASE}${path}`,

  runs: () => `${API_BASE}/api/runs`,
  run: (id) => `${API_BASE}/api/runs/${id}`,
  cancelRun: (id) => `${API_BASE}/api/runs/${id}/cancel`,
  resumeRun: (id) => `${API_BASE}/api/runs/${id}/resume`,
  rerunStage: (id, stage, fresh) => `${API_BASE}/api/runs/${id}/stages/${stage}/rerun?fresh=${fresh}`,
  runFile: (id, stage, name) => `${API_BASE}/api/runs/${id}/files/${stage}/${encodeURIComponent(name)}`,

  seedTopics: () => `${API_BASE}/api/seed-topics`,
  seedTopic: (id) => `${API_BASE}/api/seed-topics/${id}`,

  inspirations: () => `${API_BASE}/api/inspirations`,
  inspiration: (id) => `${API_BASE}/api/inspirations/${id}`,

  voices: () => `${API_BASE}/api/voices`,
  voiceSample: (id) => `${API_BASE}/api/voices/${id}/sample`,

  youtubeStatus: () => `${API_BASE}/api/youtube/status`,
  youtubeConnect: () => `${API_BASE}/api/youtube/oauth/start`,
  youtubeDisconnect: () => `${API_BASE}/api/youtube/disconnect`,
  testNotification: () => `${API_BASE}/api/youtube/test-notification`,
  vetoRun: (id) => `${API_BASE}/api/runs/${id}/veto`,

  settings: () => `${API_BASE}/api/settings`,
  killSwitch: () => `${API_BASE}/api/settings/kill-switch`,
  preferences: () => `${API_BASE}/api/preferences`,
  resetPreferences: () => `${API_BASE}/api/preferences/reset`,
  systemCheck: () => `${API_BASE}/api/system/check`,
  storage: (days) => `${API_BASE}/api/system/storage?olderThanDays=${days}`,
  cleanup: (days) => `${API_BASE}/api/system/cleanup?olderThanDays=${days}`,
};

export default ApiRoutes;
