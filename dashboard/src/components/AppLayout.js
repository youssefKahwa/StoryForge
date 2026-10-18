import React, { useCallback } from 'react';
import { Link } from 'gatsby';
import { Badge, Layout, Menu, Typography } from 'antd';
import {
  BulbOutlined, PlayCircleOutlined, PlusCircleOutlined, SettingOutlined,
} from '@ant-design/icons';
import { FormattedMessage } from 'react-intl';
import { useDispatch, useSelector } from 'react-redux';
import usePolling from '../hooks/usePolling';
import { fetchSettings, selectSettings } from '../store/settings/settingsSlice';

const { Header, Sider, Content } = Layout;

const NAV = [
  { key: '/new', icon: <PlusCircleOutlined />, label: 'nav.new' },
  { key: '/', icon: <PlayCircleOutlined />, label: 'nav.runs' },
  { key: '/topics', icon: <BulbOutlined />, label: 'nav.topics' },
  { key: '/settings', icon: <SettingOutlined />, label: 'nav.settings' },
];

const selectedKey = (pathname) => {
  if (pathname.startsWith('/new')) return '/new';
  if (pathname.startsWith('/topics')) return '/topics';
  if (pathname.startsWith('/settings')) return '/settings';
  return '/';
};

const AppLayout = ({ location, children }) => {
  const dispatch = useDispatch();
  const settings = useSelector(selectSettings);
  const refresh = useCallback(() => dispatch(fetchSettings()), [dispatch]);
  usePolling(refresh, 10000);

  const pathname = (location && location.pathname) || '/';
  const items = NAV.map((item) => ({
    key: item.key,
    icon: item.icon,
    label: <Link to={item.key}><FormattedMessage id={item.label} /></Link>,
  }));

  return (
    <Layout className="app-layout">
      <Sider breakpoint="lg" collapsedWidth="0" theme="dark">
        <div className="brand">
          <FormattedMessage id="app.title" />
        </div>
        <Menu theme="dark" mode="inline" selectedKeys={[selectedKey(pathname)]} items={items} />
      </Sider>
      <Layout>
        <Header className="app-header">
          {settings && (
            <Typography.Text>
              <Badge status={settings.paused ? 'error' : 'success'} />
              <FormattedMessage id={settings.paused ? 'killSwitch.paused' : 'killSwitch.running'} />
              <span className="profile-chip">{settings.profile || 'default'}</span>
            </Typography.Text>
          )}
        </Header>
        <Content className="app-content">{children}</Content>
      </Layout>
    </Layout>
  );
};

export default AppLayout;
