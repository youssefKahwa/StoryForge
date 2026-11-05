import React from 'react';
import { useIntl } from 'react-intl';

const KIND = {
  QUEUED: 'queued',
  RUNNING: 'running',
  COMPLETED: 'done',
  BLOCKED: 'attention',
  FAILED: 'failed',
  CANCELLED: 'stopped',
};

/** A run's status in plain words with a coloured dot: "Making it", "Ready", "Needs you"... */
const StatusBadge = ({ status, inline = false }) => {
  const intl = useIntl();
  const kind = KIND[status] || 'queued';
  return (
    <span className={`sf-badge ${kind}${inline ? ' sf-badge-inline' : ''}`}>
      <i />
      {intl.formatMessage({ id: `status.${kind}` })}
    </span>
  );
};

export const statusKind = (status) => KIND[status] || 'queued';

export default StatusBadge;
