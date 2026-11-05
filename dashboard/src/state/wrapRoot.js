import React from 'react';
import { Provider } from 'react-redux';
import { IntlProvider } from 'react-intl';
import { App as AntApp, ConfigProvider } from 'antd';
import enUS from 'antd/locale/en_US';
import { createAppStore } from '../store';
import { DEFAULT_LOCALE, messagesFor } from '../i18n';
import { ThemeModeProvider, useThemeMode } from '../theme/ThemeContext';
import { themeFor } from '../theme/tokens';

const Themed = ({ children }) => {
  const { mode } = useThemeMode();
  return (
    <ConfigProvider locale={enUS} theme={themeFor(mode)}>
      <AntApp>{children}</AntApp>
    </ConfigProvider>
  );
};

export const wrapRoot = ({ element }) => {
  const store = createAppStore();
  return (
    <Provider store={store}>
      <IntlProvider locale={DEFAULT_LOCALE} messages={messagesFor(DEFAULT_LOCALE)}>
        <ThemeModeProvider>
          <Themed>{element}</Themed>
        </ThemeModeProvider>
      </IntlProvider>
    </Provider>
  );
};
