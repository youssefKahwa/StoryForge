import React, { useCallback, useEffect, useState } from 'react';
import { navigate } from 'gatsby';
import {
  Alert, Button, Card, Col, Collapse, Descriptions, Input, InputNumber, List, Radio, Row, Select, Space, Spin, Steps,
  Tag, Typography, message,
} from 'antd';
import { PlayCircleOutlined } from '@ant-design/icons';
import { FormattedMessage, useIntl } from 'react-intl';
import { useDispatch, useSelector } from 'react-redux';
import ApiRoutes from '../api/ApiRoutes';
import AppLayout from '../components/AppLayout';
import usePolling from '../hooks/usePolling';
import { createRun } from '../store/runs/runsSlice';
import { fetchPreferences } from '../store/settings/settingsSlice';
import {
  createInspiration, fetchInspiration, fetchVoices, selectInspiration, selectVoices, setInspiration,
} from '../store/wizard/wizardSlice';

const FORMATS = ['GREAT_STORIES', 'FILM_ANALYSIS'];
const STEPS = ['inspiration', 'angle', 'voice', 'review'];

const NewVideoPage = ({ location }) => {
  const intl = useIntl();
  const dispatch = useDispatch();
  const inspiration = useSelector(selectInspiration);
  const voices = useSelector(selectVoices);
  const [step, setStep] = useState(0);
  const [urls, setUrls] = useState(['', '', '']);
  const [pasted, setPasted] = useState('');
  const [busy, setBusy] = useState(false);
  const [draft, setDraft] = useState({ topic: '', angle: '', minutes: 12, format: 'GREAT_STORIES', voice: null });
  const [playing, setPlaying] = useState(null);

  const onFail = (error) => message.error(intl.formatMessage({ id: 'error.request' }, { message: error.message }));
  const pending = inspiration && inspiration.status === 'PENDING';

  useEffect(() => {
    dispatch(setInspiration(null));
    dispatch(fetchVoices({ onFail }));
    dispatch(fetchPreferences({
      onSuccess: (prefs) => setDraft((d) => ({
        ...d, minutes: prefs.defaultMinutes, format: prefs.defaultFormat, voice: prefs.defaultVoice || d.voice,
      })),
    }));
  }, [dispatch]);

  useEffect(() => {
    if (!draft.voice && voices.length) {
      const preferred = voices.find((voice) => voice.isDefault) || voices[0];
      setDraft((d) => ({ ...d, voice: preferred.id }));
    }
  }, [voices, draft.voice]);

  const poll = useCallback(() => {
    if (inspiration && inspiration.id) {
      dispatch(fetchInspiration({ id: inspiration.id }));
    }
  }, [dispatch, inspiration && inspiration.id]);
  usePolling(poll, 3000, Boolean(pending));

  useEffect(() => {
    if (inspiration && inspiration.status === 'READY' && inspiration.ideas && step === 0) {
      const { ideas } = inspiration;
      setDraft((d) => ({
        ...d,
        topic: ideas.topic || d.topic,
        angle: ideas.angles && ideas.angles[0] ? ideas.angles[0].angle : d.angle,
        minutes: ideas.suggestedMinutes || d.minutes,
        format: ideas.suggestedFormat || d.format,
      }));
      setStep(1);
    }
  }, [inspiration, step]);

  const analyze = () => {
    setBusy(true);
    dispatch(createInspiration({
      body: { urls: urls.filter((u) => u.trim()), pastedText: pasted.trim() || null },
      onSuccess: () => setBusy(false),
      onFail: (e) => { setBusy(false); onFail(e); },
    }));
  };

  const start = () => {
    setBusy(true);
    dispatch(createRun({
      body: {
        topicTitle: draft.topic,
        angle: draft.angle,
        format: draft.format,
        targetMinutes: draft.minutes,
        voice: draft.voice,
        inspirationId: inspiration && inspiration.status === 'READY' ? inspiration.id : null,
      },
      onSuccess: (run) => navigate(`/run?id=${run.id}`),
      onFail: (e) => { setBusy(false); onFail(e); },
    }));
  };

  const play = (id) => {
    setPlaying(id);
    const audio = new Audio(ApiRoutes.voiceSample(id));
    audio.onended = () => setPlaying(null);
    audio.onerror = () => { setPlaying(null); message.warning(intl.formatMessage({ id: 'wizard.voice.help' })); };
    audio.play().catch(() => setPlaying(null));
  };

  const inspirationStep = (
    <Card>
      <Typography.Paragraph type="secondary"><FormattedMessage id="wizard.inspiration.help" /></Typography.Paragraph>
      <Space direction="vertical" className="full-width">
        {urls.map((url, i) => (
          <Input
            key={i}
            value={url}
            disabled={pending}
            placeholder={intl.formatMessage({ id: 'wizard.inspiration.url' }, { n: i + 1 })}
            onChange={(e) => setUrls(urls.map((u, j) => (j === i ? e.target.value : u)))}
          />
        ))}
        <Collapse ghost>
          <Collapse.Panel key="paste" header={intl.formatMessage({ id: 'wizard.inspiration.paste' })}>
            <Typography.Text type="secondary"><FormattedMessage id="wizard.inspiration.pasteHint" /></Typography.Text>
            <Input.TextArea rows={6} value={pasted} disabled={pending} onChange={(e) => setPasted(e.target.value)} />
          </Collapse.Panel>
        </Collapse>
        {pending && <Alert type="info" showIcon icon={<Spin size="small" />} message={<FormattedMessage id="wizard.inspiration.working" />} />}
        {inspiration && inspiration.status === 'FAILED' && (
          <Alert type="error" showIcon message={<FormattedMessage id="wizard.inspiration.failed" />} description={inspiration.error} />
        )}
        <Space>
          <Button type="primary" loading={busy || pending} disabled={!urls.some((u) => u.trim()) && !pasted.trim()} onClick={analyze}>
            <FormattedMessage id="wizard.inspiration.analyze" />
          </Button>
          <Button disabled={pending} onClick={() => setStep(1)}><FormattedMessage id="wizard.inspiration.skip" /></Button>
        </Space>
      </Space>
    </Card>
  );

  const ideas = inspiration && inspiration.ideas;
  const angleStep = (
    <Row gutter={16}>
      {ideas && (
        <Col xs={24} xl={10}>
          <Card size="small" title={<FormattedMessage id="wizard.angle.story" />}>
            <Typography.Paragraph>{ideas.story}</Typography.Paragraph>
            <Tag color="blue"><FormattedMessage id="wizard.angle.leads" values={{ count: (ideas.leads || []).length }} /></Tag>
            {inspiration.videos.map((video) => (
              <div key={video.url || video.title}>
                <Typography.Text type="secondary">{`${video.title || ''} · ${video.language || '?'} · ${video.source}`}</Typography.Text>
              </div>
            ))}
          </Card>
          <Card size="small" title={<FormattedMessage id="wizard.angle.whatWorked" />}>
            <List size="small" dataSource={ideas.whatWorked || []} renderItem={(item) => <List.Item>{item}</List.Item>} />
          </Card>
          {ideas.cautions && ideas.cautions.length > 0 && (
            <Alert type="warning" message={<FormattedMessage id="wizard.angle.cautions" />} description={ideas.cautions.join(' · ')} />
          )}
        </Col>
      )}
      <Col xs={24} xl={ideas ? 14 : 24}>
        <Card size="small">
          {ideas && (
            <>
              <Typography.Title level={5}><FormattedMessage id="wizard.angle.pick" /></Typography.Title>
              <Radio.Group
                className="full-width"
                value={draft.angle}
                onChange={(e) => {
                  const picked = ideas.angles.find((a) => a.angle === e.target.value);
                  setDraft({ ...draft, angle: e.target.value, topic: picked ? picked.title : draft.topic });
                }}
              >
                <Space direction="vertical" className="full-width">
                  {ideas.angles.map((a) => (
                    <Radio key={a.angle} value={a.angle}>
                      <strong>{a.title}</strong>
                      {` · ${a.angle}`}
                      <div><Typography.Text type="secondary">{a.whyOriginal}</Typography.Text></div>
                    </Radio>
                  ))}
                </Space>
              </Radio.Group>
            </>
          )}
          <Descriptions column={1} size="small" className="wizard-fields">
            <Descriptions.Item label={intl.formatMessage({ id: 'wizard.angle.topic' })}>
              <Input value={draft.topic} maxLength={300} onChange={(e) => setDraft({ ...draft, topic: e.target.value })} />
            </Descriptions.Item>
            <Descriptions.Item label={intl.formatMessage({ id: 'wizard.angle.angle' })}>
              <Input.TextArea rows={2} value={draft.angle} maxLength={2000} onChange={(e) => setDraft({ ...draft, angle: e.target.value })} />
            </Descriptions.Item>
            <Descriptions.Item label={intl.formatMessage({ id: 'newRun.format' })}>
              <Select value={draft.format} onChange={(format) => setDraft({ ...draft, format })} options={FORMATS.map((f) => ({ value: f, label: intl.formatMessage({ id: `format.${f}` }) }))} />
            </Descriptions.Item>
            <Descriptions.Item label={intl.formatMessage({ id: 'wizard.angle.minutes' })}>
              <InputNumber min={1} max={30} value={draft.minutes} onChange={(minutes) => setDraft({ ...draft, minutes })} />
            </Descriptions.Item>
          </Descriptions>
        </Card>
      </Col>
    </Row>
  );

  const voiceStep = (
    <Card>
      <Typography.Paragraph type="secondary"><FormattedMessage id="wizard.voice.help" /></Typography.Paragraph>
      <Radio.Group value={draft.voice} onChange={(e) => setDraft({ ...draft, voice: e.target.value })}>
        <Space direction="vertical">
          {voices.map((voice) => (
            <Space key={voice.id}>
              <Radio value={voice.id}>
                {`${voice.label} · ${voice.gender} · ${voice.accent} · grade ${voice.grade}`}
                {voice.isDefault && <Tag className="tag-gap"><FormattedMessage id="wizard.voice.default" /></Tag>}
              </Radio>
              <Button size="small" icon={<PlayCircleOutlined />} loading={playing === voice.id} onClick={() => play(voice.id)} />
            </Space>
          ))}
        </Space>
      </Radio.Group>
    </Card>
  );

  const reviewStep = (
    <Card>
      <Descriptions column={1} bordered size="small">
        <Descriptions.Item label={intl.formatMessage({ id: 'wizard.angle.topic' })}>{draft.topic}</Descriptions.Item>
        <Descriptions.Item label={intl.formatMessage({ id: 'wizard.angle.angle' })}>{draft.angle || '–'}</Descriptions.Item>
        <Descriptions.Item label={intl.formatMessage({ id: 'newRun.format' })}>{draft.format}</Descriptions.Item>
        <Descriptions.Item label={intl.formatMessage({ id: 'wizard.angle.minutes' })}>{draft.minutes}</Descriptions.Item>
        <Descriptions.Item label={intl.formatMessage({ id: 'wizard.step.voice' })}>{draft.voice}</Descriptions.Item>
        <Descriptions.Item label={intl.formatMessage({ id: 'wizard.review.inspiredBy' })}>
          {inspiration && inspiration.videos.length ? inspiration.videos.map((v) => v.title).join(' · ') : '–'}
        </Descriptions.Item>
      </Descriptions>
    </Card>
  );

  const content = [inspirationStep, angleStep, voiceStep, reviewStep][step];
  const canNext = step === 1 ? draft.topic.trim().length > 0 : true;

  return (
    <AppLayout location={location}>
      <Typography.Title level={3}><FormattedMessage id="wizard.title" /></Typography.Title>
      <Steps current={step} className="wizard-steps" items={STEPS.map((s) => ({ title: intl.formatMessage({ id: `wizard.step.${s}` }) }))} />
      {content}
      {step > 0 && (
        <Space className="wizard-nav">
          <Button onClick={() => setStep(step - 1)}><FormattedMessage id="wizard.back" /></Button>
          {step < 3 ? (
            <Button type="primary" disabled={!canNext} onClick={() => setStep(step + 1)}><FormattedMessage id="wizard.next" /></Button>
          ) : (
            <Button type="primary" loading={busy} onClick={start}><FormattedMessage id="wizard.review.start" /></Button>
          )}
        </Space>
      )}
    </AppLayout>
  );
};

export const Head = () => <title>New video · StoryForge</title>;

export default NewVideoPage;
