import React, { useCallback, useState } from 'react';
import { Link, navigate } from 'gatsby';
import { Button, Empty, Space, Table, Typography } from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { FormattedMessage, useIntl } from 'react-intl';
import { useDispatch, useSelector } from 'react-redux';
import AppLayout from '../components/AppLayout';
import NewRunModal from '../components/NewRunModal';
import RunStatusTag from '../components/RunStatusTag';
import StageChips from '../components/StageChips';
import usePolling from '../hooks/usePolling';
import { fetchRuns, selectRuns, selectRunsLoading } from '../store/runs/runsSlice';

const ACTIVE = ['QUEUED', 'RUNNING'];

const RunsPage = ({ location }) => {
  const intl = useIntl();
  const dispatch = useDispatch();
  const runs = useSelector(selectRuns);
  const loading = useSelector(selectRunsLoading);
  const [modalOpen, setModalOpen] = useState(false);
  const refresh = useCallback(() => dispatch(fetchRuns()), [dispatch]);
  usePolling(refresh, 3000, runs.some((run) => ACTIVE.includes(run.status)));

  const columns = [
    { title: intl.formatMessage({ id: 'runs.col.id' }), dataIndex: 'id', width: 60 },
    {
      title: intl.formatMessage({ id: 'runs.col.topic' }),
      dataIndex: 'topicTitle',
      render: (title, run) => (
        <Space direction="vertical" size={0}>
          <Link to={`/run?id=${run.id}`}>{title}</Link>
          {run.statusReason && <Typography.Text type="secondary">{run.statusReason}</Typography.Text>}
        </Space>
      ),
    },
    {
      title: intl.formatMessage({ id: 'runs.col.status' }),
      dataIndex: 'status',
      width: 120,
      render: (status) => <RunStatusTag status={status} />,
    },
    { title: intl.formatMessage({ id: 'runs.col.state' }), dataIndex: 'state', width: 140 },
    {
      title: intl.formatMessage({ id: 'runs.col.stages' }),
      dataIndex: 'stages',
      width: 300,
      render: (stages) => <StageChips stages={stages} />,
    },
    {
      title: intl.formatMessage({ id: 'runs.col.created' }),
      dataIndex: 'createdAt',
      width: 190,
      render: (createdAt) => new Date(createdAt).toLocaleString(),
    },
  ];

  return (
    <AppLayout location={location}>
      <div className="page-header">
        <Typography.Title level={3}><FormattedMessage id="runs.title" /></Typography.Title>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={refresh} loading={loading}>
            <FormattedMessage id="common.refresh" />
          </Button>
          <Button onClick={() => setModalOpen(true)}>
            <FormattedMessage id="runs.quick" />
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/new')}>
            <FormattedMessage id="nav.new" />
          </Button>
        </Space>
      </div>
      <Table
        rowKey="id"
        columns={columns}
        dataSource={runs}
        locale={{ emptyText: <Empty description={<FormattedMessage id="runs.empty" />} /> }}
        onRow={(run) => ({ onDoubleClick: () => navigate(`/run?id=${run.id}`) })}
      />
      <NewRunModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        onCreated={() => setModalOpen(false)}
      />
    </AppLayout>
  );
};

export const Head = () => <title>Runs · StoryForge</title>;

export default RunsPage;
