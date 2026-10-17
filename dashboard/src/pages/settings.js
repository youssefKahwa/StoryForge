import React from 'react';
import {
  Card, Descriptions, Space, Spin, Switch, Tag, Typography, message,
} from 'antd';
import { FormattedMessage, useIntl } from 'react-intl';
import { useDispatch, useSelector } from 'react-redux';
import AppLayout from '../components/AppLayout';
import { selectSettings, setKillSwitch } from '../store/settings/settingsSlice';

const SettingsPage = ({ location }) => {
  const intl = useIntl();
  const dispatch = useDispatch();
  const settings = useSelector(selectSettings);

  const toggle = (paused) => dispatch(setKillSwitch({
    paused,
    onFail: (error) => message.error(intl.formatMessage({ id: 'error.request' }, { message: error.message })),
  }));

  return (
    <AppLayout location={location}>
      <Typography.Title level={3}><FormattedMessage id="settings.title" /></Typography.Title>
      {!settings ? <Spin /> : (
        <Space direction="vertical" size="large" className="full-width">
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
