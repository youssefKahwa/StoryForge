import { createAction, createSlice } from '@reduxjs/toolkit';

export const createInspiration = createAction('wizard/createInspiration');
export const fetchInspiration = createAction('wizard/fetchInspiration');
export const fetchVoices = createAction('wizard/fetchVoices');

const wizardSlice = createSlice({
  name: 'wizard',
  initialState: { inspiration: null, voices: [] },
  reducers: {
    setInspiration: (state, { payload }) => {
      state.inspiration = payload;
    },
    setVoices: (state, { payload }) => {
      state.voices = payload;
    },
  },
});

export const { setInspiration, setVoices } = wizardSlice.actions;

export const selectInspiration = (state) => state.wizard.inspiration;
export const selectVoices = (state) => state.wizard.voices;

export default wizardSlice.reducer;
