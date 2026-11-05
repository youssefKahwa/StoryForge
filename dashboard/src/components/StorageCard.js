import React, { useEffect, useState } from 'react';
import {
  Button, Card, Descriptions, InputNumber, Popconfirm, Space, Spin, Typography,
} from 'antd';
import useNotify from '../hooks/useNotify';
import { FormattedMessage, useIntl } from 'react-intl';
import { useDispatch, useSelector } from 'react-redux';
import { cleanupStorage, fetchStorage, selectStorage } from '../store/settings/settingsSlice';

const StorageCard = () => {
  const intl = useIntl();
  const message = useNotify();
  const dispatch = useDispatch();
  const storage = useSelector(selectStorage);
  const [days, setDays] = useState(14);
  const [busy, setBusy] = useState(false);
  const onFail = (error) => {
    setBusy(false);
    message.error(intl.formatMessage({ id: 'error.request' }, { message: error.message }));
  };

  useEffect(() => {
    dispatch(fetchStorage({ days, onFail }));
  }, [dispatch, days]);

  const clean = () => {
    setBusy(true);
    dispatch(cleanupStorage({
      days,
      onSuccess: (result) => {
        setBusy(false);
        message.success(intl.formatMessage({ id: 'storage.done' }, { runs: result.runsCleaned, freed: result.freed }));
      },
      onFail,
    }));
  };

  return (
    <Card size="small" title={<FormattedMessage id="storage.title" />}>
      {!storage ? <Spin /> : (
        <Space direction="vertical" className="full-width">
          <Descriptions column={{ xs: 1, md: 3 }} size="small" bordered>
            <Descriptions.Item label={intl.formatMessage({ id: 'storage.free' })}>{storage.free}</Descriptions.Item>
            <Descriptions.Item label={intl.formatMessage({ id: 'storage.runs' })}>{storage.runs}</Descriptions.Item>
            <Descriptions.Item label={intl.formatMessage({ id: 'storage.exports' })}>{storage.exports}</Descriptions.Item>
          </Descriptions>
          <Space wrap>
            <Typography.Text><FormattedMessage id="storage.olderThan" /></Typography.Text>
            <InputNumber min={0} max={365} value={days} onChange={(v) => setDays(v ?? 14)} />
            <Typography.Text><FormattedMessage id="storage.days" /></Typography.Text>
            <Popconfirm title={intl.formatMessage({ id: 'storage.confirm' })} onConfirm={clean}
              disabled={!storage.cleanableRuns}>
              <Button danger loading={busy} disabled={!storage.cleanableRuns}>
                {intl.formatMessage({ id: 'storage.clean' }, { size: storage.cleanable, runs: storage.cleanableRuns })}
              </Button>
            </Popconfirm>
          </Space>
          <Typography.Text type="secondary"><FormattedMessage id="storage.help" /></Typography.Text>
        </Space>
      )}
    </Card>
  );
};

export default StorageCard;
