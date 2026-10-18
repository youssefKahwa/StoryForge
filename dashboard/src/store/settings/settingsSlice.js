import { createAction, createSlice } from '@reduxjs/toolkit';

export const fetchSettings = createAction('settings/fetch');
export const setKillSwitch = createAction('settings/killSwitch');
export const fetchYouTube = createAction('settings/fetchYouTube');
export const disconnectYouTube = createAction('settings/disconnectYouTube');
export const testNotification = createAction('settings/testNotification');
export const fetchPreferences = createAction('settings/fetchPreferences');
export const savePreferences = createAction('settings/savePreferences');
export const resetPreferences = createAction('settings/resetPreferences');
export const fetchSystemCheck = createAction('settings/fetchSystemCheck');
export const fetchStorage = createAction('settings/fetchStorage');
export const cleanupStorage = createAction('settings/cleanupStorage');

const settingsSlice = createSlice({
  name: 'settings',
  initialState: {
    data: null, youtube: null, preferences: null, systemCheck: null, storage: null,
  },
  reducers: {
    setSettings: (state, { payload }) => {
      state.data = payload;
    },
    setYouTube: (state, { payload }) => {
      state.youtube = payload;
    },
    setPreferences: (state, { payload }) => {
      state.preferences = payload;
    },
    setSystemCheck: (state, { payload }) => {
      state.systemCheck = payload;
    },
    setStorage: (state, { payload }) => {
      state.storage = payload;
    },
  },
});

export const {
  setSettings, setYouTube, setPreferences, setSystemCheck, setStorage,
} = settingsSlice.actions;

export const selectSettings = (state) => state.settings.data;
export const selectYouTube = (state) => state.settings.youtube;
export const selectPreferences = (state) => state.settings.preferences;
export const selectSystemCheck = (state) => state.settings.systemCheck;
export const selectStorage = (state) => state.settings.storage;

export default settingsSlice.reducer;
