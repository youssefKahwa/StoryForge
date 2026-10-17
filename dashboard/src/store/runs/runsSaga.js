import { all, call, put, takeEvery, takeLatest } from 'redux-saga/effects';
import ApiRoutes from '../../api/ApiRoutes';
import http from '../../api/http';
import { withCallbacks } from '../sagaHelpers';
import {
  cancelRun,
  createRun,
  fetchRun,
  fetchRuns,
  rerunStage,
  resumeRun,
  setRunDetail,
  setRuns,
  setRunsLoading,
} from './runsSlice';

function* loadRuns() {
  const runs = yield call(http.get, ApiRoutes.runs());
  yield put(setRuns(runs));
  return runs;
}

function* loadRun(id) {
  const detail = yield call(http.get, ApiRoutes.run(id));
  yield put(setRunDetail(detail));
  return detail;
}

function* handleFetchRuns({ payload }) {
  yield put(setRunsLoading(true));
  yield* withCallbacks(payload, call(loadRuns));
  yield put(setRunsLoading(false));
}

function* handleFetchRun({ payload }) {
  yield* withCallbacks(payload, call(loadRun, payload.id));
}

function* handleCreateRun({ payload }) {
  yield* withCallbacks(payload, call(function* create() {
    const run = yield call(http.post, ApiRoutes.runs(), payload.body);
    yield call(loadRuns);
    return run;
  }));
}

function* runCommand(payload, url) {
  yield* withCallbacks(payload, call(function* command() {
    const run = yield call(http.post, url);
    yield call(loadRun, payload.id);
    return run;
  }));
}

function* handleCancelRun({ payload }) {
  yield* runCommand(payload, ApiRoutes.cancelRun(payload.id));
}

function* handleResumeRun({ payload }) {
  yield* runCommand(payload, ApiRoutes.resumeRun(payload.id));
}

function* handleRerunStage({ payload }) {
  yield* runCommand(payload, ApiRoutes.rerunStage(payload.id, payload.stage, Boolean(payload.fresh)));
}

export default function* runsSaga() {
  yield all([
    takeLatest(fetchRuns.type, handleFetchRuns),
    takeLatest(fetchRun.type, handleFetchRun),
    takeEvery(createRun.type, handleCreateRun),
    takeEvery(cancelRun.type, handleCancelRun),
    takeEvery(resumeRun.type, handleResumeRun),
    takeEvery(rerunStage.type, handleRerunStage),
  ]);
}
