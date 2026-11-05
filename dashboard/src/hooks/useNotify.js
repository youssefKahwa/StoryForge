import { App } from 'antd';

/** Toast messages that follow the light/dark theme (antd 5's static `message` does not). */
const useNotify = () => App.useApp().message;

export default useNotify;
