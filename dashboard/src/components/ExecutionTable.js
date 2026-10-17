import React from 'react';
import { Table, Tag, Typography } from 'antd';
import { useIntl } from 'react-intl';

const STATUS_COLORS = {
  RUNNING: 'processing',
  SUCCEEDED: 'success',
  CACHED: 'cyan',
  FAILED: 'error',
  BLOCKED: 'warning',
};

const ExecutionTable = ({ executions }) => {
  const intl = useIntl();
  const columns = [
    {
      title: intl.formatMessage({ id: 'exec.stage' }),
      dataIndex: 'stage',
      width: 130,
      render: (stage) => intl.formatMessage({ id: `stage.${stage}` }),
    },
    {
      title: intl.formatMessage({ id: 'exec.status' }),
      dataIndex: 'status',
      width: 120,
      render: (status) => <Tag color={STATUS_COLORS[status]}>{status}</Tag>,
    },
    { title: intl.formatMessage({ id: 'exec.provider' }), dataIndex: 'provider', width: 200 },
    {
      title: intl.formatMessage({ id: 'exec.duration' }),
      dataIndex: 'durationMs',
      width: 100,
      render: (ms) => (ms == null ? '…' : `${(ms / 1000).toFixed(1)} s`),
    },
    {
      title: intl.formatMessage({ id: 'exec.tokens' }),
      width: 120,
      render: (_, row) => (row.inputTokens || row.outputTokens ? `${row.inputTokens} / ${row.outputTokens}` : '–'),
    },
    {
      title: intl.formatMessage({ id: 'exec.error' }),
      dataIndex: 'error',
      render: (error) => error && <Typography.Text type="danger">{error}</Typography.Text>,
    },
  ];
  return <Table rowKey="id" size="small" pagination={false} columns={columns} dataSource={executions} />;
};

export default ExecutionTable;
