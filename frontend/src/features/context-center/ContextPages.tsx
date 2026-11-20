/* Licensed under the Apache License, Version 2.0. See LICENSE. */
import { Button, Input, TextArea } from '@openmetadata/ui-core-components';
import {
  BookOpen01,
  File02,
  Lightbulb03,
  Plus,
  Upload01,
} from '@untitledui/icons';
import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate, useSearchParams } from 'react-router-dom';
import * as api from '../../api';
import {
  displayName,
  paths,
  type Article,
  type ContextFile,
  type Entity,
  type EntityType,
  type Memory,
  type PackedContext,
  type Principal,
  type Term,
} from '../../types';
import {
  EditorDialog,
  EmptyState,
  Heading,
  Surface,
  useCollection,
  useError,
  useSession,
} from '../../ui';
import ContextKnowledgePillarCard from '../../upstream/components/ContextCenter/ContextKnowledgePillarCard/ContextKnowledgePillarCard.component';
import MemoriesView from '../../upstream/components/ContextCenter/MemoriesView/MemoriesView.component';
import type { CreateContextMemory } from '../../upstream/generated/api/context/createContextMemory';
import type { CreatePage } from '../../upstream/generated/api/data/createPage';
import { PageType } from '../../upstream/generated/entity/data/page';
import {
  ShareVisibility as MemoryVisibility,
  ShareRole as MemoryShareRole,
} from '../../upstream/generated/entity/context/contextMemory';
import { HistoryButton } from '../shared/HistoryButton';

function ArticleEditor({
  entity,
  onClose,
  onSaved,
}: {
  entity?: Article;
  onClose: () => void;
  onSaved: () => void;
}) {
  const { t } = useTranslation('standalone');
  const [name, setName] = useState(entity?.name ?? '');
  const [label, setLabel] = useState(entity?.displayName ?? '');
  const [description, setDescription] = useState(entity?.description ?? '');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const save = async () => {
    if (!name.trim() || !description.trim()) {
      setError(t('requiredFields'));
      return;
    }
    setError('');
    setSaving(true);
    try {
      const command: CreatePage = {
        name: name.trim(),
        displayName: label.trim(),
        description,
        pageType: PageType.Article,
        page: {},
        owners: entity?.owners,
        tags: entity?.tags,
        relatedEntities: entity?.relatedEntities,
        parent: entity?.parent,
      };
      if (entity) {
        await api.update<Article>('page', entity, command);
      } else {
        await api.create<Article>('page', command);
      }
      onSaved();
      onClose();
    } catch (error: unknown) {
      setError(error instanceof Error ? error.message : String(error));
    } finally {
      setSaving(false);
    }
  };
  return (
    <EditorDialog
      title={t(entity ? 'editArticle' : 'newArticle')}
      saving={saving}
      error={error}
      onClose={onClose}
      onSave={() => void save()}>
      <Input
        label={t('name')}
        inputDataTestId="entity-name"
        value={name}
        onChange={setName}
        isRequired
      />
      <Input label={t('displayName')} value={label} onChange={setLabel} />
      <TextArea
        label={t('articleBody')}
        value={description}
        onChange={setDescription}
        rows={14}
        isRequired
      />
    </EditorDialog>
  );
}

