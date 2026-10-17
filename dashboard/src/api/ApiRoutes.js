const API_BASE = (process.env.GATSBY_API_URL || 'http://localhost:8080').replace(/\/$/, '');

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

  settings: () => `${API_BASE}/api/settings`,
  killSwitch: () => `${API_BASE}/api/settings/kill-switch`,
};

export default ApiRoutes;
