/* Licensed under the Apache License, Version 2.0. See LICENSE. */
import '@fontsource-variable/inter';
import 'antd/dist/antd.css';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import App from './App';
import en from './locale/en.json';
import zh from './locale/zh-CN.json';
import upstreamEn from './upstream/locale/languages/en-us.json';
import upstreamZh from './upstream/locale/languages/zh-cn.json';
import './styles.css';

void i18n
  .use(initReactI18next)
  .init({
    lng: 'zh-CN',
    fallbackLng: 'en',
    defaultNS: 'translation',
    interpolation: { escapeValue: false },
    resources: {
      en: { translation: upstreamEn, standalone: en },
      'zh-CN': { translation: upstreamZh, standalone: zh },
    },
  })
  .then(() => {
    const container = document.getElementById('root');
    if (container) {
      createRoot(container).render(
        <StrictMode>
          <App />
        </StrictMode>
      );
    }
  });
