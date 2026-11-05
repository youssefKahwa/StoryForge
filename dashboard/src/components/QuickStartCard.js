import React, { useCallback, useState } from 'react';
import { navigate } from 'gatsby';
import {
  Alert, Button, Col, Collapse, Input, Progress, Row, Space, Typography,
} from 'antd';
import { LinkOutlined, StarOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { FormattedMessage, useIntl } from 'react-intl';
import ApiRoutes from '../api/ApiRoutes';
import http from '../api/http';
import usePolling from '../hooks/usePolling';
import useNotify from '../hooks/useNotify';

const LINK = /https?:\/\/\S+/gi;

/** "links and/or a topic" typed in one box: the links, and what is left as the topic. */
export const splitSmartInput = (text) => {
  const urls = (text.match(LINK) || []).map((u) => u.replace(/[),.;]+$/, ''));
  const topic = text.replace(LINK, ' ').replace(/\s+/g, ' ').trim();
  return { urls, topic };
};

/**
 * Start a video in one step. variant "hero": one big box (links or a topic) with more options folded away, for the
 * studio home. variant "full": every field visible, for the New video page. focus: "links" or "topic".
 */
const QuickStartCard = ({
  variant = 'full', title, subtitle, focus,
}) => {
  const intl = useIntl();
  const notify = useNotify();
  const t = (id, values) => intl.formatMessage({ id }, values);
  const [smart, setSmart] = useState('');
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
        notify.error(t('quick.readFailed', { error: inspiration.error || '' }), 10);
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

  const request = () => {
    if (variant === 'hero') {
      const parsed = splitSmartInput(smart);
      return {
        urls: parsed.urls, topic: parsed.topic || null, notes: notes.trim() || null, mainUrl: mainUrl.trim() || null,
      };
    }
    return {
      urls: urls.filter((u) => u.trim()), topic: topic.trim() || null, notes: notes.trim() || null,
      mainUrl: mainUrl.trim() || null,
    };
  };
  const body = request();
  const empty = !body.urls.length && !body.topic && !body.mainUrl;

  const start = async () => {
    setBusy(true);
    try {
      const result = await http.post(ApiRoutes.quickStart(), body);
      notify.success(result.message, 6);
      if (result.runId) {
        navigate(`/run?id=${result.runId}`);
      } else if (result.inspirationId) {
        setWaiting({ id: result.inspirationId, since: Date.now() });
      } else {
        navigate('/');
      }
    } catch (error) {
      notify.error(t('error.request', { message: error.message }));
    } finally {
      setBusy(false);
    }
  };

  const reading = waiting && (
    <Alert
      type="info"
      showIcon
      style={{ marginBottom: 14 }}
      message={t('quick.reading')}
      description={(
        <Space direction="vertical" className="full-width">
          <Typography.Text type="secondary">{t('quick.readingHelp', { seconds: waitSeconds })}</Typography.Text>
          <Progress percent={Math.min(95, Math.round(waitSeconds / 3))} status="active" showInfo={false} />
        </Space>
      )}
    />
  );

  const mainField = (
    <div>
      <Input
        value={mainUrl}
        onChange={(e) => setMainUrl(e.target.value)}
        maxLength={300}
        prefix={<StarOutlined />}
        placeholder={t('quick.mainLink')}
      />
      <Typography.Text type="secondary" style={{ fontSize: 12.5 }}>{t('quick.mainHelp')}</Typography.Text>
    </div>
  );
  const notesField = (
    <Input.TextArea value={notes} onChange={(e) => setNotes(e.target.value)} autoSize={{ minRows: 2, maxRows: 5 }}
      maxLength={2000} placeholder={t('quick.notes')} />
  );
  const go = (
    <Button type="primary" size="large" icon={<ThunderboltOutlined />} loading={busy || !!waiting}
      disabled={empty} onClick={start}>
      <FormattedMessage id="quick.go" />
    </Button>
  );

  if (variant === 'hero') {
    return (
      <div className="sf-hero">
        <h1>{title}</h1>
        <p>{subtitle || t('hero.help')}</p>
        {reading}
        <div className="sf-hero-row">
          <Input
            size="large"
            allowClear
            prefix={<LinkOutlined />}
            value={smart}
            maxLength={2000}
            onChange={(e) => setSmart(e.target.value)}
            onPressEnter={() => !empty && start()}
            placeholder={t('hero.placeholder')}
          />
          {go}
        </div>
        <Collapse
          ghost
          className="sf-more"
          items={[{
            key: 'more',
            label: t('hero.more'),
            children: <Space direction="vertical" className="full-width">{mainField}{notesField}</Space>,
          }]}
        />
      </div>
    );
  }

  return (
    <div className="sf-panel">
      {reading}
      <Space direction="vertical" size="middle" className="full-width">
        {focus !== 'topic' && (
          <>
            {mainField}
            <Row gutter={[8, 8]}>
              {urls.map((url, i) => (
                <Col xs={24} md={8} key={`url-${i}`}>
                  <Input value={url} prefix={<LinkOutlined />} placeholder={t('quick.link', { n: i + 1 })}
                    onChange={(e) => setUrls(urls.map((u, j) => (j === i ? e.target.value : u)))} />
                </Col>
              ))}
            </Row>
          </>
        )}
        <Input size={focus === 'topic' ? 'large' : 'middle'} value={topic} onChange={(e) => setTopic(e.target.value)}
          maxLength={300} placeholder={t(focus === 'topic' ? 'quick.topicOnly' : 'quick.topic')}
          autoFocus={focus === 'topic'} />
        {notesField}
        {go}
      </Space>
    </div>
  );
};

export default QuickStartCard;
