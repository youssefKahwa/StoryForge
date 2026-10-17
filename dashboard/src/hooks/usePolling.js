import { useEffect, useRef } from 'react';

const usePolling = (callback, intervalMs, active = true) => {
  const saved = useRef(callback);

  useEffect(() => {
    saved.current = callback;
  }, [callback]);

  useEffect(() => {
    saved.current();
    if (!active) {
      return undefined;
    }
    const timer = setInterval(() => saved.current(), intervalMs);
    return () => clearInterval(timer);
  }, [intervalMs, active]);
};

export default usePolling;
