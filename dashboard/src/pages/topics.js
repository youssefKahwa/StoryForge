import React, { useCallback } from 'react';
import { navigate } from 'gatsby';
import {
  Button, Card, Empty, Form, Input, Popconfirm, Select, Space, Table, Tag, Typography, message,
} from 'antd';
import { FormattedMessage, useIntl } from 'react-intl';
import { useDispatch, useSelector } from 'react-redux';
import AppLayout from '../components/AppLayout';
import usePolling from '../hooks/usePolling';
import { createRun } from '../store/runs/runsSlice';
import {
  archiveTopic, createTopic, fetchTopics, selectTopics,
} from '../store/topics/topicsSlice';

const FORMATS = ['GREAT_STORIES', 'FILM_ANALYSIS'];

const TopicsPage = ({ location }) => {
  const intl = useIntl();
  const dispatch = useDispatch();
  const [form] = Form.useForm();
  const topics = useSelector(selectTopics);
  const refresh = useCallback(() => dispatch(fetchTopics()), [dispatch]);
  usePolling(refresh, 30000);

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

  const columns = [
    { title: intl.formatMessage({ id: 'topics.titleField' }), dataIndex: 'title' },
    { title: intl.formatMessage({ id: 'topics.notes' }), dataIndex: 'notes', ellipsis: true },
    { title: intl.formatMessage({ id: 'newRun.format' }), dataIndex: 'format', width: 150 },
    {
      title: intl.formatMessage({ id: 'runs.col.status' }),
      dataIndex: 'status',
      width: 90,
      render: (status) => <Tag color={status === 'NEW' ? 'blue' : 'default'}>{status}</Tag>,
    },
    {
      width: 220,
      render: (_, topic) => (
        <Space>
          <Button size="small" type="primary" disabled={topic.status !== 'NEW'} onClick={() => start(topic)}>
            <FormattedMessage id="topics.start" />
          </Button>
          <Popconfirm title="Archive this topic?" onConfirm={() => dispatch(archiveTopic({ id: topic.id, onFail }))}>
            <Button size="small"><FormattedMessage id="topics.archive" /></Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <AppLayout location={location}>
      <Typography.Title level={3}><FormattedMessage id="topics.title" /></Typography.Title>
      <Card size="small" className="topic-form">
        <Form form={form} layout="inline" onFinish={add} initialValues={{ format: 'GREAT_STORIES' }}>
          <Form.Item name="title" rules={[{ required: true, max: 300 }]}>
            <Input placeholder={intl.formatMessage({ id: 'topics.titleField' })} className="topic-title-input" />
          </Form.Item>
          <Form.Item name="notes">
            <Input placeholder={intl.formatMessage({ id: 'topics.notes' })} />
          </Form.Item>
          <Form.Item name="format">
            <Select options={FORMATS.map((format) => ({ value: format, label: format }))} className="format-select" />
          </Form.Item>
          <Button type="primary" htmlType="submit"><FormattedMessage id="topics.add" /></Button>
        </Form>
      </Card>
      <Table
        rowKey="id"
        columns={columns}
        dataSource={topics}
        locale={{ emptyText: <Empty description={<FormattedMessage id="topics.empty" />} /> }}
      />
    </AppLayout>
  );
};

export const Head = () => <title>Topics · StoryForge</title>;

export default TopicsPage;
