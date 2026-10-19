import { createAction, createSlice } from '@reduxjs/toolkit';

export const fetchTopics = createAction('topics/fetch');
export const createTopic = createAction('topics/create');
export const archiveTopic = createAction('topics/archive');
export const suggestTopics = createAction('topics/suggest');

const topicsSlice = createSlice({
  name: 'topics',
  initialState: { list: [] },
  reducers: {
    setTopics: (state, { payload }) => {
      state.list = payload;
    },
  },
});

export const { setTopics } = topicsSlice.actions;

export const selectTopics = (state) => state.topics.list;

export default topicsSlice.reducer;
