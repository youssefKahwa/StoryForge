import React from 'react';
import { Tooltip } from 'antd';
import { useIntl } from 'react-intl';

const StageChips = ({ stages }) => {
  const intl = useIntl();
  return (
    <div className="stage-chips">
      {stages.map((chip) => (
        <Tooltip key={chip.stage} title={`${intl.formatMessage({ id: `stage.${chip.stage}` })}: ${chip.status}`}>
          <span className={`stage-chip stage-${chip.status.toLowerCase()}`} />
        </Tooltip>
      ))}
    </div>
  );
};

export default StageChips;
