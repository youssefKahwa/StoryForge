import React from 'react';
import { VideoCameraAddOutlined } from '@ant-design/icons';

const EmptyState = ({
  icon = <VideoCameraAddOutlined />, title, text, action,
}) => (
  <div className="sf-empty">
    <div className="sf-empty-icon">{icon}</div>
    <h3>{title}</h3>
    {text && <p>{text}</p>}
    {action}
  </div>
);

export default EmptyState;
