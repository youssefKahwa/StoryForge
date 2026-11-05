import React, {
  createContext, useCallback, useContext, useEffect, useMemo, useState,
} from 'react';

const KEY = 'storyforge.theme';
const ThemeModeContext = createContext({ mode: 'dark', toggle: () => {} });

const save = (mode) => {
  try {
    window.localStorage.setItem(KEY, mode);
  } catch (e) {
    // not saved: the choice still works for this visit
  }
};

/** Dark unless the owner switched to light (remembered in this browser); "?theme=light" also switches. */
const stored = () => {
  try {
    const param = new URLSearchParams(window.location.search).get('theme');
    if (param === 'dark' || param === 'light') {
      save(param);
      return param;
    }
    const value = window.localStorage.getItem(KEY);
    if (value === 'dark' || value === 'light') return value;
  } catch (e) {
    // storage blocked: dark
  }
  return 'dark';
};

export const ThemeModeProvider = ({ children }) => {
  const [mode, setMode] = useState('dark');

  useEffect(() => {
    setMode(stored());
  }, []);

  useEffect(() => {
    document.documentElement.dataset.theme = mode;
  }, [mode]);

  const toggle = useCallback(() => {
    setMode((current) => {
      const next = current === 'dark' ? 'light' : 'dark';
      save(next);
      return next;
    });
  }, []);

  const value = useMemo(() => ({ mode, toggle }), [mode, toggle]);
  return <ThemeModeContext.Provider value={value}>{children}</ThemeModeContext.Provider>;
};

export const useThemeMode = () => useContext(ThemeModeContext);

/** Runs before React, so a light-mode owner never sees a dark flash on reload. */
export const THEME_BOOT_SCRIPT = `try{var q=new URLSearchParams(location.search).get('theme');`
  + `var t=(q==='light'||q==='dark')?q:localStorage.getItem('${KEY}');`
  + "document.documentElement.dataset.theme=t==='light'?'light':'dark'}"
  + "catch(e){document.documentElement.dataset.theme='dark'}";
