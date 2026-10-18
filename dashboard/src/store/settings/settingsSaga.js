import { all, call, put, takeEvery, takeLatest } from 'redux-saga/effects';
import ApiRoutes from '../../api/ApiRoutes';
import http from '../../api/http';
import { withCallbacks } from '../sagaHelpers';
import {
  cleanupStorage, disconnectYouTube, fetchPreferences, fetchSettings, fetchStorage, fetchSystemCheck, fetchYouTube,
  resetPreferences, savePreferences, setKillSwitch, setPreferences, setSettings, setStorage, setSystemCheck,
  setYouTube, testNotification,
} from './settingsSlice';

function* handleFetchSettings({ payload }) {
  yield* withCallbacks(payload, call(function* load() {
    const settings = yield call(http.get, ApiRoutes.settings());
    yield put(setSettings(settings));
    return settings;
  }));
}

function* handleKillSwitch({ payload }) {
  yield* withCallbacks(payload, call(function* toggle() {
    const settings = yield call(http.put, ApiRoutes.killSwitch(), { paused: payload.paused });
    yield put(setSettings(settings));
    return settings;
  }));
}

function* loadYouTube() {
  const status = yield call(http.get, ApiRoutes.youtubeStatus());
  yield put(setYouTube(status));
  return status;
}

function* handleFetchYouTube({ payload }) {
  yield* withCallbacks(payload, call(loadYouTube));
}

function* handleDisconnectYouTube({ payload }) {
  yield* withCallbacks(payload, call(function* disconnect() {
    yield call(http.post, ApiRoutes.youtubeDisconnect());
    return yield call(loadYouTube);
  }));
}

function* handleTestNotification({ payload }) {
  yield* withCallbacks(payload, call(http.post, ApiRoutes.testNotification()));
}

function* storePreferences(effect) {
  const preferences = yield effect;
  yield put(setPreferences(preferences));
  return preferences;
}

function* handleFetchPreferences({ payload }) {
  yield* withCallbacks(payload, call(storePreferences, call(http.get, ApiRoutes.preferences())));
}

function* handleSavePreferences({ payload }) {
  yield* withCallbacks(payload, call(storePreferences, call(http.put, ApiRoutes.preferences(), payload.preferences)));
}

function* handleResetPreferences({ payload }) {
  yield* withCallbacks(payload, call(storePreferences, call(http.post, ApiRoutes.resetPreferences())));
}

function* handleFetchSystemCheck({ payload }) {
  yield* withCallbacks(payload, call(function* load() {
    const report = yield call(http.get, ApiRoutes.systemCheck());
    yield put(setSystemCheck(report));
    return report;
  }));
}

function* loadStorage(days) {
  const storage = yield call(http.get, ApiRoutes.storage(days));
  yield put(setStorage(storage));
  return storage;
}

function* handleFetchStorage({ payload }) {
  yield* withCallbacks(payload, call(loadStorage, payload.days));
}

function* handleCleanupStorage({ payload }) {
  yield* withCallbacks(payload, call(function* clean() {
    const result = yield call(http.post, ApiRoutes.cleanup(payload.days));
    yield call(loadStorage, payload.days);
    return result;
  }));
}

export default function* settingsSaga() {
  yield all([
    takeLatest(fetchSettings.type, handleFetchSettings),
    takeEvery(setKillSwitch.type, handleKillSwitch),
    takeLatest(fetchYouTube.type, handleFetchYouTube),
    takeEvery(disconnectYouTube.type, handleDisconnectYouTube),
    takeEvery(testNotification.type, handleTestNotification),
    takeLatest(fetchPreferences.type, handleFetchPreferences),
    takeEvery(savePreferences.type, handleSavePreferences),
    takeEvery(resetPreferences.type, handleResetPreferences),
    takeLatest(fetchSystemCheck.type, handleFetchSystemCheck),
    takeLatest(fetchStorage.type, handleFetchStorage),
    takeEvery(cleanupStorage.type, handleCleanupStorage),
  ]);
}
