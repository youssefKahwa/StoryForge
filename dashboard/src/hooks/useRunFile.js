import { useEffect, useState } from 'react';
import ApiRoutes from '../api/ApiRoutes';
import http from '../api/http';

const useRunFile = (detail, stage, name) => {
  const [content, setContent] = useState(null);
  const file = detail && detail.files.find((f) => f.stage === stage && f.name === name);
  const version = file ? `${file.stage}/${file.name}/${file.sizeBytes}` : null;
  const runId = detail && detail.run.id;

  useEffect(() => {
    if (!version) {
      setContent(null);
      return undefined;
    }
    let cancelled = false;
    http
      .get(ApiRoutes.runFile(runId, stage, name))
      .then((data) => !cancelled && setContent(data))
      .catch(() => !cancelled && setContent(null));
    return () => {
      cancelled = true;
    };
  }, [runId, stage, name, version]);

  return content;
};

export default useRunFile;
