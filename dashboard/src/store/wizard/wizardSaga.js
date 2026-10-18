import { all, call, put, takeEvery, takeLatest } from 'redux-saga/effects';
import ApiRoutes from '../../api/ApiRoutes';
import http from '../../api/http';
import { withCallbacks } from '../sagaHelpers';
import {
  createInspiration, fetchInspiration, fetchVoices, setInspiration, setVoices,
} from './wizardSlice';

function* handleCreateInspiration({ payload }) {
  yield* withCallbacks(payload, call(function* create() {
    const inspiration = yield call(http.post, ApiRoutes.inspirations(), payload.body);
    yield put(setInspiration(inspiration));
    return inspiration;
  }));
}

function* handleFetchInspiration({ payload }) {
  yield* withCallbacks(payload, call(function* load() {
    const inspiration = yield call(http.get, ApiRoutes.inspiration(payload.id));
    yield put(setInspiration(inspiration));
    return inspiration;
  }));
}

function* handleFetchVoices({ payload }) {
  yield* withCallbacks(payload, call(function* load() {
    const voices = yield call(http.get, ApiRoutes.voices());
    yield put(setVoices(voices));
    return voices;
  }));
}

export default function* wizardSaga() {
  yield all([
    takeEvery(createInspiration.type, handleCreateInspiration),
    takeLatest(fetchInspiration.type, handleFetchInspiration),
    takeLatest(fetchVoices.type, handleFetchVoices),
  ]);
}
