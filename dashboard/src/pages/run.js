import React, { useCallback, useState } from 'react';
import { Link } from 'gatsby';
import {
  Alert, App, Button, Checkbox, Col, Dropdown, Empty, List, Modal, Row, Select, Space, Spin, Statistic, Tabs, Tag,
  Typography,
} from 'antd';
import {
  ArrowLeftOutlined, DownloadOutlined, MoreOutlined, PlayCircleOutlined, RedoOutlined, StopOutlined,
} from '@ant-design/icons';
import { FormattedMessage, useIntl } from 'react-intl';
import { useDispatch, useSelector } from 'react-redux';
import ApiRoutes from '../api/ApiRoutes';
import AppLayout from '../components/AppLayout';
import ExecutionTable from '../components/ExecutionTable';
import GrowthView from '../components/GrowthView';
import PublishKitView from '../components/PublishKitView';
import QaReportView from '../components/QaReportView';
import RevisePanel from '../components/RevisePanel';
import RunProgress from '../components/RunProgress';
import StatusBadge from '../components/StatusBadge';
import usePolling from '../hooks/usePolling';
import useNotify from '../hooks/useNotify';
import useRunFile from '../hooks/useRunFile';
import {
  cancelRun, fetchRun, rerunStage, resumeRun, selectRunDetail, vetoRun,
} from '../store/runs/runsSlice';
import { minutes, relativeTime } from '../utils/time';

const ACTIVE = ['QUEUED', 'RUNNING'];
const RESUMABLE = ['FAILED', 'BLOCKED', 'CANCELLED'];

const formatBytes = (bytes) => (bytes > 1048576 ? `${(bytes / 1048576).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`);

/** Right of the player: thumbnails, title options and the description, each one click to copy. */
const PublishPanel = ({ run, pkg, thumbnail }) => {
  if (!pkg && !thumbnail) return null;
  return (
    <>
      {thumbnail && (
        <div className="sf-panel">
          <div className="sf-panel-title"><FormattedMessage id="run.thumbnail" /></div>
          <img className="thumbnail" alt="thumbnail" src={`${ApiRoutes.runFile(run.id, 'package', thumbnail.name)}?v=${thumbnail.sizeBytes}`} />
          {pkg && (pkg.thumbnailAlternates || []).length > 0 && (
            <Row gutter={8} className="thumb-alternates">
              {pkg.thumbnailAlternates.map((path) => (
                <Col span={12} key={path}>
                  <img className="thumbnail" alt="alternate thumbnail" src={ApiRoutes.absolute(`/api/runs/${run.id}/files/${path}`)} />
                </Col>
              ))}
            </Row>
          )}
        </div>
      )}
      {pkg && (
        <div className="sf-panel">
          <div className="sf-panel-title"><FormattedMessage id="run.titles" /></div>
          {(pkg.titles || []).map((t) => (
            <div className="sf-copy-row" key={t.title}>
              <span className="sf-copy-text">{t.title}</span>
              {t.score != null && <span className="sf-score">{t.score}</span>}
              <Typography.Text copyable={{ text: t.title }} />
            </div>
          ))}
          <div className="sf-panel-title" style={{ marginTop: 18 }}>
            <FormattedMessage id="run.description" />
            {' '}
            <Typography.Text copyable={{ text: pkg.description }} />
          </div>
          <pre className="text-block" style={{ maxHeight: 260 }}>{pkg.description}</pre>
        </div>
      )}
    </>
  );
};

