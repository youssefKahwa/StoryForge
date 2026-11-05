import React from 'react';
import { renderToString } from 'react-dom/server';
import { createCache, extractStyle, StyleProvider } from '@ant-design/cssinjs';
import { wrapRoot } from './src/state/wrapRoot';
import { THEME_BOOT_SCRIPT } from './src/theme/ThemeContext';

export const wrapRootElement = wrapRoot;

// Ant Design 5 writes its styles at runtime; collect them while the page is built so it never shows unstyled.
export const replaceRenderer = ({ bodyComponent, replaceBodyHTMLString, setHeadComponents }) => {
  const cache = createCache();
  const html = renderToString(<StyleProvider cache={cache}>{bodyComponent}</StyleProvider>);
  replaceBodyHTMLString(html);
  setHeadComponents([
    <style key="antd-ssr" id="antd-ssr" dangerouslySetInnerHTML={{ __html: extractStyle(cache, true) }} />,
  ]);
};

export const onRenderBody = ({ setHtmlAttributes, setHeadComponents, setPreBodyComponents }) => {
  setHtmlAttributes({ lang: 'en', 'data-theme': 'dark' });
  setHeadComponents([
    <link key="fonts-pre" rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />,
    <link
      key="fonts"
      rel="stylesheet"
      href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap"
    />,
  ]);
  setPreBodyComponents([
    // eslint-disable-next-line react/no-danger
    <script key="theme-boot" dangerouslySetInnerHTML={{ __html: THEME_BOOT_SCRIPT }} />,
  ]);
};
