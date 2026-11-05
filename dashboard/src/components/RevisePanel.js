import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert, Button, Card, Col, Empty, Input, List, Popconfirm, Radio, Row, Select, Space, Spin, Tag, Typography,
} from 'antd';
import useNotify from '../hooks/useNotify';
import { AimOutlined, DeleteOutlined } from '@ant-design/icons';
import { FormattedMessage, useIntl } from 'react-intl';
import { useDispatch, useSelector } from 'react-redux';
import ApiRoutes from '../api/ApiRoutes';
import {
  addRevision, applyRevisions, deleteRevision, fetchRevisions, fetchTimeline, selectRevisions, selectTimeline,
} from '../store/revisions/revisionsSlice';

const STATUS_COLORS = {
  PENDING: 'blue', QUEUED: 'gold', APPLIED: 'green', FAILED: 'red',
};
const KIND_COLORS = {
  IMAGE: 'purple', NARRATION: 'cyan', MUSIC: 'magenta', PACKAGE: 'orange',
};

const clock = (seconds) => {
  const s = Math.max(0, Math.floor(seconds || 0));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
};

const RevisePanel = ({ run, videoUrl, busy }) => {
  const intl = useIntl();
  const message = useNotify();
  const dispatch = useDispatch();
  const t = (id, values) => intl.formatMessage({ id }, values);
  const player = useRef(null);
  const timeline = useSelector(selectTimeline(run.id));
  const revisions = useSelector(selectRevisions(run.id));
  const [selected, setSelected] = useState(null);
  const [kind, setKind] = useState('IMAGE');
  const [note, setNote] = useState('');
  const [packageTarget, setPackageTarget] = useState('title');
  const [applying, setApplying] = useState(false);
  const onFail = (error) => message.error(t('error.request', { message: error.message }));

  useEffect(() => {
    dispatch(fetchTimeline({ runId: run.id, onFail: () => {} }));
    dispatch(fetchRevisions({ runId: run.id, onFail }));
  }, [dispatch, run.id, run.status]);

  const scene = useMemo(() => (timeline && selected
    ? timeline.scenes.find((s) => s.id === selected.sceneId) : null), [timeline, selected]);
  const pending = revisions.filter((r) => r.status === 'PENDING');

  if (!timeline) {
    return <Empty description={<FormattedMessage id="revise.notReady" />} />;
  }

  const select = (shot, seek) => {
    setSelected(shot);
    if (!shot.picture && kind === 'IMAGE') setKind('NARRATION');
    if (seek && player.current) player.current.currentTime = shot.start + 0.05;
  };

  const markNow = () => {
    const now = player.current ? player.current.currentTime : 0;
    const shot = timeline.shots.find((s) => now >= s.start && now < s.end) || timeline.shots[timeline.shots.length - 1];
    if (player.current) player.current.pause();
    select(shot, false);
  };

  const target = () => {
    if (kind === 'IMAGE') return selected && selected.key;
    if (kind === 'PACKAGE') return packageTarget;
    return selected && selected.sceneId;
  };

  const add = () => dispatch(addRevision({
    runId: run.id,
    revision: {
      kind, target: target(), atSec: selected ? selected.start : null, note,
    },
    onSuccess: () => {
      setNote('');
      message.success(t('revise.added'));
    },
    onFail,
  }));

  const apply = () => {
    setApplying(true);
    dispatch(applyRevisions({
      runId: run.id,
      onSuccess: () => {
        setApplying(false);
        message.success(t('revise.applied'));
      },
      onFail: (error) => {
        setApplying(false);
        onFail(error);
      },
    }));
  };

  const needsSelection = kind !== 'PACKAGE' && !selected;
  const imageUrl = (asset) => (asset ? ApiRoutes.absolute(`/api/runs/${run.id}/files/${asset}`) : null);

  return (
    <Space direction="vertical" size="large" className="full-width">
      <Alert type="info" showIcon message={<FormattedMessage id="revise.help" />} />
      <Row gutter={24}>
        <Col xs={24} xl={14}>
          {videoUrl ? <video ref={player} className="video-player" controls src={videoUrl} /> : <Empty />}
          <Button className="mark-button" icon={<AimOutlined />} onClick={markNow} disabled={!videoUrl}>
            <FormattedMessage id="revise.markNow" />
          </Button>
        </Col>
        <Col xs={24} xl={10}>
          <Card size="small" title={selected ? t('revise.selected', { time: clock(selected.start), scene: selected.sceneId }) : t('revise.pick')}>
            <Space direction="vertical" className="full-width">
              {selected && selected.picture && selected.asset && (
                <img className="thumbnail" alt="selected shot" src={imageUrl(selected.asset)} />
              )}
              {selected && <Typography.Text type="secondary">{`“${selected.spoken || ''}”`}</Typography.Text>}
              <Radio.Group value={kind} onChange={(e) => setKind(e.target.value)} buttonStyle="solid">
                <Radio.Button value="IMAGE" disabled={selected && !selected.picture}><FormattedMessage id="revise.kind.IMAGE" /></Radio.Button>
                <Radio.Button value="NARRATION"><FormattedMessage id="revise.kind.NARRATION" /></Radio.Button>
                <Radio.Button value="MUSIC"><FormattedMessage id="revise.kind.MUSIC" /></Radio.Button>
                <Radio.Button value="PACKAGE"><FormattedMessage id="revise.kind.PACKAGE" /></Radio.Button>
              </Radio.Group>
              {kind === 'NARRATION' && scene && (
                <Typography.Paragraph className="text-block" ellipsis={{ rows: 4, expandable: true }}>{scene.narration}</Typography.Paragraph>
              )}
              {kind === 'PACKAGE' && (
                <Select value={packageTarget} onChange={setPackageTarget} className="full-width"
                  options={timeline.packageTargets.map((v) => ({ value: v, label: t(`revise.package.${v}`) }))} />
              )}
              {kind === 'MUSIC' ? (
                <Select value={note || undefined} onChange={setNote} className="full-width" placeholder={t('revise.musicPlaceholder')}
                  options={timeline.musicChoices.map((v) => ({ value: v, label: t(`revise.music.${v}`, { mood: v }) }))} />
              ) : (
                <Input.TextArea rows={3} value={note} onChange={(e) => setNote(e.target.value)} maxLength={2000}
                  placeholder={t(`revise.placeholder.${kind}`)} />
              )}
              <Button type="primary" onClick={add} disabled={!note || needsSelection || busy}>
                <FormattedMessage id="revise.add" />
              </Button>
            </Space>
          </Card>
        </Col>
      </Row>

      <div className="shot-strip">
        {timeline.shots.map((shot) => (
          <button
            type="button"
            key={shot.key}
            className={`shot-thumb${selected && selected.key === shot.key ? ' selected' : ''}`}
            onClick={() => select(shot, true)}
            title={shot.spoken}
          >
            {shot.picture && shot.asset ? <img alt={shot.key} src={imageUrl(shot.asset)} loading="lazy" /> : <span className="shot-graphic">Aa</span>}
            <span className="shot-time">{clock(shot.start)}</span>
          </button>
        ))}
      </div>

      <Card size="small" title={t('revise.listTitle', { count: revisions.length })}
        extra={(
          <Popconfirm title={t('revise.applyConfirm')} onConfirm={apply} disabled={!pending.length || busy}>
            <Button type="primary" loading={applying} disabled={!pending.length || busy}>
              {t('revise.apply', { count: pending.length })}
            </Button>
          </Popconfirm>
        )}>
        {applying && <Spin tip={t('revise.applying')} />}
        <List
          size="small"
          dataSource={[...revisions].reverse()}
          locale={{ emptyText: t('revise.empty') }}
          renderItem={(r) => (
            <List.Item actions={(r.status === 'PENDING' || r.status === 'FAILED') ? [
              <Button key="del" size="small" type="text" icon={<DeleteOutlined />}
                onClick={() => dispatch(deleteRevision({ runId: run.id, id: r.id, onFail }))} />,
            ] : []}>
              <Space wrap>
                <Tag color={KIND_COLORS[r.kind]}>{t(`revise.kind.${r.kind}`)}</Tag>
                <Typography.Text type="secondary">{r.atSec != null ? clock(r.atSec) : r.target}</Typography.Text>
                <Typography.Text>{r.note}</Typography.Text>
                <Tag color={STATUS_COLORS[r.status]}>{r.status}</Tag>
                {r.status === 'FAILED' && r.result && <Typography.Text type="danger">{r.result}</Typography.Text>}
              </Space>
            </List.Item>
          )}
        />
      </Card>
    </Space>
  );
};

export default RevisePanel;
