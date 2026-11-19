/* Licensed under the Apache License, Version 2.0. See LICENSE. */
import { Button, Input } from '@openmetadata/ui-core-components';
import {
  BookOpen01,
  File02,
  Home01,
  Lightbulb03,
  Stars01,
} from '@untitledui/icons';
import { lazy, Suspense, useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { HashRouter, NavLink, Navigate, Route, Routes } from 'react-router-dom';
import { api, ApiError, setToken } from './api';
import type { Session } from './types';
import { ErrorContext, SessionContext, Surface } from './ui';

const GlossaryPage = lazy(() => import('./features/glossary/GlossaryPage'));
const ContextDashboard = lazy(() =>
  import('./features/context-center/ContextPages').then((module) => ({
    default: module.ContextDashboard,
  }))
);
const ArticlesPage = lazy(() =>
  import('./features/context-center/ContextPages').then((module) => ({
    default: module.ArticlesPage,
  }))
);
const MemoriesPage = lazy(() =>
  import('./features/context-center/ContextPages').then((module) => ({
    default: module.MemoriesPage,
  }))
);
const DocumentsPage = lazy(() =>
  import('./features/context-center/ContextPages').then((module) => ({
    default: module.DocumentsPage,
  }))
);
const ContextPreviewPage = lazy(() =>
  import('./features/context-center/ContextPages').then((module) => ({
    default: module.ContextPreviewPage,
  }))
);

export default function App() {
  const { t, i18n } = useTranslation('standalone');
  const [session, setSession] = useState<Session | null>(null);
  const [loading, setLoading] = useState(true);
  const [credential, setCredential] = useState('');
  const [error, setError] = useState('');
  const onError = useCallback((error: unknown) => {
    setError(error instanceof Error ? error.message : String(error));
    if (error instanceof ApiError && error.status === 401) {
      setSession(null);
    }
  }, []);
  useEffect(() => {
    api<Session>('session')
      .then(setSession)
      .catch(onError)
      .finally(() => setLoading(false));
  }, [onError]);
  const connect = async () => {
    setLoading(true);
    setError('');
    setToken(credential);
    try {
      setSession(await api<Session>('session'));
      setCredential('');
    } catch (error: unknown) {
      onError(error);
    } finally {
      setLoading(false);
    }
  };
  const navigation = [
    { to: '/', label: t('contextCenter'), icon: Home01 },
    { to: '/glossary', label: t('glossary'), icon: BookOpen01 },
    { to: '/articles', label: t('articles'), icon: File02 },
    { to: '/memories', label: t('memories'), icon: Lightbulb03 },
    { to: '/documents', label: t('documents'), icon: File02 },
    { to: '/preview', label: t('contextPreview'), icon: Stars01 },
  ];
  if (!session) {
    return (
      <main className="login-screen tw:bg-secondary">
        <Surface className="tw:w-full tw:max-w-md">
          <h1 className="tw:mb-2 tw:text-xl tw:font-semibold">
            {t('product')}
          </h1>
          <p className="tw:mb-6 tw:text-sm tw:text-tertiary">
            {t('connectHelp')}
          </p>
          {error && (
            <p role="alert" className="tw:mb-4 tw:text-error-primary">
              {error}
            </p>
          )}
          <Input
            label={t('apiToken')}
            type="password"
            value={credential}
            onChange={setCredential}
            autoComplete="off"
          />
          <Button
            className="tw:mt-5"
            isLoading={loading}
            onClick={() => void connect()}>
            {t('connect')}
          </Button>
        </Surface>
      </main>
    );
  }
  return (
    <ErrorContext.Provider value={onError}>
      <SessionContext.Provider value={session}>
        <HashRouter>
          <div className="app-frame">
            <aside className="sidebar tw:bg-primary tw:border-r tw:border-secondary">
              <div className="tw:px-4 tw:py-6">
                <h1 className="tw:font-semibold tw:text-lg">{t('product')}</h1>
                <p className="tw:mt-1 tw:text-xs tw:text-quaternary">
                  OpenMetadata 2.0.8
                </p>
              </div>
              <nav
                className="tw:flex-1 tw:space-y-1 tw:px-3"
                aria-label={t('navigation')}>
                {navigation.map((item) => (
                  <NavLink
                    className={({ isActive }) =>
                      `nav-entry ${isActive ? 'is-active' : ''}`
                    }
                    end={item.to === '/'}
                    key={item.to}
                    to={item.to}>
                    <item.icon className="tw:size-5 tw:shrink-0" />
                    {item.label}
                  </NavLink>
                ))}
              </nav>
              <div className="tw:border-t tw:border-secondary tw:p-4 tw:space-y-3">
                <label className="select-field">
                  {t('language')}
                  <select
                    value={i18n.language}
                    onChange={(event) =>
                      void i18n.changeLanguage(event.target.value)
                    }>
                    <option value="zh-CN">中文</option>
                    <option value="en">English</option>
                  </select>
                </label>
                <p className="tw:text-sm tw:text-secondary">{session.name}</p>
                <Button
                  size="sm"
                  color="link-gray"
                  onClick={() => {
                    setToken('');
                    setSession(null);
                  }}>
                  {t('disconnect')}
                </Button>
              </div>
            </aside>
            <main className="main-content tw:bg-secondary">
              {error && (
                <div
                  role="alert"
                  className="error-banner tw:mb-5 tw:bg-error-primary tw:text-error-primary">
                  <span>{error}</span>
                  <Button
                    size="sm"
                    color="link-gray"
                    onClick={() => setError('')}>
                    {t('close')}
                  </Button>
                </div>
              )}
              <Suspense fallback={<p>{t('loading')}</p>}>
                <Routes>
                  <Route path="/" element={<ContextDashboard />} />
                  <Route path="/glossary" element={<GlossaryPage />} />
                  <Route path="/articles" element={<ArticlesPage />} />
                  <Route path="/memories" element={<MemoriesPage />} />
                  <Route path="/documents" element={<DocumentsPage />} />
                  <Route path="/preview" element={<ContextPreviewPage />} />
                  <Route path="*" element={<Navigate to="/" replace />} />
                </Routes>
              </Suspense>
            </main>
          </div>
        </HashRouter>
      </SessionContext.Provider>
    </ErrorContext.Provider>
  );
}
