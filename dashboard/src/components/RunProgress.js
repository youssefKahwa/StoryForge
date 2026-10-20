import React, { useCallback, useEffect, useState } from 'react';
import {
  Alert, Card, Col, Progress, Row, Space, Statistic, Steps, Tag, Typography,
} from 'antd';
import { ClockCircleOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { FormattedMessage, useIntl } from 'react-intl';
import ApiRoutes from '../api/ApiRoutes';
import http from '../api/http';
import usePolling from '../hooks/usePolling';

const ACTIVE = ['QUEUED', 'RUNNING'];

/** "1 h 05 min", "12 min", "40 s". */
export const duration = (seconds) => {
  if (seconds == null) return '–';
  const s = Math.max(0, Math.round(seconds));
  if (s < 60) return `${s} s`;
  const m = Math.round(s / 60);
  if (m < 60) return `${m} min`;
  return `${Math.floor(m / 60)} h ${String(m % 60).padStart(2, '0')} min`;
};

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

/** Small bar for the runs list. */
export const RunProgressCell = ({ run }) => {
  const progress = useRunProgress(run.id, ACTIVE.includes(run.status));
  if (!progress || !ACTIVE.includes(run.status)) return null;
  return (
    <div className="progress-cell">
      <Progress percent={progress.percent} size="small" status="active" />
      <Typography.Text type="secondary" className="progress-cell-text">
        {progress.stageDetail || progress.stageLabel}
        {progress.remainingSeconds != null ? ` · ~${duration(progress.remainingSeconds)} left` : ''}
      </Typography.Text>
    </div>
  );
};

/** Full progress view on the run page. */
const RunProgress = ({ run }) => {
  const intl = useIntl();
  const active = ACTIVE.includes(run.status);
  const progress = useRunProgress(run.id, active);

  useEffect(() => {
    if (progress && active) {
      document.title = `${progress.percent}% · ${progress.stageLabel} — StoryForge`;
    }
    return () => { document.title = 'Run · StoryForge'; };
  }, [progress, active]);

  if (!progress) return null;
  if (!active) {
    return run.status === 'COMPLETED' ? (
      <Alert type="success" showIcon className="progress-done"
        message={intl.formatMessage({ id: 'progress.doneIn' }, { time: duration(progress.elapsedSeconds) })} />
    ) : null;
  }
  const expected = progress.expectedAt ? new Date(progress.expectedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : null;
  const current = progress.steps.findIndex((s) => s.status === 'running');
  return (
    <Card className="progress-card">
      <Row gutter={24} align="middle">
        <Col xs={24} md={14}>
          <Typography.Title level={4} className="progress-title">
            {run.status === 'QUEUED' ? <FormattedMessage id="progress.queued" /> : progress.stageLabel}
          </Typography.Title>
          {progress.stageDetail && <Typography.Text type="secondary">{progress.stageDetail}</Typography.Text>}
          <Progress percent={progress.percent} status="active" strokeColor={{ from: '#304860', to: '#70c0e0' }} strokeWidth={14} />
          {progress.stagePercent != null && (
            <Progress percent={progress.stagePercent} size="small" showInfo={false} strokeColor="#70c0e0" />
          )}
        </Col>
        <Col xs={24} md={10}>
          <Space size="large" wrap>
            <Statistic title={intl.formatMessage({ id: 'progress.elapsed' })} value={duration(progress.elapsedSeconds)} prefix={<ClockCircleOutlined />} />
            <Statistic title={intl.formatMessage({ id: 'progress.left' })} value={`~${duration(progress.remainingSeconds)}`} prefix={<ThunderboltOutlined />} />
            {expected && <Statistic title={intl.formatMessage({ id: 'progress.readyAt' })} value={expected} />}
          </Space>
        </Col>
      </Row>
      {progress.onBattery && (
        <Alert type="warning" showIcon className="progress-battery" message={<FormattedMessage id="progress.battery" />} />
      )}
      <Steps
        className="progress-steps"
        direction="vertical"
        size="small"
        current={current < 0 ? undefined : current}
        items={progress.steps.map((s) => ({
          title: s.label,
          status: STEP_STATUS[s.status] || 'wait',
          description: s.status === 'done' ? duration(s.seconds)
            : s.status === 'running' ? (
              <Tag color="blue">{`${duration(s.seconds)} / ~${duration(s.estimateSeconds)}`}</Tag>
            ) : `~${duration(s.estimateSeconds)}`,
        }))}
      />
    </Card>
  );
};

export default RunProgress;