function MemoryEditor({
  entity,
  canEdit,
  onClose,
  onSaved,
}: {
  entity?: Memory;
  canEdit: boolean;
  onClose: () => void;
  onSaved: () => void;
}) {
  const { t } = useTranslation('standalone');
  const session = useSession();
  const onError = useError();
  const articles = useCollection<Article>('page');
  const files = useCollection<ContextFile>('contextFile');
  const originalSource = entity?.sourceEntity ?? entity?.sourceFile;
  const [sourceKey, setSourceKey] = useState(
    originalSource ? `${originalSource.type}:${originalSource.id}` : ''
  );
  const [principals, setPrincipals] = useState<Principal[]>([]);
  const [name] = useState(entity?.name ?? `memory_${crypto.randomUUID()}`);
  const [title, setTitle] = useState(entity?.title ?? '');
  const [question, setQuestion] = useState(entity?.question ?? '');
  const [answer, setAnswer] = useState(entity?.answer ?? '');
  const [visibility, setVisibility] = useState(
    entity?.shareConfig?.visibility ?? MemoryVisibility.Private
  );
  const [sharedWith, setSharedWith] = useState(
    entity?.shareConfig?.sharedWith ?? []
  );
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const managesSharing =
    !entity ||
    session.isAdmin ||
    entity.owners?.some(
      (owner) => owner.type === 'user' && owner.id === session.id
    );
  useEffect(() => {
    let active = true;
    api
      .api<{ data: Principal[] }>('users')
      .then((result) => {
        if (active) {
          setPrincipals(result.data);
        }
      })
      .catch(onError);
    return () => {
      active = false;
    };
  }, [onError]);
  const save = async () => {
    if (!title.trim() || !question.trim() || !answer.trim()) {
      setError(t('requiredFields'));
      return;
    }
    setError('');
    setSaving(true);
    try {
      const command: CreateContextMemory = {
        name,
        title: title.trim(),
        question,
        answer,
        owners: entity?.owners,
        shareConfig: {
          visibility,
          sharedWith: visibility === MemoryVisibility.Shared ? sharedWith : [],
        },
        sourceEntity: sourceKey
          ? (() => {
              const sourceType = sourceKey.startsWith('page:')
                ? 'page'
                : 'contextFile';
              const sourceId = sourceKey.substring(sourceKey.indexOf(':') + 1);
              const source = [...articles.data, ...files.data].find(
                (item) => item.id === sourceId
              );
              return source
                ? {
                    id: source.id,
                    type: sourceType,
                    name: source.name,
                    fullyQualifiedName: source.fullyQualifiedName,
                  }
                : originalSource;
            })()
          : undefined,
        description: entity?.description,
        memoryType: entity?.memoryType,
        memoryScope: entity?.memoryScope,
        status: entity?.status,
        tags: entity?.tags,
        primaryEntity: entity?.primaryEntity,
        relatedEntities: entity?.relatedEntities,
      };
      if (entity) {
        await api.update<Memory>('contextMemory', entity, command);
      } else {
        await api.create<Memory>('contextMemory', command);
      }
      onSaved();
      onClose();
    } catch (error: unknown) {
      setError(error instanceof Error ? error.message : String(error));
    } finally {
      setSaving(false);
    }
  };
  return (
    <EditorDialog
      title={t(entity ? 'memory' : 'newMemory')}
      saving={saving}
      error={error}
      onClose={onClose}
      onSave={canEdit ? () => void save() : undefined}>
      <Input
        label={t('title')}
        inputDataTestId="memory-title"
        value={title}
        onChange={setTitle}
        isReadOnly={!canEdit}
        isRequired
      />
      <TextArea
        label={t('question')}
        value={question}
        onChange={setQuestion}
        isReadOnly={!canEdit}
        isRequired
        rows={3}
      />
      <TextArea
        label={t('answer')}
        value={answer}
        onChange={setAnswer}
        isReadOnly={!canEdit}
        isRequired
        rows={7}
      />
      {entity && <HistoryButton type="contextMemory" entity={entity} />}
      <label className="select-field">
        {t('sourceDocument')}
        <select
          value={sourceKey}
          disabled={!canEdit}
          onChange={(event) => setSourceKey(event.target.value)}>
          <option value="">{t('noSource')}</option>
          {originalSource &&
            ![...articles.data, ...files.data].some(
              (item) => item.id === originalSource.id
            ) && (
              <option value={`${originalSource.type}:${originalSource.id}`}>
                {displayName(originalSource)}
              </option>
            )}
          {articles.data.map((source) => (
            <option value={`page:${source.id}`} key={source.id}>
              {displayName(source)}
            </option>
          ))}
          {files.data.map((source) => (
            <option value={`contextFile:${source.id}`} key={source.id}>
              {displayName(source)}
            </option>
          ))}
        </select>
      </label>
      <label className="select-field">
        {t('visibility')}
        <select
          value={visibility}
          disabled={!canEdit || !managesSharing}
          onChange={(e) => setVisibility(e.target.value as MemoryVisibility)}>
          <option value={MemoryVisibility.Private}>{t('private')}</option>
          <option value={MemoryVisibility.Entity}>{t('organization')}</option>
          <option value={MemoryVisibility.Shared}>{t('shared')}</option>
        </select>
      </label>
      {visibility === MemoryVisibility.Shared && (
        <>
          <label className="select-field">
            {t('sharedWith')}
            <select
              multiple
              disabled={!canEdit || !managesSharing}
              value={sharedWith.map((share) => share.principal?.id ?? '')}
              onChange={(e) =>
                setSharedWith(
                  Array.from(e.target.selectedOptions, (option) => ({
                    principal: { id: option.value, type: 'user' },
                    role:
                      sharedWith.find((s) => s.principal?.id === option.value)
                        ?.role ?? MemoryShareRole.Viewer,
                  }))
                )
              }>
              {principals
                .filter((principal) => principal.id !== session.id)
                .map((principal) => (
                  <option key={principal.id} value={principal.id}>
                    {principal.name}
                  </option>
                ))}
            </select>
          </label>
          {sharedWith.map((share) => (
            <label className="select-field" key={share.principal?.id}>
              {principals.find(
                (principal) => principal.id === share.principal?.id
              )?.name ??
                share.principal?.name ??
                share.principal?.id}
              <select
                disabled={!canEdit || !managesSharing}
                value={share.role ?? MemoryShareRole.Viewer}
                onChange={(e) =>
                  setSharedWith((shares) =>
                    shares.map((item) =>
                      item.principal?.id === share.principal?.id
                        ? { ...item, role: e.target.value as MemoryShareRole }
                        : item
                    )
                  )
                }>
                <option value={MemoryShareRole.Viewer}>{t('viewer')}</option>
                <option value={MemoryShareRole.Editor}>{t('editor')}</option>
              </select>
            </label>
          ))}
        </>
      )}
    </EditorDialog>
  );
}

