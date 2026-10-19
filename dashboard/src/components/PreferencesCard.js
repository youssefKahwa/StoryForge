import React, { useEffect } from 'react';
import {
  Button, Card, Col, Form, Input, InputNumber, Row, Select, Space, Spin, Switch, message,
} from 'antd';
import { FormattedMessage, useIntl } from 'react-intl';
import { useDispatch, useSelector } from 'react-redux';
import {
  fetchPreferences, resetPreferences, savePreferences, selectPreferences,
} from '../store/settings/settingsSlice';
import { fetchVoices, selectVoices } from '../store/wizard/wizardSlice';

const FORMATS = ['GREAT_STORIES', 'FILM_ANALYSIS'];
const VOLUMES = ['off', 'low', 'normal', 'high'];
const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
const ZONES = ['America/New_York', 'America/Los_Angeles', 'Europe/London', 'Europe/Paris', 'Africa/Casablanca',
  'Asia/Dubai', 'UTC'];

const PreferencesCard = () => {
  const intl = useIntl();
  const dispatch = useDispatch();
  const [form] = Form.useForm();
  const preferences = useSelector(selectPreferences);
  const voices = useSelector(selectVoices);
  const t = (id) => intl.formatMessage({ id });
  const onFail = (error) => message.error(intl.formatMessage({ id: 'error.request' }, { message: error.message }));

  useEffect(() => {
    dispatch(fetchPreferences({ onFail }));
    dispatch(fetchVoices({ onFail }));
  }, [dispatch]);

  useEffect(() => {
    if (preferences) {
      form.setFieldsValue(preferences);
    }
  }, [preferences, form]);

  const save = (values) => dispatch(savePreferences({
    preferences: { ...preferences, ...values },
    onSuccess: () => message.success(t('prefs.saved')),
    onFail,
  }));

  return (
    <Card size="small" title={<FormattedMessage id="prefs.title" />}>
      {!preferences ? <Spin /> : (
        <Form form={form} layout="vertical" onFinish={save} initialValues={preferences}>
          <Row gutter={16}>
            <Col xs={24} md={8}>
              <Form.Item name="defaultVoice" label={t('prefs.voice')}>
                <Select showSearch options={voices.map((v) => ({ value: v.id, label: `${v.label} (${v.accent}, ${v.gender})` }))} />
              </Form.Item>
            </Col>
            <Col xs={12} md={8}>
              <Form.Item name="defaultMinutes" label={t('prefs.minutes')}>
                <InputNumber min={1} max={30} step={1} className="full-width" />
              </Form.Item>
            </Col>
            <Col xs={12} md={8}>
              <Form.Item name="defaultFormat" label={t('prefs.format')}>
                <Select options={FORMATS.map((f) => ({ value: f, label: t(`format.${f}`) }))} />
              </Form.Item>
            </Col>
            <Col xs={12} md={6}>
              <Form.Item name="shortsEnabled" label={t('prefs.shorts')} valuePropName="checked"><Switch /></Form.Item>
            </Col>
            <Col xs={12} md={6}>
              <Form.Item name="shortsCount" label={t('prefs.shortsCount')}>
                <InputNumber min={0} max={5} className="full-width" />
              </Form.Item>
            </Col>
            <Col xs={12} md={6}>
              <Form.Item name="burnCaptions" label={t('prefs.burnCaptions')} valuePropName="checked"
                tooltip={t('prefs.burnCaptionsHelp')}>
                <Switch />
              </Form.Item>
            </Col>
            <Col xs={12} md={6}>
              <Form.Item name="upscaleImages" label={t('prefs.upscale')} valuePropName="checked"
                tooltip={t('prefs.upscaleHelp')}>
                <Switch />
              </Form.Item>
            </Col>
            <Col xs={12} md={6}>
              <Form.Item name="musicVolume" label={t('prefs.music')}>
                <Select options={VOLUMES.map((v) => ({ value: v, label: t(`prefs.music.${v}`) }))} />
              </Form.Item>
            </Col>
            <Col xs={12} md={6}>
              <Form.Item name="publishDay" label={t('prefs.publishDay')}>
                <Select options={DAYS.map((d) => ({ value: d, label: d.charAt(0) + d.slice(1).toLowerCase() }))} />
              </Form.Item>
            </Col>
            <Col xs={12} md={6}>
              <Form.Item name="publishTime" label={t('prefs.publishTime')}
                rules={[{ pattern: /^([01]\d|2[0-3]):[0-5]\d$/, message: 'HH:mm' }]}>
                <Input placeholder="15:00" />
              </Form.Item>
            </Col>
            <Col xs={12} md={6}>
              <Form.Item name="publishZone" label={t('prefs.publishZone')} tooltip={t('prefs.publishZoneHelp')}>
                <Select showSearch options={[...new Set([preferences.publishZone, ...ZONES])].map((z) => ({ value: z, label: z }))} />
              </Form.Item>
            </Col>
            <Col xs={24}>
              <Form.Item name="channelBrief" label={t('prefs.channelBrief')} tooltip={t('prefs.channelBriefHelp')}>
                <Input.TextArea rows={3} maxLength={2000} showCount />
              </Form.Item>
            </Col>
          </Row>
          <Space>
            <Button type="primary" htmlType="submit"><FormattedMessage id="prefs.save" /></Button>
            <Button onClick={() => dispatch(resetPreferences({ onSuccess: () => message.success(t('prefs.reset.done')), onFail }))}>
              <FormattedMessage id="prefs.reset" />
            </Button>
          </Space>
        </Form>
      )}
    </Card>
  );
};

export default PreferencesCard;