const RunPage = ({ location }) => {
  const intl = useIntl();
  const dispatch = useDispatch();
  const notify = useNotify();
  const { modal } = App.useApp();
  const id = Number(new URLSearchParams(location.search).get('id'));
  const stored = useSelector(selectRunDetail);
  const detail = stored && stored.run.id === id ? stored : null;
  const [rerunOpen, setRerunOpen] = useState(false);
  const [rerunTarget, setRerunTarget] = useState(null);
  const [fresh, setFresh] = useState(false);

  const refresh = useCallback(() => {
    if (id) {
      dispatch(fetchRun({ id }));
    }
  }, [dispatch, id]);
  usePolling(refresh, 3000, !detail || ACTIVE.includes(detail.run.status));

  const qaReport = useRunFile(detail, 'qa', 'qa_report.json');
  const pkg = useRunFile(detail, 'package', 'package.json');
  const growth = useRunFile(detail, 'package', 'growth.json');
  const motion = useRunFile(detail, 'visual_plan', 'motion.json');
  const script = useRunFile(detail, 'critique', 'script.sfm.md');
  const publication = useRunFile(detail, 'publish', 'publication.json');
  const kit = useRunFile(detail, 'publish', 'kit.json');

  const onFail = (error) => notify.error(intl.formatMessage({ id: 'error.request' }, { message: error.message }));

  if (!detail) {
    return (
      <AppLayout location={location}>
        <Spin size="large" style={{ display: 'block', margin: '120px auto' }} />
      </AppLayout>
    );
  }

  const { run } = detail;
  const active = ACTIVE.includes(run.status);
  const video = detail.files.find((f) => f.stage === 'assemble' && f.name === 'final_16x9.mp4');
  const thumbnail = detail.files.find((f) => f.stage === 'package' && f.name === 'thumbnail.png');
  const videoUrl = video ? `${ApiRoutes.runFile(run.id, 'assemble', video.name)}?v=${video.sizeBytes}` : null;
  const reviewPages = detail.files.filter((f) => f.stage === 'qa' && /^review_\d+\.jpg$/.test(f.name))
    .sort((a, b) => a.name.localeCompare(b.name));

  const cancel = () => modal.confirm({
    title: intl.formatMessage({ id: 'run.cancelTitle' }),
    content: intl.formatMessage({ id: 'run.cancelText' }),
    okText: intl.formatMessage({ id: 'run.cancel' }),
    okButtonProps: { danger: true },
    onOk: () => dispatch(cancelRun({ id, onFail })),
  });

  const more = {
    items: [
      { key: 'rerun', icon: <RedoOutlined />, label: intl.formatMessage({ id: 'run.rerunMenu' }), disabled: run.status === 'RUNNING' },
      ...(videoUrl ? [{ key: 'download', icon: <DownloadOutlined />, label: <a href={videoUrl} download><FormattedMessage id="kit.downloadVideo" /></a> }] : []),
    ],
    onClick: ({ key }) => { if (key === 'rerun') setRerunOpen(true); },
  };

  const details = (
    <>
      <Row gutter={16} className="stats-row">
        <Col xs={12} md={6}><Statistic title={intl.formatMessage({ id: 'run.cost' })} prefix="$" value={detail.totalCostUsd} precision={2} /></Col>
        <Col xs={12} md={6}><Statistic title={intl.formatMessage({ id: 'run.tokens' })} value={`${detail.totalInputTokens} / ${detail.totalOutputTokens}`} /></Col>
        <Col xs={12} md={6}><Statistic title={intl.formatMessage({ id: 'run.target' })} value={minutes(run.targetSeconds)} /></Col>
        <Col xs={12} md={6}><Statistic title={intl.formatMessage({ id: 'run.profile' })} value={run.profile} /></Col>
      </Row>
      <ExecutionTable executions={detail.executions} />
      <div className="sf-panel-title" style={{ marginTop: 24 }}><FormattedMessage id="run.tab.files" /></div>
      <List
        size="small"
        dataSource={detail.files}
        renderItem={(file) => (
          <List.Item extra={formatBytes(file.sizeBytes)}>
            <a href={ApiRoutes.absolute(file.url)} target="_blank" rel="noreferrer">{`${file.stage}/${file.name}`}</a>
          </List.Item>
        )}
      />
    </>
  );

  return (
    <AppLayout location={location} title={run.topicTitle}>
      <Link to="/" className="sf-back"><ArrowLeftOutlined /> <FormattedMessage id="run.back" /></Link>
      <div className="sf-page-header">
        <div style={{ minWidth: 0 }}>
          <Typography.Title level={2} className="sf-page-title">{run.topicTitle}</Typography.Title>
          <div className="sf-chips">
            <StatusBadge status={run.status} inline />
            <span className="sf-chip">{`#${run.id}`}</span>
            <span className="sf-chip">{minutes(run.targetSeconds)}</span>
            <span className="sf-chip">{intl.formatMessage({ id: `format.${run.format}` })}</span>
            <span className="sf-chip">{relativeTime(run.createdAt)}</span>
            {!active && <RunProgress run={run} />}
          </div>
          {run.statusReason && !active && run.status !== 'COMPLETED' && (
            <Alert type={run.status === 'FAILED' ? 'error' : 'warning'} showIcon style={{ marginTop: 14 }} message={run.statusReason} />
          )}
        </div>
        <Space wrap>
          {active && <Button danger icon={<StopOutlined />} onClick={cancel}><FormattedMessage id="run.cancel" /></Button>}
          {RESUMABLE.includes(run.status) && (
            <Button type="primary" icon={<PlayCircleOutlined />} onClick={() => dispatch(resumeRun({ id, onFail }))}>
              <FormattedMessage id="run.resume" />
            </Button>
          )}
          <Dropdown menu={more} trigger={['click']}>
            <Button icon={<MoreOutlined />}><FormattedMessage id="run.more" /></Button>
          </Dropdown>
        </Space>
      </div>

      {active && <RunProgress run={run} />}
      {active && (
        <Typography.Text type="secondary" style={{ display: 'block', marginTop: 8 }}>
          <FormattedMessage id="run.keepAwake" />
        </Typography.Text>
      )}

      {publication && (
        <Alert
          style={{ marginTop: 16 }}
          type={publication.privacy === 'private-vetoed' ? 'warning' : 'info'}
          showIcon
          message={publication.privacy === 'private-vetoed'
            ? intl.formatMessage({ id: 'publish.vetoed' })
            : intl.formatMessage({ id: 'publish.scheduled' }, { when: publication.publishAt ? new Date(publication.publishAt).toLocaleString() : '–' })}
          description={(
            <Space wrap>
              <a href={publication.url} target="_blank" rel="noreferrer">{publication.url}</a>
              {(publication.warnings || []).map((w) => <Tag color="orange" key={w}>{w}</Tag>)}
              {publication.privacy !== 'private-vetoed' && (
                <Button danger size="small" onClick={() => dispatch(vetoRun({ id, onFail }))}>
                  <FormattedMessage id="publish.veto" />
                </Button>
              )}
            </Space>
          )}
        />
      )}

      {videoUrl && (
        <Row gutter={[24, 16]} style={{ marginTop: active ? 24 : 4 }}>
          <Col xs={24} xl={15}>
            <video className="sf-player" controls src={videoUrl} poster={thumbnail ? ApiRoutes.runFile(run.id, 'package', thumbnail.name) : undefined} />
          </Col>
          <Col xs={24} xl={9}>
            <PublishPanel run={run} pkg={pkg} thumbnail={thumbnail} />
          </Col>
        </Row>
      )}

      <Tabs
        className="sf-tabs"
        defaultActiveKey={kit ? 'kit' : (videoUrl ? 'revise' : 'script')}
        items={[
          { key: 'kit', label: intl.formatMessage({ id: 'run.tab.kit' }), children: <PublishKitView kit={kit} runId={run.id} /> },
          { key: 'growth', label: intl.formatMessage({ id: 'run.tab.growth' }), children: <GrowthView growth={growth} /> },
          {
            key: 'revise',
            label: intl.formatMessage({ id: 'run.tab.revise' }),
            children: <RevisePanel run={run} busy={active} videoUrl={videoUrl} />,
          },
          {
            key: 'script',
            label: intl.formatMessage({ id: 'run.tab.script' }),
            children: script ? <pre className="text-block">{script}</pre> : <Empty description={<FormattedMessage id="run.noScript" />} />,
          },
          {
            key: 'shots',
            label: intl.formatMessage({ id: 'run.tab.shots' }),
            children: reviewPages.length ? (
              <Space direction="vertical" size="middle" className="full-width">
                <Typography.Text type="secondary"><FormattedMessage id="run.shotsHelp" /></Typography.Text>
                {Array.isArray(motion) && motion.length ? (
                  <Space wrap>
                    <Typography.Text strong><FormattedMessage id="run.animations" /></Typography.Text>
                    {motion.map((m) => (
                      <Tag key={m.shot} color="blue">
                        {`${Math.floor(m.start / 60)}:${String(Math.floor(m.start % 60)).padStart(2, '0')} ${m.animation.template}`}
                        {m.animation.title ? ` · ${m.animation.title}` : ''}
                      </Tag>
                    ))}
                  </Space>
                ) : null}
                {reviewPages.map((page) => {
                  const url = `${ApiRoutes.runFile(run.id, 'qa', page.name)}?v=${page.sizeBytes}`;
                  return (
                    <a key={page.name} href={url} target="_blank" rel="noreferrer">
                      <img className="thumbnail" alt={page.name} src={url} loading="lazy" />
                    </a>
                  );
                })}
              </Space>
            ) : <Empty description={<FormattedMessage id="run.noShots" />} />,
          },
          { key: 'qa', label: intl.formatMessage({ id: 'run.tab.qa' }), children: <QaReportView report={qaReport} /> },
          { key: 'details', label: intl.formatMessage({ id: 'run.tab.details' }), children: details },
        ]}
      />

      <Modal
        open={rerunOpen}
        title={intl.formatMessage({ id: 'run.rerunMenu' })}
        okText={intl.formatMessage({ id: 'run.rerun' })}
        okButtonProps={{ disabled: !rerunTarget }}
        onCancel={() => setRerunOpen(false)}
        onOk={() => {
          dispatch(rerunStage({ id, stage: rerunTarget, fresh, onFail }));
          setRerunOpen(false);
        }}
      >
        <Space direction="vertical" className="full-width">
          <Typography.Text type="secondary"><FormattedMessage id="run.rerunHelp" /></Typography.Text>
          <Select
            className="full-width"
            placeholder={intl.formatMessage({ id: 'run.rerunPick' })}
            value={rerunTarget}
            onChange={setRerunTarget}
            options={run.stages.map((s) => ({ value: s.stage, label: intl.formatMessage({ id: `stage.${s.stage}` }) }))}
          />
          <Checkbox checked={fresh} onChange={(e) => setFresh(e.target.checked)}><FormattedMessage id="run.fresh" /></Checkbox>
        </Space>
      </Modal>
    </AppLayout>
  );
};

export const Head = () => <title>Video · StoryForge</title>;

export default RunPage;
