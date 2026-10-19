import { all, call, put, takeEvery, takeLatest } from 'redux-saga/effects';
import ApiRoutes from '../../api/ApiRoutes';
import http from '../../api/http';
import { withCallbacks } from '../sagaHelpers';
import {
  addRevision, applyRevisions, deleteRevision, fetchRevisions, fetchTimeline, setRevisions, setTimeline,
} from './revisionsSlice';

function* loadRevisions(runId) {
  const items = yield call(http.get, ApiRoutes.revisions(runId));
  yield put(setRevisions({ runId, items }));
  return items;
}

function* handleFetchTimeline({ payload }) {
  yield* withCallbacks(payload, call(function* load() {
    const timeline = yield call(http.get, ApiRoutes.timeline(payload.runId));
    yield put(setTimeline({ runId: payload.runId, timeline }));
    return timeline;
  }));
}

function* handleFetchRevisions({ payload }) {
  yield* withCallbacks(payload, call(loadRevisions, payload.runId));
}

function* handleAddRevision({ payload }) {
  yield* withCallbacks(payload, call(function* add() {
    const created = yield call(http.post, ApiRoutes.revisions(payload.runId), payload.revision);
    yield call(loadRevisions, payload.runId);
    return created;
  }));
}

function* handleDeleteRevision({ payload }) {
  yield* withCallbacks(payload, call(function* remove() {
    yield call(http.delete, ApiRoutes.revision(payload.runId, payload.id));
    return yield call(loadRevisions, payload.runId);
  }));
}

function* handleApplyRevisions({ payload }) {
  yield* withCallbacks(payload, call(function* apply() {
    const run = yield call(http.post, ApiRoutes.applyRevisions(payload.runId));
    yield call(loadRevisions, payload.runId);
    return run;
  }));
}

export default function* revisionsSaga() {
  yield all([
    takeLatest(fetchTimeline.type, handleFetchTimeline),
    takeLatest(fetchRevisions.type, handleFetchRevisions),
    takeEvery(addRevision.type, handleAddRevision),
    takeEvery(deleteRevision.type, handleDeleteRevision),
    takeEvery(applyRevisions.type, handleApplyRevisions),
  ]);
}
