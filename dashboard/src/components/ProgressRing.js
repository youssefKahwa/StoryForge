import React from 'react';
import { Progress } from 'antd';
import { duration } from '../utils/time';

/** Big circular progress with the percent inside and the time left under it. */
const ProgressRing = ({ percent, remainingSeconds, size = 132 }) => (
  <Progress
    type="dashboard"
    percent={percent || 0}
    size={size}
    strokeWidth={9}
    strokeColor={{ '0%': '#4cc2ff', '100%': '#8c5cff' }}
    format={(p) => (
      <div>
        <div style={{ fontSize: size / 5, fontWeight: 800 }}>{`${p}%`}</div>
        {remainingSeconds != null && (
          <div style={{ fontSize: 12, opacity: 0.7, marginTop: 2 }}>{`~${duration(remainingSeconds)} left`}</div>
        )}
      </div>
    )}
  />
);

export default ProgressRing;
