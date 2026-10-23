import React from 'react';
import { createRoot } from 'react-dom/client';
import { ConfigProvider } from 'antd';
import koKR from 'antd/locale/ko_KR';
import App from './App.jsx';
import './styles.css';

createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <ConfigProvider locale={koKR} theme={{ token: { colorPrimary: '#5946c6', borderRadius: 8, fontSize: 16, controlHeight: 44 } }}>
      <App />
    </ConfigProvider>
  </React.StrictMode>,
);
