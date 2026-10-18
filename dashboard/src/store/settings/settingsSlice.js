import { createAction, createSlice } from '@reduxjs/toolkit';

export const fetchSettings = createAction('settings/fetch');
export const setKillSwitch = createAction('settings/killSwitch');
export const fetchYouTube = createAction('settings/fetchYouTube');
export const disconnectYouTube = createAction('settings/disconnectYouTube');
export const testNotification = createAction('settings/testNotification');

const settingsSlice = createSlice({
  name: 'settings',
  initialState: { data: null, youtube: null },
  reducers: {
    setSettings: (state, { payload }) => {
      state.data = payload;
    },
    setYouTube: (state, { payload }) => {
      state.youtube = payload;
    },
  },
});

export const { setSettings, setYouTube } = settingsSlice.actions;

export const selectSettings = (state) => state.settings.data;
export const selectYouTube = (state) => state.settings.youtube;

export default settingsSlice.reducer;
