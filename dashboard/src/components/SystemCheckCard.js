import React, { useEffect, useState } from 'react';
import {
  Alert, Button, Card, List, Space, Spin, Tag, Typography, message,
} from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { FormattedMessage, useIntl } from 'react-intl';
import { useDispatch, useSelector } from 'react-redux';
import { fetchSystemCheck, selectSystemCheck } from '../store/settings/settingsSlice';

const COLORS = {
  OK: 'green', WARN: 'orange', FAIL: 'red', OFF: 'default',
};

const SystemCheckCard = () => {
  const intl = useIntl();
  const dispatch = useDispatch();
  const report = useSelector(selectSystemCheck);
  const [loading, setLoading] = useState(false);

  const load = () => {
    setLoading(true);
    dispatch(fetchSystemCheck({
      onSuccess: () => setLoading(false),
      onFail: (error) => {
        setLoading(false);
        message.error(intl.formatMessage({ id: 'error.request' }, { message: error.message }));
      },
    }));
  };

  useEffect(load, [dispatch]);

  return (
    <Card size="small" title={<FormattedMessage id="system.title" />}
      extra={<Button size="small" icon={<ReloadOutlined />} loading={loading} onClick={load}><FormattedMessage id="system.recheck" /></Button>}>
      {!report ? <Spin /> : (
        <Space direction="vertical" className="full-width">
          <Alert showIcon type={report.ready ? (report.warnings ? 'warning' : 'success') : 'error'}
            message={intl.formatMessage({ id: report.ready ? 'system.ready' : 'system.notReady' },
              { failures: report.failures, warnings: report.warnings })} />
          <List size="small" dataSource={report.checks} renderItem={(check) => (
            <List.Item>
              <Space direction="vertical" size={0}>
                <Space>
                  <Tag color={COLORS[check.level]}>{check.level}</Tag>
                  <Typography.Text strong>{check.label}</Typography.Text>
                  <Typography.Text type="secondary">{check.detail}</Typography.Text>
                </Space>
                {check.fix && <Typography.Text type="warning">{`→ ${check.fix}`}</Typography.Text>}
              </Space>
            </List.Item>
          )} />
        </Space>
      )}
    </Card>
  );
};

export default SystemCheckCard;
