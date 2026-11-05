import React, { useEffect } from 'react';
import {
  Alert, Button, Collapse, Descriptions, Space, Spin, Switch, Tabs, Tag, Typography,
} from 'antd';
import {
  ApiOutlined, SoundOutlined, YoutubeOutlined,
} from '@ant-design/icons';
import { FormattedMessage, useIntl } from 'react-intl';
import { useDispatch, useSelector } from 'react-redux';
import ApiRoutes from '../api/ApiRoutes';
import AppLayout from '../components/AppLayout';
import PageHeader from '../components/PageHeader';
import PreferencesCard from '../components/PreferencesCard';
import StorageCard from '../components/StorageCard';
import SystemCheckCard from '../components/SystemCheckCard';
import useNotify from '../hooks/useNotify';
import {
  disconnectYouTube, fetchYouTube, selectSettings, selectYouTube, setKillSwitch, testNotification,
} from '../store/settings/settingsSlice';

const SettingsPage = ({ location }) => {
  const intl = useIntl();
  const dispatch = useDispatch();
  const notify = useNotify();
  const settings = useSelector(selectSettings);
  const youtube = useSelector(selectYouTube);
  const result = new URLSearchParams(location.search).get('youtube');

  useEffect(() => {
    dispatch(fetchYouTube());
  }, [dispatch]);

  const toggle = (paused) => dispatch(setKillSwitch({
    paused,
    onFail: (error) => notify.error(intl.formatMessage({ id: 'error.request' }, { message: error.message })),
  }));

  const publishing = (
    <div className="sf-panel">
      <div className="sf-panel-title"><FormattedMessage id="youtube.title" /></div>
      {!youtube ? <Spin /> : (
        <Space direction="vertical" size="middle">
          {!youtube.configured && <Alert type="info" showIcon message={<FormattedMessage id="youtube.notConfigured" />} />}
          <Space wrap>
            <FormattedMessage id="youtube.status" />
            {youtube.connected ? <Tag color="green">{youtube.channel || 'connected'}</Tag> : <Tag>not connected</Tag>}
            <Tag>{`publish: ${youtube.publishMode}`}</Tag>
          </Space>
          <Space wrap>
            <Button type="primary" icon={<YoutubeOutlined />} disabled={!youtube.configured} href={ApiRoutes.youtubeConnect()}>
              <FormattedMessage id={youtube.connected ? 'youtube.reconnect' : 'youtube.connect'} />
            </Button>
            {youtube.connected && (
              <Button onClick={() => dispatch(disconnectYouTube({}))}><FormattedMessage id="youtube.disconnect" /></Button>
            )}
            <Button disabled={!youtube.telegram} onClick={() => dispatch(testNotification({ onSuccess: () => notify.success('sent') }))}>
              <FormattedMessage id="youtube.testNotification" />
            </Button>
          </Space>
          <Typography.Text type="secondary">{`Redirect URI: ${youtube.redirectUri} · Telegram: ${youtube.telegram ? 'on' : 'off'} · Autopilot: ${youtube.autopilot ? 'on' : 'off'}`}</Typography.Text>
        </Space>
      )}
    </div>
  );

  const system = settings && (
    <Space direction="vertical" size="large" className="full-width">
      <div className="sf-panel">
        <div className="sf-panel-title"><FormattedMessage id="settings.killSwitch" /></div>
        <Space>
          <Switch checked={settings.paused} onChange={toggle} checkedChildren="PAUSED" unCheckedChildren="ACTIVE" />
          <Typography.Text type="secondary"><FormattedMessage id="settings.killSwitchHelp" /></Typography.Text>
        </Space>
      </div>
      <SystemCheckCard />
      <StorageCard />
      <Collapse
        items={[{
          key: 'engine',
          label: <strong><FormattedMessage id="settings.engine" /></strong>,
          children: (
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
          ),
        }]}
      />
    </Space>
  );

  return (
    <AppLayout location={location} title={intl.formatMessage({ id: 'nav.settings' })}>
      <PageHeader title={intl.formatMessage({ id: 'settings.title' })} subtitle={intl.formatMessage({ id: 'settings.subtitle' })} />
      {result && (
        <Alert style={{ marginBottom: 16 }} type={result === 'connected' ? 'success' : 'warning'} showIcon
          message={intl.formatMessage({ id: `youtube.result.${result}` })} />
      )}
      {!settings ? <Spin /> : (
        <Tabs
          defaultActiveKey={result ? 'publishing' : 'channel'}
          items={[
            { key: 'channel', icon: <SoundOutlined />, label: intl.formatMessage({ id: 'settings.tab.channel' }), children: <PreferencesCard /> },
            { key: 'publishing', icon: <YoutubeOutlined />, label: intl.formatMessage({ id: 'settings.tab.publishing' }), children: publishing },
            { key: 'system', icon: <ApiOutlined />, label: intl.formatMessage({ id: 'settings.tab.system' }), children: system },
          ]}
        />
      )}
    </AppLayout>
  );
};

export const Head = () => <title>Settings · StoryForge</title>;

export default SettingsPage;
