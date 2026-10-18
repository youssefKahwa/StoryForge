import React, { useEffect } from 'react';
import {
  Alert, Button, Card, Descriptions, Space, Spin, Switch, Tag, Typography, message,
} from 'antd';
import ApiRoutes from '../api/ApiRoutes';
import { FormattedMessage, useIntl } from 'react-intl';
import { useDispatch, useSelector } from 'react-redux';
import AppLayout from '../components/AppLayout';
import PreferencesCard from '../components/PreferencesCard';
import StorageCard from '../components/StorageCard';
import SystemCheckCard from '../components/SystemCheckCard';
import {
  disconnectYouTube, fetchYouTube, selectSettings, selectYouTube, setKillSwitch, testNotification,
} from '../store/settings/settingsSlice';

const SettingsPage = ({ location }) => {
  const intl = useIntl();
  const dispatch = useDispatch();
  const settings = useSelector(selectSettings);
  const youtube = useSelector(selectYouTube);
  const result = new URLSearchParams(location.search).get('youtube');

  useEffect(() => {
    dispatch(fetchYouTube());
  }, [dispatch]);

  const toggle = (paused) => dispatch(setKillSwitch({
    paused,
    onFail: (error) => message.error(intl.formatMessage({ id: 'error.request' }, { message: error.message })),
  }));

  return (
    <AppLayout location={location}>
      <Typography.Title level={3}><FormattedMessage id="settings.title" /></Typography.Title>
      {!settings ? <Spin /> : (
        <Space direction="vertical" size="large" className="full-width">
          {result && (
            <Alert type={result === 'connected' ? 'success' : 'warning'} showIcon
              message={intl.formatMessage({ id: `youtube.result.${result}` })} />
          )}
          <SystemCheckCard />
          <PreferencesCard />
          <Card size="small" title={<FormattedMessage id="youtube.title" />}>
            {!youtube ? <Spin /> : (
              <Space direction="vertical">
                {!youtube.configured && <Alert type="info" showIcon message={<FormattedMessage id="youtube.notConfigured" />} />}
                <Typography.Text>
                  <FormattedMessage id="youtube.status" />
                  {': '}
                  {youtube.connected ? <Tag color="green">{youtube.channel || 'connected'}</Tag> : <Tag>not connected</Tag>}
                  <Tag>{`publish: ${youtube.publishMode}`}</Tag>
                </Typography.Text>
                <Space>
                  <Button type="primary" disabled={!youtube.configured} href={ApiRoutes.youtubeConnect()}>
                    <FormattedMessage id={youtube.connected ? 'youtube.reconnect' : 'youtube.connect'} />
                  </Button>
                  {youtube.connected && (
                    <Button onClick={() => dispatch(disconnectYouTube({}))}><FormattedMessage id="youtube.disconnect" /></Button>
                  )}
                  <Button disabled={!youtube.telegram} onClick={() => dispatch(testNotification({ onSuccess: () => message.success('sent') }))}>
                    <FormattedMessage id="youtube.testNotification" />
                  </Button>
                </Space>
                <Typography.Text type="secondary">{`Redirect URI: ${youtube.redirectUri} · Telegram: ${youtube.telegram ? 'on' : 'off'} · Autopilot: ${youtube.autopilot ? 'on' : 'off'}`}</Typography.Text>
              </Space>
            )}
          </Card>
          <StorageCard />
          <Card size="small" title={<FormattedMessage id="settings.killSwitch" />}>
            <Space>
              <Switch checked={settings.paused} onChange={toggle} checkedChildren="PAUSED" unCheckedChildren="ACTIVE" />
              <Typography.Text type="secondary"><FormattedMessage id="settings.killSwitchHelp" /></Typography.Text>
            </Space>
          </Card>
          <Card size="small">
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label={intl.formatMessage({ id: 'settings.profile' })}>{settings.profile || 'default'}</Descriptions.Item>
              <Descriptions.Item label={intl.formatMessage({ id: 'settings.workerBusy' })}>
                <FormattedMessage id={settings.workerBusy ? 'common.yes' : 'common.no'} />
              </Descriptions.Item>
              <Descriptions.Item label={intl.formatMessage({ id: 'settings.budget' })}>{`$${settings.budgetUsdPerVideo}`}</Descriptions.Item>
              <Descriptions.Item label={intl.formatMessage({ id: 'settings.providers' })}>
                {Object.entries(settings.providers).map(([kind, name]) => <Tag key={kind}>{`${kind}: ${name}`}</Tag>)}
              </Descriptions.Item>
              <Descriptions.Item label={intl.formatMessage({ id: 'settings.llmOnly' })}>{settings.llmOnly || '–'}</Descriptions.Item>
              <Descriptions.Item label={intl.formatMessage({ id: 'settings.llm' })}>
                <Space direction="vertical" size={2}>
                  <span>{`default: ${settings.llmDefaultRoute.join(' → ')}`}</span>
                  {Object.entries(settings.llmRoutes).map(([stage, route]) => (
                    <span key={stage}>{`${stage}: ${route.join(' → ')}`}</span>
                  ))}
                </Space>
              </Descriptions.Item>
            </Descriptions>
          </Card>
        </Space>
      )}
    </AppLayout>
  );
};

export const Head = () => <title>Settings · StoryForge</title>;

export default SettingsPage;
