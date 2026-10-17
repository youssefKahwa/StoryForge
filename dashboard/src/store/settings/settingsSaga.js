import { all, call, put, takeEvery, takeLatest } from 'redux-saga/effects';
import ApiRoutes from '../../api/ApiRoutes';
import http from '../../api/http';
import { withCallbacks } from '../sagaHelpers';
import { fetchSettings, setKillSwitch, setSettings } from './settingsSlice';

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

export default function* settingsSaga() {
  yield all([
    takeLatest(fetchSettings.type, handleFetchSettings),
    takeEvery(setKillSwitch.type, handleKillSwitch),
  ]);
}
