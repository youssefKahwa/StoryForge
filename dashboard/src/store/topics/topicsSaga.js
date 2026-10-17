import { all, call, put, takeEvery, takeLatest } from 'redux-saga/effects';
import ApiRoutes from '../../api/ApiRoutes';
import http from '../../api/http';
import { withCallbacks } from '../sagaHelpers';
import { archiveTopic, createTopic, fetchTopics, setTopics } from './topicsSlice';

function* loadTopics() {
  const topics = yield call(http.get, ApiRoutes.seedTopics());
  yield put(setTopics(topics));
  return topics;
}

function* handleFetchTopics({ payload }) {
  yield* withCallbacks(payload, call(loadTopics));
}

function* handleCreateTopic({ payload }) {
  yield* withCallbacks(payload, call(function* create() {
    const topic = yield call(http.post, ApiRoutes.seedTopics(), payload.body);
    yield call(loadTopics);
    return topic;
  }));
}

function* handleArchiveTopic({ payload }) {
  yield* withCallbacks(payload, call(function* archive() {
    yield call(http.delete, ApiRoutes.seedTopic(payload.id));
    return yield call(loadTopics);
  }));
}

export default function* topicsSaga() {
  yield all([
    takeLatest(fetchTopics.type, handleFetchTopics),
    takeEvery(createTopic.type, handleCreateTopic),
    takeEvery(archiveTopic.type, handleArchiveTopic),
  ]);
}