export function ArticlesPage() {
  const { t } = useTranslation('standalone');
  const articles = useCollection<Article>('page');
  const session = useSession();
  const onError = useError();
  const [selectedId, setSelectedId] = useState<string>();
  const [editor, setEditor] = useState<{ entity?: Article }>();
  const [query, setQuery] = useState('');
  const article =
    articles.data.find((item) => item.id === selectedId) ?? articles.data[0];
  const canEdit = session.isAdmin || session.scopes.includes('context:write');
  const archive = async (entity: Article) => {
    if (!window.confirm(t('archiveConfirm'))) {
      return;
    }
    try {
      await api.archive('page', entity);
      articles.reload();
    } catch (error: unknown) {
      onError(error);
    }
  };
  return (
    <>
      <Heading
        title={t('articles')}
        subtitle={t('articleSubtitle')}
        actions={
          canEdit && (
            <Button
              iconLeading={Plus}
              data-testid="new-article"
              onClick={() => setEditor({})}>
              {t('newArticle')}
            </Button>
          )
        }
      />
      <div className="record-layout">
        <Surface>
          <Input label={t('search')} value={query} onChange={setQuery} />
          <div className="tw:mt-4 tw:space-y-1">
            {articles.data
              .filter((item) =>
                displayName(item).toLowerCase().includes(query.toLowerCase())
              )
              .map((item) => (
                <button
                  className={`tree-entry ${
                    article?.id === item.id ? 'is-active' : ''
                  }`}
                  key={item.id}
                  onClick={() => setSelectedId(item.id)}
                  type="button">
                  <File02 className="tw:size-4" />
                  {displayName(item)}
                </button>
              ))}
          </div>
        </Surface>
        <Surface>
          {article ? (
            <div className="tw:space-y-5">
              <h2 className="tw:text-xl tw:font-semibold">
                {displayName(article)}
              </h2>
              <div className="tw:flex tw:gap-2">
                <HistoryButton type="page" entity={article} />
                {canEdit && (
                  <>
                    <Button
                      color="secondary"
                      size="sm"
                      onClick={() => setEditor({ entity: article })}>
                      {t('edit')}
                    </Button>
                    <Button
                      color="secondary"
                      size="sm"
                      onClick={() => void archive(article)}>
                      {t('archive')}
                    </Button>
                  </>
                )}
              </div>
              <p className="preserve-text">{article.description}</p>
            </div>
          ) : (
            <EmptyState />
          )}
        </Surface>
      </div>
      {editor && (
        <ArticleEditor
          entity={editor.entity}
          onClose={() => setEditor(undefined)}
          onSaved={articles.reload}
        />
      )}
    </>
  );
}

