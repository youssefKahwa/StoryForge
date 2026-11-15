import React from 'react';
import { Card, Col, Empty, List, Progress, Row, Space, Tag, Typography } from 'antd';
import { CheckCircleFilled, CloseCircleFilled, LinkOutlined } from '@ant-design/icons';
import { FormattedMessage, useIntl } from 'react-intl';

const { Paragraph, Text } = Typography;

const views = (n) => (n >= 1e6 ? `${(n / 1e6).toFixed(1)}M` : n >= 1e3 ? `${Math.round(n / 1e3)}K` : `${n}`);

/** The Growth tab: titles ranked with reasons, what people search, the competition, tags and a checklist. */
const GrowthView = ({ growth }) => {
  const intl = useIntl();
  const t = (id) => intl.formatMessage({ id });
  if (!growth) {
    return <Empty description={<FormattedMessage id="growth.notReady" />} />;
  }
  const passed = growth.checklist.filter((c) => c.ok).length;
  return (
    <Space direction="vertical" size="large" className="full-width">
      <Typography.Text type="secondary"><FormattedMessage id="growth.help" values={{ name: growth.name }} /></Typography.Text>
      <Row gutter={[16, 16]}>
        <Col xs={24} xl={14}>
          <Card size="small" title={t('growth.titles')}>
            <List
              dataSource={growth.titles}
              renderItem={(title, i) => (
                <List.Item>
                  <Space direction="vertical" size={2} className="full-width">
                    <Space wrap>
                      <Tag color={i === 0 ? 'green' : 'default'}>{title.score}</Tag>
                      <Paragraph copyable style={{ margin: 0 }} strong={i === 0}>{title.title}</Paragraph>
                    </Space>
                    <Text type="secondary">{title.reasons.join(' · ')}</Text>
                  </Space>
                </List.Item>
              )}
            />
          </Card>
        </Col>
        <Col xs={24} xl={10}>
          <Card size="small" title={t('growth.checklist')} extra={`${passed} / ${growth.checklist.length}`}>
            <Progress percent={Math.round((passed / Math.max(1, growth.checklist.length)) * 100)} showInfo={false} />
            <List
              size="small"
              dataSource={growth.checklist}
              renderItem={(check) => (
                <List.Item>
                  <Space align="start">
                    {check.ok
                      ? <CheckCircleFilled style={{ color: '#52c41a' }} />
                      : <CloseCircleFilled style={{ color: '#ff4d4f' }} />}
                    <span>
                      <Text strong>{check.item}</Text>
                      <br />
                      <Text type="secondary">{check.detail}</Text>
                    </span>
                  </Space>
                </List.Item>
              )}
            />
          </Card>
        </Col>
        <Col xs={24} xl={12}>
          <Card size="small" title={t('growth.searches')}>
            <Paragraph type="secondary">{t('growth.searchesHelp')}</Paragraph>
            <Space wrap>
              {growth.searches.map((s) => (
                <Tag key={s.phrase} color={s.used ? 'green' : 'default'}>{s.phrase}</Tag>
              ))}
            </Space>
          </Card>
        </Col>
        <Col xs={24} xl={12}>
          <Card size="small" title={t('growth.tags')}>
            <Paragraph copyable={{ text: growth.tags.join(', ') }}>
              {growth.tags.map((tag) => <Tag key={tag}>{tag}</Tag>)}
            </Paragraph>
            <Text type="secondary">{t('growth.descriptionTop')}</Text>
            <Paragraph copyable className="text-block">{growth.descriptionTop}</Paragraph>
          </Card>
        </Col>
        <Col xs={24}>
          <Card size="small" title={t('growth.competitors')}>
            <List
              size="small"
              dataSource={growth.competitors}
              renderItem={(video) => (
                <List.Item extra={<Tag>{`${views(video.views)} ${t('growth.views')}`}</Tag>}>
                  <a href={video.url} target="_blank" rel="noreferrer"><LinkOutlined /> {video.title}</a>
                  <Text type="secondary">{` · ${video.channel || ''} · ${Math.round(video.durationSec / 60)} min`}</Text>
                </List.Item>
              )}
            />
          </Card>
        </Col>
      </Row>
    </Space>
  );
};

export default GrowthView;
