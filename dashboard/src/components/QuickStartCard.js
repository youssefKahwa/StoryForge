import React, { useCallback, useState } from 'react';
import { navigate } from 'gatsby';
import {
  Alert, Button, Card, Col, Input, Progress, Row, Space, Typography, message,
} from 'antd';
import { ThunderboltOutlined } from '@ant-design/icons';
import { FormattedMessage, useIntl } from 'react-intl';
import ApiRoutes from '../api/ApiRoutes';
import http from '../api/http';
import usePolling from '../hooks/usePolling';

const QuickStartCard = () => {
  const intl = useIntl();
  const t = (id, values) => intl.formatMessage({ id }, values);
  const [mainUrl, setMainUrl] = useState('');
  const [urls, setUrls] = useState(['', '', '']);
  const [topic, setTopic] = useState('');
  const [notes, setNotes] = useState('');
  const [busy, setBusy] = useState(false);
  const [waiting, setWaiting] = useState(null);
  const [waitSeconds, setWaitSeconds] = useState(0);

  // Links: the videos are read first; the run starts by itself when the analysis is ready, then we open it.
  const poll = useCallback(async () => {
    if (!waiting) return;
    setWaitSeconds(Math.round((Date.now() - waiting.since) / 1000));
    try {
      const inspiration = await http.get(ApiRoutes.inspiration(waiting.id));
      if (inspiration.status === 'FAILED') {
        message.error(t('quick.readFailed', { error: inspiration.error || '' }), 10);
        setWaiting(null);
        return;
      }
      if (inspiration.status === 'READY') {
        const runs = await http.get(ApiRoutes.runs());
        const run = runs.find((r) => r.inspirationId === waiting.id);
        if (run) navigate(`/run?id=${run.id}`);
      }
    } catch (error) {
      // keep waiting; the API may be busy
    }
  }, [waiting]);
  usePolling(poll, 3000, !!waiting);

  const start = async () => {
    setBusy(true);
    try {
      const result = await http.post(ApiRoutes.quickStart(), {
        urls: urls.filter((u) => u.trim()), topic: topic.trim() || null, notes: notes.trim() || null,
        mainUrl: mainUrl.trim() || null,
      });
      message.success(result.message, 6);
      if (result.runId) {
        navigate(`/run?id=${result.runId}`);
      } else if (result.inspirationId) {
        setWaiting({ id: result.inspirationId, since: Date.now() });
      } else {
        navigate('/');
      }
    } catch (error) {
      message.error(t('error.request', { message: error.message }));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card title={<Space><ThunderboltOutlined /><FormattedMessage id="quick.title" /></Space>}>
      <Space direction="vertical" className="full-width">
        <Typography.Text type="secondary"><FormattedMessage id="quick.help" /></Typography.Text>
        {waiting && (
          <Alert
            type="info"
            showIcon
            message={t('quick.reading')}
            description={(
              <Space direction="vertical" className="full-width">
                <Typography.Text type="secondary">{t('quick.readingHelp', { seconds: waitSeconds })}</Typography.Text>
                <Progress percent={Math.min(95, Math.round(waitSeconds / 3))} status="active" showInfo={false} />
              </Space>
            )}
          />
        )}
        <Input value={mainUrl} onChange={(e) => setMainUrl(e.target.value)} maxLength={300}
          placeholder={t('quick.mainLink')} addonBefore={t('quick.mainLabel')} />
        <Typography.Text type="secondary">{t('quick.mainHelp')}</Typography.Text>
        <Row gutter={8}>
          {urls.map((url, i) => (
            <Col xs={24} md={8} key={`url-${i}`}>
              <Input value={url} placeholder={t('quick.link', { n: i + 1 })}
                onChange={(e) => setUrls(urls.map((u, j) => (j === i ? e.target.value : u)))} />
            </Col>
          ))}
        </Row>
        <Input value={topic} onChange={(e) => setTopic(e.target.value)} maxLength={300} placeholder={t('quick.topic')} />
        <Input.TextArea value={notes} onChange={(e) => setNotes(e.target.value)} rows={2} maxLength={2000}
          placeholder={t('quick.notes')} />
        <Button type="primary" size="large" icon={<ThunderboltOutlined />} loading={busy || !!waiting} onClick={start}>
          <FormattedMessage id="quick.go" />
        </Button>
      </Space>
    </Card>
  );
};

export default QuickStartCard;
