import React from 'react';
import {
  Alert, Card, Checkbox, Col, Empty, List, Row, Space, Tag, Typography,
} from 'antd';
import { FormattedMessage, useIntl } from 'react-intl';
import ApiRoutes from '../api/ApiRoutes';

const { Paragraph, Text, Title } = Typography;

const fileUrl = (runId, path) => ApiRoutes.absolute(`/api/runs/${runId}/files/${path}`);

const Copyable = ({ label, value, block = false }) => (
  <div className="kit-field">
    <Text type="secondary">{label}</Text>
    {block ? (
      <Paragraph copyable={{ text: value }} className="kit-block"><pre className="text-block">{value}</pre></Paragraph>
    ) : (
      <Paragraph copyable strong>{value || '–'}</Paragraph>
    )}
  </div>
);

const PublishKitView = ({ kit, runId }) => {
  const intl = useIntl();
  if (!kit) {
    return <Empty description={<FormattedMessage id="kit.notReady" />} />;
  }
  const t = (id) => intl.formatMessage({ id });
  return (
    <Space direction="vertical" size="large" className="full-width">
      <Alert
        type="success"
        showIcon
        message={<FormattedMessage id="kit.ready" />}
        description={<Paragraph copyable={{ text: kit.exportDir }} className="kit-path">{kit.exportDir}</Paragraph>}
      />
      <Row gutter={16}>
        <Col xs={24} xl={14}>
          <Card size="small" title={t('kit.texts')}>
            <Copyable label={t('kit.title')} value={kit.title} />
            {kit.alternativeTitles.length > 0 && (
              <div className="kit-field">
                <Text type="secondary">{t('kit.alternatives')}</Text>
                {kit.alternativeTitles.map((title) => <Paragraph key={title} copyable>{title}</Paragraph>)}
              </div>
            )}
            <Copyable label={t('kit.description')} value={kit.description} block />
            <Copyable label={t('kit.tags')} value={kit.tagsCsv} />
            <div className="kit-field">
              <Text type="secondary">{t('kit.hashtags')}</Text>
              <div>{kit.hashtags.map((h) => <Tag key={h}>{h}</Tag>)}</div>
            </div>
            <Copyable label={t('kit.pinned')} value={kit.pinnedComment} />
            <Copyable label={t('kit.community')} value={kit.communityPost} />
          </Card>
        </Col>
        <Col xs={24} xl={10}>
          <Card size="small" title={t('kit.schedule')}>
            <Paragraph><Text strong>{kit.publishAtLocal}</Text></Paragraph>
            <Paragraph type="secondary">{`${t('kit.audienceTime')}: ${kit.publishAtAudience}`}</Paragraph>
            <Paragraph>
              <Tag>{kit.category}</Tag>
              <Tag>{kit.language}</Tag>
              <Tag color={kit.madeForKids ? 'red' : 'green'}>{kit.madeForKids ? 'Made for kids' : 'Not made for kids'}</Tag>
              <Tag color={kit.alteredContent ? 'orange' : 'default'}>{`Altered content: ${kit.alteredContent ? 'Yes' : 'No'}`}</Tag>
            </Paragraph>
            <Text type="secondary">{kit.alteredContentReason}</Text>
          </Card>
          <Card size="small" title={t('kit.thumbnails')}>
            <Row gutter={8}>
              {kit.thumbnails.map((path, i) => (
                <Col span={i === 0 ? 24 : 12} key={path}>
                  <a href={fileUrl(runId, path)} target="_blank" rel="noreferrer">
                    <img className="thumbnail" alt={`thumbnail ${i + 1}`} src={fileUrl(runId, path)} />
                  </a>
                </Col>
              ))}
            </Row>
          </Card>
        </Col>
      </Row>
      {kit.shorts.length > 0 && (
        <Card size="small" title={t('kit.shorts')}>
          <Row gutter={16}>
            {kit.shorts.map((clip) => (
              <Col xs={24} md={8} key={clip.file}>
                <video className="short-player" controls src={fileUrl(runId, clip.file)} />
                <Paragraph copyable strong>{clip.title}</Paragraph>
                <Paragraph copyable={{ text: clip.description }} type="secondary">{`${Math.round(clip.seconds)} s`}</Paragraph>
              </Col>
            ))}
          </Row>
        </Card>
      )}
      <Card size="small" title={t('kit.checklist')}>
        <List
          size="small"
          dataSource={kit.studioChecklist}
          renderItem={(step) => <List.Item><Checkbox>{step}</Checkbox></List.Item>}
        />
      </Card>
      <Title level={5}>
        <a href={fileUrl(runId, 'assemble/final_16x9.mp4')} download><FormattedMessage id="kit.downloadVideo" /></a>
      </Title>
    </Space>
  );
};

export default PublishKitView;
