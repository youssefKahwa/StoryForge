import React from 'react';
import { Typography } from 'antd';

const PageHeader = ({ title, subtitle, actions }) => (
  <div className="sf-page-header">
    <div>
      <Typography.Title level={2} className="sf-page-title">{title}</Typography.Title>
      {subtitle && <div className="sf-page-sub">{subtitle}</div>}
    </div>
    {actions}
  </div>
);

export default PageHeader;
