import { theme } from 'antd';

const FONT = "'Inter', system-ui, -apple-system, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif";

/** Dark "studio": deep night background, the logo's sky blue as the accent. */
const dark = {
  algorithm: theme.darkAlgorithm,
  token: {
    colorPrimary: '#4cc2ff',
    colorInfo: '#4cc2ff',
    colorSuccess: '#3ddc97',
    colorWarning: '#ffb547',
    colorError: '#ff5c7a',
    colorBgBase: '#0b0f17',
    colorBgLayout: '#0b0f17',
    colorBgContainer: '#121826',
    colorBgElevated: '#182133',
    colorBorder: '#253047',
    colorBorderSecondary: '#1d2638',
    colorText: '#e6edf6',
    colorTextSecondary: '#93a1b6',
    borderRadius: 12,
    borderRadiusLG: 16,
    fontFamily: FONT,
    controlHeight: 38,
    wireframe: false,
  },
  components: {
    Button: { primaryColor: '#05121f', fontWeight: 600, primaryShadow: '0 6px 20px rgba(76, 194, 255, 0.25)' },
    Card: { headerBg: 'transparent' },
    Layout: { bodyBg: '#0b0f17', headerBg: 'transparent', siderBg: '#0e131e' },
    Menu: {
      itemBg: 'transparent', itemSelectedBg: 'rgba(76, 194, 255, 0.14)', itemSelectedColor: '#4cc2ff',
      itemHoverBg: 'rgba(255, 255, 255, 0.05)', itemBorderRadius: 10, itemHeight: 44,
    },
    Tabs: { itemSelectedColor: '#4cc2ff', inkBarColor: '#4cc2ff' },
    Table: { headerBg: '#151c2b', rowHoverBg: '#161e2e' },
    Segmented: { itemSelectedBg: '#253047' },
  },
};

/** Light: the same accents on a soft grey-blue page with white cards. */
const light = {
  algorithm: theme.defaultAlgorithm,
  token: {
    colorPrimary: '#0b8fd4',
    colorInfo: '#0b8fd4',
    colorSuccess: '#16a36a',
    colorWarning: '#e08a00',
    colorError: '#e5395d',
    colorBgLayout: '#f4f7fb',
    colorBgContainer: '#ffffff',
    colorBorderSecondary: '#e8edf3',
    colorText: '#111827',
    colorTextSecondary: '#5b6678',
    borderRadius: 12,
    borderRadiusLG: 16,
    fontFamily: FONT,
    controlHeight: 38,
    wireframe: false,
  },
  components: {
    Button: { fontWeight: 600, primaryShadow: '0 6px 18px rgba(11, 143, 212, 0.22)' },
    Card: { headerBg: 'transparent' },
    Layout: { bodyBg: '#f4f7fb', headerBg: 'transparent', siderBg: '#ffffff' },
    Menu: {
      itemBg: 'transparent', itemSelectedBg: 'rgba(11, 143, 212, 0.10)', itemSelectedColor: '#0b8fd4',
      itemBorderRadius: 10, itemHeight: 44,
    },
    Table: { headerBg: '#f7f9fc' },
  },
};

export const themeFor = (mode) => (mode === 'light' ? light : dark);
