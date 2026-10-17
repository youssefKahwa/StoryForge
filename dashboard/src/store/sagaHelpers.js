import { call } from 'redux-saga/effects';

export function* withCallbacks(payload, effect) {
  const { onSuccess, onFail } = payload || {};
  try {
    const result = yield effect;
    if (onSuccess) {
      yield call(onSuccess, result);
    }
    return result;
  } catch (error) {
    if (onFail) {
      yield call(onFail, error);
    }
    return undefined;
  }
}
