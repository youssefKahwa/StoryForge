import { configureStore } from '@reduxjs/toolkit';
import createSagaMiddleware from 'redux-saga';
import runs from './runs/runsSlice';
import topics from './topics/topicsSlice';
import settings from './settings/settingsSlice';
import wizard from './wizard/wizardSlice';
import rootSaga from './rootSaga';

export const createAppStore = () => {
  const sagaMiddleware = createSagaMiddleware();
  const store = configureStore({
    reducer: { runs, topics, settings, wizard },
    middleware: (getDefault) => getDefault({ thunk: false, serializableCheck: false }).concat(sagaMiddleware),
  });
  if (typeof window !== 'undefined') {
    sagaMiddleware.run(rootSaga);
  }
  return store;
};
