import React, { useEffect } from 'react';
import { Form, Input, InputNumber, Modal, Select, message } from 'antd';
import { FormattedMessage, useIntl } from 'react-intl';
import { useDispatch, useSelector } from 'react-redux';
import { createRun } from '../store/runs/runsSlice';
import { fetchTopics, selectTopics } from '../store/topics/topicsSlice';

const FORMATS = ['GREAT_STORIES', 'FILM_ANALYSIS'];

const NewRunModal = ({ open, onClose, onCreated }) => {
  const [form] = Form.useForm();
  const intl = useIntl();
  const dispatch = useDispatch();
  const topics = useSelector(selectTopics).filter((topic) => topic.status === 'NEW');

  useEffect(() => {
    if (open) {
      dispatch(fetchTopics());
      form.resetFields();
    }
  }, [open, dispatch, form]);

  const submit = (values) => {
    const body = values.seedTopicId
      ? { seedTopicId: values.seedTopicId, targetMinutes: values.targetMinutes }
      : { topicTitle: values.topicTitle, format: values.format, targetMinutes: values.targetMinutes };
    dispatch(createRun({
      body,
      onSuccess: (run) => {
        message.success(intl.formatMessage({ id: 'runs.created' }, { id: run.id }));
        onCreated(run);
      },
      onFail: (error) => message.error(intl.formatMessage({ id: 'error.request' }, { message: error.message })),
    }));
  };

  return (
    <Modal
      open={open}
      title={<FormattedMessage id="newRun.title" />}
      okText={<FormattedMessage id="newRun.submit" />}
      onOk={() => form.submit()}
      onCancel={onClose}
      destroyOnClose
    >
      <Form form={form} layout="vertical" onFinish={submit} initialValues={{ format: 'GREAT_STORIES' }}>
        <Form.Item name="seedTopicId" label={<FormattedMessage id="newRun.seed" />}>
          <Select
            allowClear
            options={topics.map((topic) => ({ value: topic.id, label: topic.title }))}
          />
        </Form.Item>
        <Form.Item noStyle shouldUpdate={(prev, next) => prev.seedTopicId !== next.seedTopicId}>
          {({ getFieldValue }) => !getFieldValue('seedTopicId') && (
            <>
              <Form.Item
                name="topicTitle"
                label={<FormattedMessage id="newRun.topic" />}
                extra={<FormattedMessage id="newRun.topicHint" />}
                rules={[{ required: true, max: 300 }]}
              >
                <Input />
              </Form.Item>
              <Form.Item name="format" label={<FormattedMessage id="newRun.format" />}>
                <Select options={FORMATS.map((format) => ({ value: format, label: format }))} />
              </Form.Item>
            </>
          )}
        </Form.Item>
        <Form.Item
          name="targetMinutes"
          label={<FormattedMessage id="newRun.minutes" />}
          extra={<FormattedMessage id="newRun.minutesHint" />}
        >
          <InputNumber min={0.5} max={60} step={0.5} />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default NewRunModal;
