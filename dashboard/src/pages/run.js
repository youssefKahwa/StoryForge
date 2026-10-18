import React, { useCallback, useState } from 'react';
import { Link } from 'gatsby';
import {
  Button, Card, Checkbox, Col, Descriptions, Empty, List, Row, Select, Space, Spin, Statistic, Tabs, Typography, message,
} from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { FormattedMessage, useIntl } from 'react-intl';
import { useDispatch, useSelector } from 'react-redux';
import ApiRoutes from '../api/ApiRoutes';
import AppLayout from '../components/AppLayout';
import ExecutionTable from '../components/ExecutionTable';
import QaReportView from '../components/QaReportView';
import RunStatusTag from '../components/RunStatusTag';
import StageChips from '../components/StageChips';
import usePolling from '../hooks/usePolling';
import useRunFile from '../hooks/useRunFile';
import {
  cancelRun, fetchRun, rerunStage, resumeRun, selectRunDetail,
} from '../store/runs/runsSlice';

const ACTIVE = ['QUEUED', 'RUNNING'];
const RESUMABLE = ['FAILED', 'BLOCKED', 'CANCELLED'];

const formatBytes = (bytes) => (bytes > 1048576 ? `${(bytes / 1048576).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`);

const RunPage = ({ location }) => {
  const intl = useIntl();
  const dispatch = useDispatch();
  const id = Number(new URLSearchParams(location.search).get('id'));
  const stored = useSelector(selectRunDetail);
  const detail = stored && stored.run.id === id ? stored : null;
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
  const script = useRunFile(detail, 'critique', 'script.sfm.md');

  const onFail = (error) => message.error(intl.formatMessage({ id: 'error.request' }, { message: error.message }));

  if (!detail) {
    return (
      <AppLayout location={location}>
        <Spin />
      </AppLayout>
    );
  }

  const { run } = detail;
  const video = detail.files.find((f) => f.stage === 'assemble' && f.name === 'final_16x9.mp4');
  const thumbnail = detail.files.find((f) => f.stage === 'package' && f.name === 'thumbnail.png');

  const overview = (
    <>
      <Row gutter={16} className="stats-row">
        <Col span={6}><Statistic title={intl.formatMessage({ id: 'run.cost' })} prefix="$" value={detail.totalCostUsd} precision={2} /></Col>
        <Col span={6}><Statistic title={intl.formatMessage({ id: 'run.tokens' })} value={`${detail.totalInputTokens} / ${detail.totalOutputTokens}`} /></Col>
        <Col span={6}><Statistic title={intl.formatMessage({ id: 'run.target' })} value={`${Math.round(run.targetSeconds / 6) / 10} min`} /></Col>
        <Col span={6}><Statistic title={intl.formatMessage({ id: 'run.profile' })} value={run.profile} /></Col>
      </Row>
      <ExecutionTable executions={detail.executions} />
    </>
  );

  const videoTab = (
    <Row gutter={24}>
      <Col xs={24} xl={14}>
        {video ? (
          <video className="video-player" controls src={`${ApiRoutes.runFile(run.id, 'assemble', video.name)}?v=${video.sizeBytes}`} />
        ) : <Empty description={<FormattedMessage id="run.noVideo" />} />}
      </Col>
      <Col xs={24} xl={10}>
        {thumbnail && (
          <Card size="small" title={<FormattedMessage id="run.thumbnail" />}>
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
          </Card>
        )}
        {pkg && (
          <>
            <Card size="small" title={<FormattedMessage id="run.titles" />}>
              <List
                size="small"
                dataSource={pkg.titles}
                renderItem={(t) => <List.Item extra={t.score}>{t.title}</List.Item>}
              />
            </Card>
            <Card size="small" title={<FormattedMessage id="run.description" />}>
              <pre className="text-block">{pkg.description}</pre>
            </Card>
          </>
        )}
      </Col>
    </Row>
  );

  const filesTab = (
    <List
      size="small"
      dataSource={detail.files}
      renderItem={(file) => (
        <List.Item extra={formatBytes(file.sizeBytes)}>
          <a href={ApiRoutes.absolute(file.url)} target="_blank" rel="noreferrer">{`${file.stage}/${file.name}`}</a>
        </List.Item>
      )}
    />
  );

  return (
    <AppLayout location={location}>
      <Link to="/"><ArrowLeftOutlined /> <FormattedMessage id="run.back" /></Link>
      <div className="page-header">
        <Space direction="vertical" size={4}>
          <Typography.Title level={3}>{`#${run.id} ${run.topicTitle}`}</Typography.Title>
          <Space>
            <RunStatusTag status={run.status} />
            <Typography.Text type="secondary">{run.state}</Typography.Text>
            {run.statusReason && <Typography.Text type="secondary">{run.statusReason}</Typography.Text>}
          </Space>
          <StageChips stages={run.stages} />
        </Space>
        <Space wrap>
          {ACTIVE.includes(run.status) && (
            <Button danger onClick={() => dispatch(cancelRun({ id, onFail }))}><FormattedMessage id="run.cancel" /></Button>
          )}
          {RESUMABLE.includes(run.status) && (
            <Button type="primary" onClick={() => dispatch(resumeRun({ id, onFail }))}><FormattedMessage id="run.resume" /></Button>
          )}
          <Select
            placeholder={intl.formatMessage({ id: 'run.rerun' })}
            className="rerun-select"
            value={rerunTarget}
            onChange={setRerunTarget}
            options={run.stages.map((s) => ({ value: s.stage, label: intl.formatMessage({ id: `stage.${s.stage}` }) }))}
          />
          <Checkbox checked={fresh} onChange={(e) => setFresh(e.target.checked)}><FormattedMessage id="run.fresh" /></Checkbox>
          <Button
            disabled={!rerunTarget || run.status === 'RUNNING'}
            onClick={() => dispatch(rerunStage({ id, stage: rerunTarget, fresh, onFail }))}
          >
            <FormattedMessage id="run.rerun" />
          </Button>
        </Space>
      </div>
      <Descriptions size="small" column={3} className="run-meta">
        <Descriptions.Item label="Format">{run.format}</Descriptions.Item>
        <Descriptions.Item label="Language">{run.language}</Descriptions.Item>
        <Descriptions.Item label="Created">{new Date(run.createdAt).toLocaleString()}</Descriptions.Item>
      </Descriptions>
      <Tabs
        items={[
          { key: 'overview', label: intl.formatMessage({ id: 'run.tab.overview' }), children: overview },
          { key: 'video', label: intl.formatMessage({ id: 'run.tab.video' }), children: videoTab },
          { key: 'qa', label: intl.formatMessage({ id: 'run.tab.qa' }), children: <QaReportView report={qaReport} /> },
          {
            key: 'script',
            label: intl.formatMessage({ id: 'run.tab.script' }),
            children: script ? <pre className="text-block">{script}</pre> : <Empty description={<FormattedMessage id="run.noScript" />} />,
          },
          { key: 'files', label: intl.formatMessage({ id: 'run.tab.files' }), children: filesTab },
        ]}
      />
    </AppLayout>
  );
};

export const Head = () => <title>Run · StoryForge</title>;

export default RunPage;
