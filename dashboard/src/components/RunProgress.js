import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Progress, Steps, Typography } from 'antd';
import { FormattedMessage, useIntl } from 'react-intl';
import ApiRoutes from '../api/ApiRoutes';
import http from '../api/http';
import usePolling from '../hooks/usePolling';
import ProgressRing from './ProgressRing';
import { duration } from '../utils/time';

export { duration };

const ACTIVE = ['QUEUED', 'RUNNING'];

const STEP_STATUS = {
  done: 'finish', running: 'process', failed: 'error', pending: 'wait',
};

export const useRunProgress = (runId, active) => {
  const [progress, setProgress] = useState(null);
  const load = useCallback(() => {
    if (!runId) return;
    http.get(ApiRoutes.progress(runId)).then(setProgress).catch(() => {});
  }, [runId]);
  usePolling(load, 3000, active);
  return progress;
};

/** Small bar for the table view of the runs list. */
export const RunProgressCell = ({ run }) => {
  const progress = useRunProgress(run.id, ACTIVE.includes(run.status));
  if (!progress || !ACTIVE.includes(run.status)) return null;
  return (
    <div style={{ minWidth: 220 }}>
      <Progress percent={progress.percent} size="small" status="active" />
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        {progress.stageDetail || progress.stageLabel}
        {progress.remainingSeconds != null ? ` · ~${duration(progress.remainingSeconds)} left` : ''}
      </Typography.Text>
    </div>
  );
};

/** Full progress view on the run page while the video is being made. */
const RunProgress = ({ run }) => {
  const intl = useIntl();
  const active = ACTIVE.includes(run.status);
  const progress = useRunProgress(run.id, active);

  useEffect(() => {
    if (progress && active) {
      document.title = `${progress.percent}% · ${progress.stageLabel} — StoryForge`;
    }
    return () => { document.title = 'Video · StoryForge'; };
  }, [progress, active]);

  if (!progress) return null;
  if (!active) {
    return run.status === 'COMPLETED' ? (
      <Typography.Text type="secondary">
        {intl.formatMessage({ id: 'progress.doneIn' }, { time: duration(progress.elapsedSeconds) })}
      </Typography.Text>
    ) : null;
  }
  const expected = progress.expectedAt
    ? new Date(progress.expectedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : null;
  const current = progress.steps.findIndex((s) => s.status === 'running');
  return (
    <div className="sf-panel">
      <div className="sf-progress-hero">
        <ProgressRing percent={progress.percent} remainingSeconds={progress.remainingSeconds} size={150} />
        <div className="sf-live-main">
          <div className="sf-panel-title"><FormattedMessage id="progress.now" /></div>
          <div className="sf-live-title">
            {run.status === 'QUEUED' ? <FormattedMessage id="progress.queued" /> : progress.stageLabel}
          </div>
          {progress.stageDetail && <div className="sf-live-detail">{progress.stageDetail}</div>}
          {progress.stagePercent != null && (
            <Progress percent={progress.stagePercent} size="small" showInfo={false} style={{ maxWidth: 420, marginTop: 8 }} />
          )}
          <div className="sf-stats">
            <div>
              <div className="sf-stat-label"><FormattedMessage id="progress.elapsed" /></div>
              <div className="sf-stat-value">{duration(progress.elapsedSeconds)}</div>
            </div>
            <div>
              <div className="sf-stat-label"><FormattedMessage id="progress.left" /></div>
              <div className="sf-stat-value">{`~${duration(progress.remainingSeconds)}`}</div>
            </div>
            {expected && (
              <div>
                <div className="sf-stat-label"><FormattedMessage id="progress.readyAt" /></div>
                <div className="sf-stat-value">{expected}</div>
              </div>
            )}
          </div>
        </div>
      </div>
      {progress.onBattery && (
        <Alert type="warning" showIcon style={{ marginTop: 16 }} message={<FormattedMessage id="progress.battery" />} />
      )}
      <Steps
        className="sf-timeline"
        direction="vertical"
        size="small"
        current={current < 0 ? undefined : current}
        items={progress.steps.map((s) => ({
          title: s.label,
          status: STEP_STATUS[s.status] || 'wait',
          description: s.status === 'done' ? duration(s.seconds)
            : s.status === 'running' ? `${duration(s.seconds)} of ~${duration(s.estimateSeconds)}`
              : `~${duration(s.estimateSeconds)}`,
        }))}
      />
    </div>
  );
};

export default RunProgress;
