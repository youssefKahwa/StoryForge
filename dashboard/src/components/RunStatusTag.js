import React from 'react';
import { Tag } from 'antd';

const COLORS = {
  QUEUED: 'default',
  RUNNING: 'processing',
  COMPLETED: 'success',
  FAILED: 'error',
  BLOCKED: 'warning',
  CANCELLED: 'default',
};

const RunStatusTag = ({ status }) => <Tag color={COLORS[status] || 'default'}>{status}</Tag>;

export default RunStatusTag;
