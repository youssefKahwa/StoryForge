import React, { useState } from 'react';
import { navigate } from 'gatsby';
import {
  Button, Card, Col, Input, Row, Space, Typography, message,
} from 'antd';
import { ThunderboltOutlined } from '@ant-design/icons';
import { FormattedMessage, useIntl } from 'react-intl';
import ApiRoutes from '../api/ApiRoutes';
import http from '../api/http';

const QuickStartCard = () => {
  const intl = useIntl();
  const t = (id, values) => intl.formatMessage({ id }, values);
  const [urls, setUrls] = useState(['', '', '']);
  const [topic, setTopic] = useState('');
  const [notes, setNotes] = useState('');
  const [busy, setBusy] = useState(false);

  const start = async () => {
    setBusy(true);
    try {
      const result = await http.post(ApiRoutes.quickStart(), {
        urls: urls.filter((u) => u.trim()), topic: topic.trim() || null, notes: notes.trim() || null,
      });
      message.success(result.message, 6);
      navigate(result.runId ? `/run?id=${result.runId}` : '/');
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
        <Button type="primary" size="large" icon={<ThunderboltOutlined />} loading={busy} onClick={start}>
          <FormattedMessage id="quick.go" />
        </Button>
      </Space>
    </Card>
  );
};

export default QuickStartCard;
