import React from 'react';
import { Provider } from 'react-redux';
import { IntlProvider } from 'react-intl';
import { ConfigProvider } from 'antd';
import enUS from 'antd/lib/locale/en_US';
import { createAppStore } from '../store';
import { DEFAULT_LOCALE, messagesFor } from '../i18n';

export const wrapRoot = ({ element }) => {
  const store = createAppStore();
  return (
    <Provider store={store}>
      <IntlProvider locale={DEFAULT_LOCALE} messages={messagesFor(DEFAULT_LOCALE)}>
        <ConfigProvider locale={enUS}>{element}</ConfigProvider>
      </IntlProvider>
    </Provider>
  );
};