export function MemoriesPage() {
  const { t } = useTranslation('standalone');
  const session = useSession();
  const onError = useError();
  const memories = useCollection<Memory>('contextMemory');
  const [searchParams] = useSearchParams();
  const [editor, setEditor] = useState<{ entity?: Memory }>();
  const [query, setQuery] = useState('');
  const canWrite = session.isAdmin || session.scopes.includes('context:write');
  const canEdit = (entity?: Memory) =>
    canWrite &&
    (!entity ||
      session.isAdmin ||
      entity.owners?.some(
        (owner) => owner.type === 'user' && owner.id === session.id
      ) ||
      entity.shareConfig?.sharedWith?.some(
        (share) =>
          share.role === MemoryShareRole.Editor &&
          share.principal?.id === session.id
      )) === true;
  const targetName = searchParams.get('memory');
  useEffect(() => {
    if (targetName) {
      const target = memories.data.find((item) => item.name === targetName);
      if (target) {
        setEditor({ entity: target });
      }
    }
  }, [targetName, memories.data]);
  const doArchive = async (entity: Memory) => {
    if (!window.confirm(t('archiveConfirm'))) {
      return;
    }
    try {
      await api.archive('contextMemory', entity);
      memories.reload();
    } catch (error: unknown) {
      onError(error);
    }
  };
  return (
    <>
      <Heading
        title={t('memories')}
        subtitle={t('memorySubtitle')}
        actions={
          canWrite && (
            <Button
              iconLeading={Plus}
              data-testid="new-memory"
              onClick={() => setEditor({})}>
              {t('newMemory')}
            </Button>
          )
        }
      />
      <Surface>
        <Input label={t('search')} value={query} onChange={setQuery} />
        <MemoriesView
          canDelete={canWrite}
          canEdit={canWrite}
          currentUserName={session.name}
          isAdminUser={session.isAdmin}
          isLoading={memories.loading}
          isSearching={Boolean(query)}
          data={memories.data.filter((memory) =>
            `${memory.title ?? ''} ${memory.answer}`
              .toLowerCase()
              .includes(query.toLowerCase())
          )}
          onEditMemory={(memory) =>
            setEditor({
              entity: memories.data.find((item) => item.id === memory.id),
            })
          }
          onViewMemory={(memory) =>
            setEditor({
              entity: memories.data.find((item) => item.id === memory.id),
            })
          }
          onDeleteMemory={(memory) => {
            const target = memories.data.find((item) => item.id === memory.id);
            if (target) {
              void doArchive(target);
            }
          }}
        />
        {!memories.loading && !memories.data.length && <EmptyState />}
      </Surface>
      {editor && (
        <MemoryEditor
          entity={editor.entity}
          canEdit={canEdit(editor.entity)}
          onClose={() => setEditor(undefined)}
          onSaved={memories.reload}
        />
      )}
    </>
  );
}

