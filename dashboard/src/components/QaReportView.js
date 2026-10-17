import React from 'react';
import { Alert, Empty, Table, Tag } from 'antd';
import { FormattedMessage, useIntl } from 'react-intl';

const SEVERITY_COLORS = { CRITICAL: 'red', WARNING: 'orange', INFO: 'blue' };

const QaReportView = ({ report }) => {
  const intl = useIntl();
  if (!report) {
    return <Empty description={<FormattedMessage id="run.noQa" />} />;
  }
  const columns = [
    {
      title: intl.formatMessage({ id: 'qa.col.result' }),
      dataIndex: 'passed',
      width: 90,
      render: (passed) => <Tag color={passed ? 'green' : 'red'}>{passed ? 'PASS' : 'FAIL'}</Tag>,
    },
    {
      title: intl.formatMessage({ id: 'qa.col.severity' }),
      dataIndex: 'severity',
      width: 110,
      render: (severity) => <Tag color={SEVERITY_COLORS[severity]}>{severity}</Tag>,
    },
    { title: intl.formatMessage({ id: 'qa.col.check' }), dataIndex: 'id', width: 230 },
    { title: intl.formatMessage({ id: 'qa.col.detail' }), dataIndex: 'detail' },
  ];
  return (
    <>
      <Alert
        className="qa-verdict"
        type={report.passed ? 'success' : 'error'}
        showIcon
        message={(
          <span>
            <FormattedMessage id={report.passed ? 'qa.verdictPassed' : 'qa.verdictBlocked'} />
            {` · ${report.criticalFailures} critical · ${report.warnings} warnings · `}
            <FormattedMessage id="qa.disclosure" />
            {`: ${report.disclosureRequired ? 'yes' : 'no'}`}
          </span>
        )}
      />
      <Table rowKey="id" size="small" pagination={false} columns={columns} dataSource={report.checks} />
    </>
  );
};

export default QaReportView;
