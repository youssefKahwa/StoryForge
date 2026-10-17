import { createAction, createSlice } from '@reduxjs/toolkit';

export const fetchSettings = createAction('settings/fetch');
export const setKillSwitch = createAction('settings/killSwitch');

const settingsSlice = createSlice({
  name: 'settings',
  initialState: { data: null },
  reducers: {
    setSettings: (state, { payload }) => {
      state.data = payload;
    },
  },
});

export const { setSettings } = settingsSlice.actions;

export const selectSettings = (state) => state.settings.data;

export default settingsSlice.reducer;