export function DocumentsPage() {
  const { t } = useTranslation('standalone');
  const documents = useCollection<ContextFile>('contextFile');
  const session = useSession();
  const onError = useError();
  const input = useRef<HTMLInputElement>(null);
  const replacement = useRef<ContextFile>();
  const [selectedId, setSelectedId] = useState<string>();
  const [uploading, setUploading] = useState(false);
  const file =
    documents.data.find((item) => item.id === selectedId) ?? documents.data[0];
  const canWrite = session.isAdmin || session.scopes.includes('context:write');
  const canEditFile =
    file &&
    canWrite &&
    (session.isAdmin ||
      file.owners?.some(
        (owner) => owner.type === 'user' && owner.id === session.id
      ));
  const upload = async (selected: File) => {
    setUploading(true);
    try {
      const target = replacement.current;
      const result = await api.api<ContextFile>(
        target
          ? `${paths.contextFile}/${target.id}/content?revision=${target.revision}`
          : `${paths.contextFile}/upload`,
        {
          method: target ? 'PUT' : 'POST',
          body: selected,
          headers: {
            'Content-Type': 'application/octet-stream',
            'X-File-Name': encodeURIComponent(selected.name),
          },
        }
      );
      setSelectedId(result.id);
      documents.reload();
    } catch (error: unknown) {
      onError(error);
    } finally {
      setUploading(false);
      replacement.current = undefined;
      if (input.current) {
        input.current.value = '';
      }
    }
  };
  const doArchive = async (entity: ContextFile) => {
    if (!window.confirm(t('archiveConfirm'))) {
      return;
    }
    try {
      await api.archive('contextFile', entity);
      documents.reload();
    } catch (error: unknown) {
      onError(error);
    }
  };
  return (
    <>
      <Heading
        title={t('documents')}
        subtitle={t('documentSubtitle')}
        actions={
          canWrite && (
            <Button
              iconLeading={Upload01}
              isLoading={uploading}
              data-testid="upload-document"
              onClick={() => {
                replacement.current = undefined;
                input.current?.click();
              }}>
              {t('upload')}
            </Button>
          )
        }
      />
      <input
        hidden
        type="file"
        ref={input}
        data-testid="file-input"
        accept=".txt,.md,.csv,.json"
        onChange={(event) => {
          const file = event.target.files?.[0];
          if (file) {
            void upload(file);
          }
        }}
      />
      <div className="record-layout">
        <Surface>
          <div className="tw:space-y-1">
            {documents.data.map((item) => (
              <button
                className={`tree-entry ${
                  file?.id === item.id ? 'is-active' : ''
                }`}
                type="button"
                key={item.id}
                onClick={() => setSelectedId(item.id)}>
                <File02 className="tw:size-4" />
                {displayName(item)}
              </button>
            ))}
          </div>
        </Surface>
        <Surface>
          {file ? (
            <div className="tw:space-y-5">
              <div>
                <h2 className="tw:text-xl tw:font-semibold">
                  {displayName(file)}
                </h2>
                <p className="tw:mt-1 tw:text-xs tw:text-quaternary">
                  {t('revision', { revision: file.revision })} ·{' '}
                  {Math.ceil(file.size / 1024)} KB
                </p>
              </div>
              <div className="tw:flex tw:flex-wrap tw:gap-2">
                <HistoryButton type="contextFile" entity={file} />
                <Button
                  color="secondary"
                  size="sm"
                  onClick={() => {
                    void api.downloadFile(file).catch(onError);
                  }}>
                  {t('download')}
                </Button>
                {canEditFile && (
                  <>
                    <Button
                      color="secondary"
                      size="sm"
                      isDisabled={uploading}
                      onClick={() => {
                        replacement.current = file;
                        input.current?.click();
                      }}>
                      {t('replaceContent')}
                    </Button>
                    <Button
                      color="secondary"
                      size="sm"
                      onClick={() => void doArchive(file)}>
                      {t('archive')}
                    </Button>
                  </>
                )}
              </div>
              <pre className="document-preview" data-testid="document-preview">
                {file.extractedText}
              </pre>
            </div>
          ) : (
            <EmptyState />
          )}
        </Surface>
      </div>
    </>
  );
}

