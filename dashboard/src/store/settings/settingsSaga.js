import { all, call, put, takeEvery, takeLatest } from 'redux-saga/effects';
import ApiRoutes from '../../api/ApiRoutes';
import http from '../../api/http';
import { withCallbacks } from '../sagaHelpers';
import {
  disconnectYouTube, fetchSettings, fetchYouTube, setKillSwitch, setSettings, setYouTube, testNotification,
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

export default function* settingsSaga() {
  yield all([
    takeLatest(fetchSettings.type, handleFetchSettings),
    takeEvery(setKillSwitch.type, handleKillSwitch),
    takeLatest(fetchYouTube.type, handleFetchYouTube),
    takeEvery(disconnectYouTube.type, handleDisconnectYouTube),
    takeEvery(testNotification.type, handleTestNotification),
  ]);
}
