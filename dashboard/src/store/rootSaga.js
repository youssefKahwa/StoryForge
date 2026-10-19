import { all } from 'redux-saga/effects';
import runsSaga from './runs/runsSaga';
import topicsSaga from './topics/topicsSaga';
import settingsSaga from './settings/settingsSaga';
import wizardSaga from './wizard/wizardSaga';
import revisionsSaga from './revisions/revisionsSaga';

export default function* rootSaga() {
  yield all([runsSaga(), topicsSaga(), settingsSaga(), wizardSaga(), revisionsSaga()]);
}