export function ContextPreviewPage() {
  const { t } = useTranslation('standalone');
  const onError = useError();
  const terms = useCollection<Term>('glossaryTerm');
  const articles = useCollection<Article>('page');
  const memories = useCollection<Memory>('contextMemory');
  const files = useCollection<ContextFile>('contextFile');
  const [selected, setSelected] = useState<
    Record<string, { id: string; type: EntityType }>
  >({});
  const [maxChars, setMaxChars] = useState(8000);
  const [result, setResult] = useState<PackedContext>();
  const [loading, setLoading] = useState(false);
  const groups: { type: EntityType; label: string; items: Entity[] }[] = [
    { type: 'glossaryTerm', label: t('terms'), items: terms.data },
    { type: 'page', label: t('articles'), items: articles.data },
    { type: 'contextMemory', label: t('memories'), items: memories.data },
    { type: 'contextFile', label: t('documents'), items: files.data },
  ];
  const pack = async () => {
    setLoading(true);
    setResult(undefined);
    try {
      setResult(
        await api.api<PackedContext>('ai/context', {
          method: 'POST',
          body: JSON.stringify({ sources: Object.values(selected), maxChars }),
        })
      );
    } catch (error: unknown) {
      onError(error);
    } finally {
      setLoading(false);
    }
  };
  return (
    <>
      <Heading
        title={t('contextPreview')}
        subtitle={t('previewSubtitle')}
        actions={
          <Button
            isLoading={loading}
            isDisabled={!Object.keys(selected).length}
            data-testid="pack-context"
            onClick={() => void pack()}>
            {t('generateContext')}
          </Button>
        }
      />
      <div className="record-layout">
        <Surface>
          <Input
            label={t('characterBudget')}
            type="number"
            value={String(maxChars)}
            onChange={(value) => setMaxChars(Number(value))}
          />
          {groups.map((group) => (
            <section className="tw:mt-5" key={group.type}>
              <h2 className="tw:mb-2 tw:font-semibold">{group.label}</h2>
              {group.items.map((item) => {
                const key = `${group.type}:${item.id}`;
                return (
                  <label className="check-field" key={key}>
                    <input
                      type="checkbox"
                      data-testid={`select-source-${item.id}`}
                      checked={Boolean(selected[key])}
                      onChange={(event) =>
                        setSelected((previous) => {
                          const next = { ...previous };
                          if (event.target.checked) {
                            next[key] = { id: item.id, type: group.type };
                          } else {
                            delete next[key];
                          }
                          return next;
                        })
                      }
                    />
                    {'title' in item
                      ? item.title || displayName(item)
                      : displayName(item)}
                  </label>
                );
              })}
            </section>
          ))}
        </Surface>
        <Surface>
          {result ? (
            <div className="tw:space-y-4">
              <p className="tw:text-sm tw:text-tertiary">
                {t('packingStats', {
                  used: result.usedChars,
                  budget: result.maxChars,
                  count: result.sources.length,
                  omitted: result.omitted.length,
                })}
              </p>
              <pre className="document-preview" data-testid="context-preview">
                {result.markdown}
              </pre>
              <h2 className="tw:font-semibold">{t('sources')}</h2>
              <ul className="tw:space-y-2 tw:text-sm">
                {result.sources.map((source) => (
                  <li
                    className="tw:break-all"
                    key={`${source.type}:${source.id}`}>
                    {source.displayName || source.fullyQualifiedName} ·{' '}
                    {t('revision', { revision: source.revision })}
                  </li>
                ))}
              </ul>
            </div>
          ) : (
            <EmptyState />
          )}
        </Surface>
      </div>
    </>
  );
}

export function ContextDashboard() {
  const { t } = useTranslation('standalone');
  const navigate = useNavigate();
  const terms = useCollection<Term>('glossaryTerm');
  const articles = useCollection<Article>('page');
  const memories = useCollection<Memory>('contextMemory');
  const files = useCollection<ContextFile>('contextFile');
  const groups = [
    {
      title: t('terms'),
      subtitle: t('glossarySubtitle'),
      icon: BookOpen01,
      collection: terms,
      route: '/glossary',
    },
    {
      title: t('articles'),
      subtitle: t('articleSubtitle'),
      icon: File02,
      collection: articles,
      route: '/articles',
    },
    {
      title: t('memories'),
      subtitle: t('memorySubtitle'),
      icon: Lightbulb03,
      collection: memories,
      route: '/memories',
    },
    {
      title: t('documents'),
      subtitle: t('documentSubtitle'),
      icon: File02,
      collection: files,
      route: '/documents',
    },
  ];
  return (
    <>
      <Heading title={t('contextCenter')} subtitle={t('dashboardSubtitle')} />
      <div className="dashboard-grid">
        {groups.map((group) => (
          <ContextKnowledgePillarCard
            key={group.route}
            icon={group.icon}
            title={group.title}
            subtitle={group.subtitle}
            stat={String(group.collection.data.length)}
            statSub={t('items')}
            cta={t('browse')}
            onClick={() => navigate(group.route)}
            isLoading={group.collection.loading}
            recent={[...group.collection.data]
              .sort((a, b) => (b.updatedAt ?? 0) - (a.updatedAt ?? 0))
              .slice(0, 3)
              .map((item) => ({
                title:
                  'title' in item
                    ? item.title || displayName(item)
                    : displayName(item),
                meta: [item.updatedBy ?? ''],
                onClick: () => navigate(group.route),
              }))}
          />
        ))}
      </div>
    </>
  );
}
