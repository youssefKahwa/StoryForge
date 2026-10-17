import { wrapRoot } from './src/state/wrapRoot';

export const wrapRootElement = wrapRoot;

export const onRenderBody = ({ setHtmlAttributes }) => {
  setHtmlAttributes({ lang: 'en' });
};
