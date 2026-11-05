import React, { useCallback, useState } from 'react';
import { navigate } from 'gatsby';
import {
  Button, Form, Input, Popconfirm, Select, Space, Typography,
} from 'antd';
import { BulbOutlined, PlusOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { FormattedMessage, useIntl } from 'react-intl';
import { useDispatch, useSelector } from 'react-redux';
import AppLayout from '../components/AppLayout';
import EmptyState from '../components/EmptyState';
import PageHeader from '../components/PageHeader';
import usePolling from '../hooks/usePolling';
import useNotify from '../hooks/useNotify';
import { createRun } from '../store/runs/runsSlice';
import {
  archiveTopic, createTopic, fetchTopics, selectTopics, suggestTopics,
} from '../store/topics/topicsSlice';

const FORMATS = ['GREAT_STORIES', 'FILM_ANALYSIS'];

const TopicsPage = ({ location }) => {
  const intl = useIntl();
  const dispatch = useDispatch();
  const [form] = Form.useForm();
  const message = useNotify();
  const topics = useSelector(selectTopics);
  const refresh = useCallback(() => dispatch(fetchTopics()), [dispatch]);
  usePolling(refresh, 30000);

  const [suggesting, setSuggesting] = useState(false);
  const suggest = () => {
    setSuggesting(true);
    dispatch(suggestTopics({
      count: 5,
      onSuccess: (created) => {
        setSuggesting(false);
        message.success(intl.formatMessage({ id: 'topics.suggested' }, { count: created.length }));
      },
      onFail: (error) => {
        setSuggesting(false);
        onFail(error);
      },
    }));
  };

  const onFail = (error) => message.error(intl.formatMessage({ id: 'error.request' }, { message: error.message }));

  const add = (values) => dispatch(createTopic({
    body: values,
    onSuccess: () => form.resetFields(),
    onFail,
  }));

  const start = (topic) => dispatch(createRun({
    body: { seedTopicId: topic.id },
    onSuccess: (run) => navigate(`/run?id=${run.id}`),
    onFail,
  }));

  const live = topics.filter((topic) => topic.status === 'NEW');
  const used = topics.filter((topic) => topic.status !== 'NEW');

  const card = (topic) => (
    <div className="sf-panel" key={topic.id} style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
      <div style={{ fontWeight: 700, fontSize: 16, lineHeight: 1.35 }}>{topic.title}</div>
      {topic.notes && <Typography.Text type="secondary" ellipsis={{ rows: 2 }}>{topic.notes}</Typography.Text>}
      <div className="sf-chips" style={{ marginTop: 'auto' }}>
        <span className="sf-chip">{intl.formatMessage({ id: `format.${topic.format}` })}</span>
        {topic.status !== 'NEW' && <span className="sf-chip">{topic.status.toLowerCase()}</span>}
      </div>
      <Space>
        <Button type="primary" icon={<ThunderboltOutlined />} disabled={topic.status !== 'NEW'} onClick={() => start(topic)}>
          <FormattedMessage id="topics.start" />
        </Button>
        <Popconfirm title={intl.formatMessage({ id: 'topics.archiveConfirm' })} onConfirm={() => dispatch(archiveTopic({ id: topic.id, onFail }))}>
          <Button type="text"><FormattedMessage id="topics.archive" /></Button>
        </Popconfirm>
      </Space>
    </div>
  );

  return (
    <AppLayout location={location} title={intl.formatMessage({ id: 'nav.topics' })}>
      <PageHeader
        title={intl.formatMessage({ id: 'topics.title' })}
        subtitle={intl.formatMessage({ id: 'topics.subtitle' })}
        actions={(
          <Button icon={<BulbOutlined />} loading={suggesting} onClick={suggest}><FormattedMessage id="topics.suggest" /></Button>
        )}
      />
      <div className="sf-panel" style={{ marginBottom: 24 }}>
        <Form form={form} layout="inline" onFinish={add} initialValues={{ format: 'GREAT_STORIES' }} style={{ rowGap: 10 }}>
          <Form.Item name="title" rules={[{ required: true, max: 300 }]} style={{ flex: '2 1 280px' }}>
            <Input placeholder={intl.formatMessage({ id: 'topics.titleField' })} />
          </Form.Item>
          <Form.Item name="notes" style={{ flex: '2 1 220px' }}>
            <Input placeholder={intl.formatMessage({ id: 'topics.notes' })} />
          </Form.Item>
          <Form.Item name="format">
            <Select options={FORMATS.map((format) => ({ value: format, label: intl.formatMessage({ id: `format.${format}` }) }))} className="format-select" />
          </Form.Item>
          <Button type="primary" htmlType="submit" icon={<PlusOutlined />}><FormattedMessage id="topics.add" /></Button>
        </Form>
      </div>
      {topics.length === 0 ? (
        <EmptyState icon={<BulbOutlined />} title={intl.formatMessage({ id: 'topics.empty' })} text={intl.formatMessage({ id: 'topics.emptyText' })} />
      ) : (
        <>
          <div className="sf-grid">{live.map(card)}</div>
          {used.length > 0 && (
            <>
              <div className="sf-section-title"><h2><FormattedMessage id="topics.used" /></h2></div>
              <div className="sf-grid">{used.map(card)}</div>
            </>
          )}
        </>
      )}
    </AppLayout>
  );
};

export const Head = () => <title>Topics · StoryForge</title>;

export default TopicsPage;
