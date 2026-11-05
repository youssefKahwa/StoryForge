import React, { useState } from 'react';
import { Link } from 'gatsby';
import ApiRoutes from '../api/ApiRoutes';
import StatusBadge from './StatusBadge';
import { useRunProgress } from './RunProgress';
import { duration, minutes, relativeTime } from '../utils/time';

const ACTIVE = ['QUEUED', 'RUNNING'];
const DONE = ['SUCCEEDED', 'CACHED'];

const hasStage = (run, stage) => (run.stages || []).some((s) => String(s.stage).toLowerCase() === stage && DONE.includes(s.status));

/** One video in the studio grid: thumbnail (or a gradient with the title), status, length, when. */
const VideoCard = ({ run }) => {
  const active = ACTIVE.includes(run.status);
  const progress = useRunProgress(run.id, active);
  const [thumbOk, setThumbOk] = useState(true);
  const showThumb = thumbOk && hasStage(run, 'package');
  const note = !active && run.status !== 'COMPLETED' ? run.statusReason : null;

  return (
    <Link to={`/run?id=${run.id}`} className="sf-video-card">
      <div className="sf-thumb">
        {showThumb ? (
          <img
            alt=""
            loading="lazy"
            src={ApiRoutes.runFile(run.id, 'package', 'thumbnail.png')}
            onError={() => setThumbOk(false)}
          />
        ) : (
          <div className="sf-thumb-placeholder"><span>{run.topicTitle}</span></div>
        )}
        <span className="sf-thumb-badge"><StatusBadge status={run.status} /></span>
        {run.targetSeconds > 0 && <span className="sf-thumb-length">{minutes(run.targetSeconds)}</span>}
        {active && progress && (
          <div className="sf-thumb-progress"><span style={{ width: `${progress.percent}%` }} /></div>
        )}
      </div>
      <div className="sf-video-body">
        <div className="sf-video-title">{run.topicTitle}</div>
        <div className="sf-video-meta">
          {active && progress
            ? `${progress.stageLabel}${progress.remainingSeconds != null ? ` · ~${duration(progress.remainingSeconds)} left` : ''}`
            : `#${run.id} · ${relativeTime(run.finishedAt || run.createdAt)}`}
        </div>
        {note && <div className="sf-video-note">{note}</div>}
      </div>
    </Link>
  );
};

export default VideoCard;
