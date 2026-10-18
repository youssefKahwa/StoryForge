import { createAction, createSlice } from '@reduxjs/toolkit';

export const fetchRuns = createAction('runs/fetch');
export const fetchRun = createAction('runs/fetchOne');
export const createRun = createAction('runs/create');
export const cancelRun = createAction('runs/cancel');
export const resumeRun = createAction('runs/resume');
export const rerunStage = createAction('runs/rerunStage');
export const vetoRun = createAction('runs/veto');

const runsSlice = createSlice({
  name: 'runs',
  initialState: { list: [], detail: null, loading: false },
  reducers: {
    setRuns: (state, { payload }) => {
      state.list = payload;
    },
    setRunDetail: (state, { payload }) => {
      state.detail = payload;
    },
    setRunsLoading: (state, { payload }) => {
      state.loading = payload;
    },
  },
});

export const { setRuns, setRunDetail, setRunsLoading } = runsSlice.actions;

export const selectRuns = (state) => state.runs.list;
export const selectRunDetail = (state) => state.runs.detail;
export const selectRunsLoading = (state) => state.runs.loading;

export default runsSlice.reducer;
