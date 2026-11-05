import React, { useCallback, useState } from 'react';
import { Link, navigate } from 'gatsby';
import {
  App, Button, Drawer, Grid, Layout, Menu, Tooltip,
} from 'antd';
import {
  AppstoreOutlined, BulbOutlined, MenuOutlined, MoonOutlined, PlusOutlined, SettingOutlined, SunOutlined,
  VideoCameraAddOutlined,
} from '@ant-design/icons';
import { FormattedMessage, useIntl } from 'react-intl';
import { useDispatch, useSelector } from 'react-redux';
import usePolling from '../hooks/usePolling';
import useNotify from '../hooks/useNotify';
import { fetchSettings, selectSettings, setKillSwitch } from '../store/settings/settingsSlice';
import { useThemeMode } from '../theme/ThemeContext';

const { Sider, Content } = Layout;

const NAV = [
  { key: '/', icon: <AppstoreOutlined />, label: 'nav.studio' },
  { key: '/new', icon: <VideoCameraAddOutlined />, label: 'nav.new' },
  { key: '/topics', icon: <BulbOutlined />, label: 'nav.topics' },
  { key: '/settings', icon: <SettingOutlined />, label: 'nav.settings' },
];

const selectedKey = (pathname) => {
  if (pathname.startsWith('/new')) return '/new';
  if (pathname.startsWith('/topics')) return '/topics';
  if (pathname.startsWith('/settings')) return '/settings';
  return '/';
};

const Brand = () => (
  <Link to="/" className="sf-brand">
    <img src="/logo.png" alt="" />
    <div>
      <div className="sf-brand-name">StoryForge</div>
      <div className="sf-brand-sub">Video studio</div>
    </div>
  </Link>
);

const AppLayout = ({ location, title, children }) => {
  const intl = useIntl();
  const dispatch = useDispatch();
  const { modal } = App.useApp();
  const notify = useNotify();
  const settings = useSelector(selectSettings);
  const { mode, toggle } = useThemeMode();
  const screens = Grid.useBreakpoint();
  const mobile = screens.lg === false; // unknown while the page is built: desktop
  const [drawer, setDrawer] = useState(false);
  const refresh = useCallback(() => dispatch(fetchSettings()), [dispatch]);
  usePolling(refresh, 10000);

  const pathname = (location && location.pathname) || '/';
  const items = NAV.map((item) => ({
    key: item.key,
    icon: item.icon,
    label: <FormattedMessage id={item.label} />,
  }));
  const go = ({ key }) => {
    setDrawer(false);
    navigate(key);
  };
  const nav = (
    <Menu className="sf-nav" mode="inline" selectedKeys={[selectedKey(pathname)]} items={items} onClick={go} />
  );

  const paused = settings && settings.paused;
  const toggleKillSwitch = () => modal.confirm({
    title: intl.formatMessage({ id: paused ? 'killSwitch.resumeTitle' : 'killSwitch.pauseTitle' }),
    content: intl.formatMessage({ id: paused ? 'killSwitch.resumeText' : 'killSwitch.pauseText' }),
    okText: intl.formatMessage({ id: paused ? 'killSwitch.resume' : 'killSwitch.pause' }),
    onOk: () => dispatch(setKillSwitch({
      paused: !paused,
      onFail: (error) => notify.error(intl.formatMessage({ id: 'error.request' }, { message: error.message })),
    })),
  });

  return (
    <Layout className="sf-shell" hasSider={!mobile}>
      {!mobile && (
        <Sider width={236} className="sf-sider">
          <Brand />
          {nav}
          <div className="sf-sider-foot"><FormattedMessage id="app.footer" /></div>
        </Sider>
      )}
      {mobile && (
        <Drawer placement="left" open={drawer} onClose={() => setDrawer(false)} width={260} styles={{ body: { padding: 0 } }}>
          <Brand />
          {nav}
        </Drawer>
      )}
      <Layout className="sf-main">
        <div className="sf-topbar">
          {mobile && <Button type="text" icon={<MenuOutlined />} onClick={() => setDrawer(true)} aria-label="menu" />}
          <div className="sf-topbar-title">{title}</div>
          {settings && (
            <Tooltip title={intl.formatMessage({ id: paused ? 'killSwitch.pausedHelp' : 'killSwitch.runningHelp' })}>
              <button type="button" className="sf-status-pill" onClick={toggleKillSwitch}>
                <span className={`sf-dot${paused ? ' paused' : ''}`} />
                <span className="sf-hide-mobile">
                  <FormattedMessage id={paused ? 'killSwitch.paused' : 'killSwitch.running'} />
                </span>
              </button>
            </Tooltip>
          )}
          <Tooltip title={intl.formatMessage({ id: mode === 'dark' ? 'theme.light' : 'theme.dark' })}>
            <Button shape="circle" icon={mode === 'dark' ? <SunOutlined /> : <MoonOutlined />} onClick={toggle} />
          </Tooltip>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/new')}>
            <span className="sf-hide-mobile"><FormattedMessage id="nav.new" /></span>
          </Button>
        </div>
        <Content className="sf-content">{children}</Content>
      </Layout>
    </Layout>
  );
};

export default AppLayout;
