import React from 'react';
import { Link } from 'gatsby';
import { Result } from 'antd';
import AppLayout from '../components/AppLayout';

const NotFoundPage = ({ location }) => (
  <AppLayout location={location}>
    <Result status="404" title="404" subTitle="This page does not exist." extra={<Link to="/">Back to the studio</Link>} />
  </AppLayout>
);

export const Head = () => <title>Not found · StoryForge</title>;

export default NotFoundPage;
