import { createAction, createSlice } from '@reduxjs/toolkit';

export const fetchTimeline = createAction('revisions/fetchTimeline');
export const fetchRevisions = createAction('revisions/fetch');
export const addRevision = createAction('revisions/add');
export const deleteRevision = createAction('revisions/delete');
export const applyRevisions = createAction('revisions/apply');

const revisionsSlice = createSlice({
  name: 'revisions',
  initialState: { runId: null, timeline: null, items: [] },
  reducers: {
    setTimeline: (state, { payload }) => {
      state.runId = payload.runId;
      state.timeline = payload.timeline;
    },
    setRevisions: (state, { payload }) => {
      state.runId = payload.runId;
      state.items = payload.items;
    },
  },
});

export const { setTimeline, setRevisions } = revisionsSlice.actions;

export const selectTimeline = (runId) => (state) => (state.revisions.runId === runId ? state.revisions.timeline : null);
export const selectRevisions = (runId) => (state) => (state.revisions.runId === runId ? state.revisions.items : []);

export default revisionsSlice.reducer;
