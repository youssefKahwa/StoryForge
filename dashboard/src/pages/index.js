import React, {
  useCallback, useEffect, useMemo, useState,
} from 'react';
import { Link, navigate } from 'gatsby';
import {
  App, Button, Segmented, Space, Table, Typography,
} from 'antd';
import { AppstoreOutlined, BarsOutlined, StopOutlined } from '@ant-design/icons';
import { FormattedMessage, useIntl } from 'react-intl';
import { useDispatch, useSelector } from 'react-redux';
import AppLayout from '../components/AppLayout';
import EmptyState from '../components/EmptyState';
import ProgressRing from '../components/ProgressRing';
import QuickStartCard from '../components/QuickStartCard';
import StatusBadge from '../components/StatusBadge';
import VideoCard from '../components/VideoCard';
import { RunProgressCell, useRunProgress } from '../components/RunProgress';
import usePolling from '../hooks/usePolling';
import useNotify from '../hooks/useNotify';
import { cancelRun, fetchRuns, selectRuns } from '../store/runs/runsSlice';
import { greeting, minutes, relativeTime } from '../utils/time';

const ACTIVE = ['QUEUED', 'RUNNING'];
const ATTENTION = ['BLOCKED', 'FAILED'];
const VIEW_KEY = 'storyforge.view';

const FILTERS = {
  all: () => true,
  making: (run) => ACTIVE.includes(run.status),
  ready: (run) => run.status === 'COMPLETED',
  attention: (run) => ATTENTION.includes(run.status),
};

/** One video being made: big ring, the step in plain words, time left, cancel. */
const LiveCard = ({ run }) => {
  const intl = useIntl();
  const dispatch = useDispatch();
  const { modal } = App.useApp();
  const notify = useNotify();
  const progress = useRunProgress(run.id, true);
  const cancel = (e) => {
    e.preventDefault();
    modal.confirm({
      title: intl.formatMessage({ id: 'run.cancelTitle' }),
      content: intl.formatMessage({ id: 'run.cancelText' }),
      okText: intl.formatMessage({ id: 'run.cancel' }),
      okButtonProps: { danger: true },
      onOk: () => dispatch(cancelRun({
        id: run.id,
        onFail: (error) => notify.error(intl.formatMessage({ id: 'error.request' }, { message: error.message })),
      })),
    });
  };
  return (
    <Link to={`/run?id=${run.id}`} className="sf-live-card">
      <ProgressRing percent={progress ? progress.percent : 0} remainingSeconds={progress ? progress.remainingSeconds : null} size={112} />
      <div className="sf-live-main">
        <StatusBadge status={run.status} inline />
        <div className="sf-live-title" style={{ marginTop: 8 }}>{run.topicTitle}</div>
        <div className="sf-live-stage">
          {run.status === 'QUEUED' || !progress ? <FormattedMessage id="progress.queued" /> : progress.stageLabel}
        </div>
        {progress && progress.stageDetail && <div className="sf-live-detail">{progress.stageDetail}</div>}
      </div>
      <Button icon={<StopOutlined />} onClick={cancel}><FormattedMessage id="run.cancel" /></Button>
    </Link>
  );
};

const readView = () => {
  try {
    return window.localStorage.getItem(VIEW_KEY) || 'grid';
  } catch (e) {
    return 'grid';
  }
};

const StudioPage = ({ location }) => {
  const intl = useIntl();
  const dispatch = useDispatch();
  const runs = useSelector(selectRuns);
  const [filter, setFilter] = useState('all');
  const [view, setView] = useState('grid');
  useEffect(() => setView(readView()), []);
  const refresh = useCallback(() => dispatch(fetchRuns()), [dispatch]);
  usePolling(refresh, 3000, runs.some((run) => ACTIVE.includes(run.status)));

  const live = runs.filter((run) => ACTIVE.includes(run.status));
  const shown = useMemo(() => runs.filter(FILTERS[filter]), [runs, filter]);
  const count = (key) => runs.filter(FILTERS[key]).length;
  const changeView = (value) => {
    setView(value);
    try {
      window.localStorage.setItem(VIEW_KEY, value);
    } catch (e) {
      // remembered for this visit only
    }
  };

  const columns = [
    { title: '#', dataIndex: 'id', width: 64 },
    {
      title: intl.formatMessage({ id: 'runs.col.topic' }),
      dataIndex: 'topicTitle',
      render: (title, run) => (
        <Space direction="vertical" size={0}>
          <Link to={`/run?id=${run.id}`}>{title}</Link>
          {run.statusReason && <Typography.Text type="secondary" style={{ fontSize: 12 }}>{run.statusReason}</Typography.Text>}
        </Space>
      ),
    },
    {
      title: intl.formatMessage({ id: 'runs.col.status' }), dataIndex: 'status', width: 140, render: (s) => <StatusBadge status={s} inline />,
    },
    {
      title: intl.formatMessage({ id: 'runs.col.progress' }), key: 'progress', width: 260, render: (_, run) => <RunProgressCell run={run} />,
    },
    { title: intl.formatMessage({ id: 'runs.col.length' }), dataIndex: 'targetSeconds', width: 90, render: minutes },
    {
      title: intl.formatMessage({ id: 'runs.col.created' }), dataIndex: 'createdAt', width: 150, render: relativeTime,
    },
  ];

  return (
    <AppLayout location={location} title={intl.formatMessage({ id: 'nav.studio' })}>
      <QuickStartCard variant="hero" title={`${greeting()} 👋`} />

      {live.length > 0 && (
        <>
          <div className="sf-section-title"><h2><FormattedMessage id="studio.making" /></h2></div>
          {live.map((run) => <LiveCard key={run.id} run={run} />)}
        </>
      )}

      <div className="sf-section-title">
        <h2><FormattedMessage id="studio.videos" /></h2>
        <Space wrap className="sf-scroll-x">
          <Segmented
            value={filter}
            onChange={setFilter}
            options={Object.keys(FILTERS).map((key) => ({
              value: key, label: `${intl.formatMessage({ id: `studio.filter.${key}` })} · ${count(key)}`,
            }))}
          />
          <Segmented
            value={view}
            onChange={changeView}
            options={[{ value: 'grid', icon: <AppstoreOutlined /> }, { value: 'table', icon: <BarsOutlined /> }]}
          />
        </Space>
      </div>

      {runs.length === 0 ? (
        <EmptyState
          title={intl.formatMessage({ id: 'studio.empty.title' })}
          text={intl.formatMessage({ id: 'studio.empty.text' })}
          action={<Button type="primary" onClick={() => navigate('/new')}><FormattedMessage id="nav.new" /></Button>}
        />
      ) : view === 'grid' ? (
        <div className="sf-grid">
          {shown.map((run) => <VideoCard key={run.id} run={run} />)}
        </div>
      ) : (
        <Table
          rowKey="id"
          columns={columns}
          dataSource={shown}
          pagination={{ pageSize: 20, hideOnSinglePage: true }}
          onRow={(run) => ({ onDoubleClick: () => navigate(`/run?id=${run.id}`) })}
        />
      )}
    </AppLayout>
  );
};

export const Head = () => <title>Studio · StoryForge</title>;

export default StudioPage;
