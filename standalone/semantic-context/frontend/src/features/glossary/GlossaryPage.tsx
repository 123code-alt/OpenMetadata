/* Licensed under the Apache License, Version 2.0. See LICENSE. */
import { Button, Input, TextArea } from '@openmetadata/ui-core-components';
import { BookOpen01, Plus } from '@untitledui/icons';
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import * as api from '../../api';
import {
  displayName,
  type Entity,
  type EntityType,
  type Glossary,
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
import GlossaryTermSynonyms from '../../upstream/components/Glossary/GlossaryTerms/tabs/GlossaryTermSynonyms';
import GlossaryTermReferencesModal from '../../upstream/components/Glossary/GlossaryTerms/GlossaryTermReferencesModal.component';
import type {
  GlossaryTerm,
  TermReference,
} from '../../upstream/generated/entity/data/glossaryTerm';
import type { CreateGlossaryTerm } from '../../upstream/generated/api/data/createGlossaryTerm';
import { buildTree } from '../../upstream/utils/GlossaryTree';
import { HistoryButton } from '../shared/HistoryButton';

function termCommand(term: GlossaryTerm): CreateGlossaryTerm {
  return {
    name: term.name,
    displayName: term.displayName,
    description: term.description,
    glossary: term.glossary.fullyQualifiedName ?? '',
    parent: term.parent?.fullyQualifiedName,
    synonyms: term.synonyms,
    references: term.references,
    owners: term.owners,
    tags: term.tags,
    mutuallyExclusive: term.mutuallyExclusive,
    relatedTerms: term.relatedTerms
      ?.map((edge) => edge.term.fullyQualifiedName ?? '')
      .filter(Boolean),
  };
}

interface TreeItem {
  id: string;
  name?: string;
  displayName?: string;
  children?: TreeItem[];
}

function TermTree({
  nodes,
  selectedId,
  onSelect,
}: {
  nodes: TreeItem[];
  selectedId?: string;
  onSelect: (id: string) => void;
}) {
  return (
    <ul className="term-tree">
      {nodes.map((node) => (
        <li key={node.id}>
          <button
            className={`tree-entry ${
              selectedId === node.id ? 'is-active' : ''
            }`}
            onClick={() => onSelect(node.id)}
            type="button">
            <BookOpen01 className="tw:size-4" />
            {displayName(node)}
          </button>
          {node.children?.length ? (
            <TermTree
              nodes={node.children}
              selectedId={selectedId}
              onSelect={onSelect}
            />
          ) : null}
        </li>
      ))}
    </ul>
  );
}

function GlossaryEditor({
  entity,
  onClose,
  onSaved,
}: {
  entity?: Glossary;
  onClose: () => void;
  onSaved: (entity: Glossary) => void;
}) {
  const { t } = useTranslation('standalone');
  const [name, setName] = useState(entity?.name ?? '');
  const [label, setLabel] = useState(entity?.displayName ?? '');
  const [description, setDescription] = useState(entity?.description ?? '');
  const [exclusive, setExclusive] = useState(
    entity?.mutuallyExclusive ?? false
  );
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const save = async () => {
    setError('');
    if (!name.trim() || !description.trim()) {
      setError(t('requiredFields'));
      return;
    }
    setSaving(true);
    try {
      const command = {
        name: name.trim(),
        displayName: label.trim(),
        description,
        mutuallyExclusive: exclusive,
        owners: entity?.owners,
        tags: entity?.tags,
      };
      const saved = entity
        ? await api.update<Glossary>('glossary', entity, command)
        : await api.create<Glossary>('glossary', command);
      onSaved(saved);
      onClose();
    } catch (error: unknown) {
      setError(error instanceof Error ? error.message : String(error));
    } finally {
      setSaving(false);
    }
  };
  return (
    <EditorDialog
      title={t(entity ? 'editGlossary' : 'newGlossary')}
      onClose={onClose}
      onSave={() => void save()}
      saving={saving}
      error={error}>
      <Input
        label={t('name')}
        inputDataTestId="entity-name"
        value={name}
        onChange={setName}
        isRequired
      />
      <Input label={t('displayName')} value={label} onChange={setLabel} />
      <TextArea
        label={t('description')}
        value={description}
        onChange={setDescription}
        isRequired
        rows={6}
      />
      <label className="check-field">
        <input
          type="checkbox"
          checked={exclusive}
          onChange={(e) => setExclusive(e.target.checked)}
        />
        {t('mutuallyExclusive')}
      </label>
    </EditorDialog>
  );
}

function TermEditor({
  entity,
  glossaries,
  glossary,
  terms,
  onClose,
  onSaved,
}: {
  entity?: Term;
  glossaries: Glossary[];
  glossary: Glossary;
  terms: Term[];
  onClose: () => void;
  onSaved: (entity: Term) => void;
}) {
  const { t } = useTranslation('standalone');
  const [name, setName] = useState(entity?.name ?? '');
  const [label, setLabel] = useState(entity?.displayName ?? '');
  const [description, setDescription] = useState(entity?.description ?? '');
  const [glossaryFqn, setGlossaryFqn] = useState(
    entity?.glossary.fullyQualifiedName ?? glossary.fullyQualifiedName ?? ''
  );
  const [parent, setParent] = useState(
    entity?.parent?.fullyQualifiedName ?? ''
  );
  const [related, setRelated] = useState(
    entity?.relatedTerms?.map((edge) => edge.term.fullyQualifiedName ?? '') ??
      []
  );
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
      const command: CreateGlossaryTerm = {
        ...(entity ? termCommand(entity) : {}),
        name: name.trim(),
        displayName: label.trim(),
        description,
        glossary: glossaryFqn,
        parent: parent || undefined,
        relatedTerms: related,
      };
      const saved = entity
        ? await api.update<Term>('glossaryTerm', entity, command)
        : await api.create<Term>('glossaryTerm', command);
      onSaved(saved);
      onClose();
    } catch (error: unknown) {
      setError(error instanceof Error ? error.message : String(error));
    } finally {
      setSaving(false);
    }
  };
  return (
    <EditorDialog
      title={t(entity ? 'editTerm' : 'newTerm')}
      onClose={onClose}
      onSave={() => void save()}
      saving={saving}
      error={error}>
      <Input
        label={t('name')}
        inputDataTestId="entity-name"
        value={name}
        onChange={setName}
        isRequired
      />
      <Input label={t('displayName')} value={label} onChange={setLabel} />
      <TextArea
        label={t('description')}
        value={description}
        onChange={setDescription}
        isRequired
        rows={5}
      />
      <label className="select-field">
        {t('glossary')}
        <select
          value={glossaryFqn}
          onChange={(e) => {
            setGlossaryFqn(e.target.value);
            setParent('');
          }}>
          {glossaries.map((g) => (
            <option value={g.fullyQualifiedName} key={g.id}>
              {displayName(g)}
            </option>
          ))}
        </select>
      </label>
      <label className="select-field">
        {t('parentTerm')}
        <select value={parent} onChange={(e) => setParent(e.target.value)}>
          <option value="">{t('rootTerm')}</option>
          {terms
            .filter(
              (term) =>
                term.id !== entity?.id &&
                term.glossary.fullyQualifiedName === glossaryFqn
            )
            .map((term) => (
              <option value={term.fullyQualifiedName} key={term.id}>
                {term.fullyQualifiedName}
              </option>
            ))}
        </select>
      </label>
      <label className="select-field">
        {t('relatedTerms')}
        <select
          multiple
          value={related}
          onChange={(e) =>
            setRelated(
              Array.from(e.target.selectedOptions, (option) => option.value)
            )
          }>
          {terms
            .filter((term) => term.id !== entity?.id)
            .map((term) => (
              <option value={term.fullyQualifiedName} key={term.id}>
                {term.fullyQualifiedName}
              </option>
            ))}
        </select>
      </label>
    </EditorDialog>
  );
}

export default function GlossaryPage() {
  const { t } = useTranslation('standalone');
  const session = useSession();
  const onError = useError();
  const glossaries = useCollection<Glossary>('glossary');
  const allTerms = useCollection<Term>('glossaryTerm');
  const [selectedGlossaryId, setSelectedGlossaryId] = useState<string>();
  const [selectedTermId, setSelectedTermId] = useState<string>();
  const [glossaryEditor, setGlossaryEditor] = useState<{ entity?: Glossary }>();
  const [termEditor, setTermEditor] = useState<{ entity?: Term }>();
  const [referenceEditor, setReferenceEditor] = useState(false);
  const [query, setQuery] = useState('');
  const glossary =
    glossaries.data.find((g) => g.id === selectedGlossaryId) ??
    glossaries.data[0];
  const terms = useMemo(
    () => allTerms.data.filter((term) => term.glossary.id === glossary?.id),
    [allTerms.data, glossary?.id]
  );
  const tree = useMemo(() => buildTree(terms), [terms]);
  const term = terms.find((item) => item.id === selectedTermId);
  const canEdit = session.isAdmin || session.scopes.includes('glossary:write');
  const reload = () => {
    glossaries.reload();
    allTerms.reload();
  };
  const savedTerm = (term: Term) => {
    setSelectedGlossaryId(term.glossary.id);
    setSelectedTermId(term.id);
    reload();
  };
  const updateTerm = async (changed: GlossaryTerm) => {
    if (!term) {
      return;
    }
    savedTerm(
      await api.update<Term>('glossaryTerm', term, termCommand(changed))
    );
  };
  const doArchive = async (type: EntityType, entity: Entity) => {
    if (!window.confirm(t('archiveConfirm'))) {
      return;
    }
    try {
      await api.archive(type, entity, true);
      setSelectedTermId(undefined);
      reload();
    } catch (error: unknown) {
      onError(error);
    }
  };
  return (
    <>
      <Heading
        title={t('glossary')}
        subtitle={t('glossarySubtitle')}
        actions={
          canEdit && (
            <Button
              iconLeading={Plus}
              data-testid="new-glossary"
              onClick={() => setGlossaryEditor({})}>
              {t('newGlossary')}
            </Button>
          )
        }
      />
      <div className="glossary-layout">
        <Surface>
          <Input label={t('search')} value={query} onChange={setQuery} />
          <nav className="tw:mt-4 tw:space-y-1">
            {glossaries.data
              .filter((g) =>
                displayName(g).toLowerCase().includes(query.toLowerCase())
              )
              .map((g) => (
                <button
                  type="button"
                  data-testid={`glossary-${g.id}`}
                  className={`tree-entry ${
                    glossary?.id === g.id ? 'is-active' : ''
                  }`}
                  key={g.id}
                  onClick={() => {
                    setSelectedGlossaryId(g.id);
                    setSelectedTermId(undefined);
                  }}>
                  <BookOpen01 className="tw:size-4 tw:shrink-0" />
                  <span className="tw:grow tw:text-left">{displayName(g)}</span>
                  <span className="tw:text-quaternary">{g.termCount ?? 0}</span>
                </button>
              ))}
          </nav>
          {!glossaries.loading && !glossaries.data.length && <EmptyState />}
        </Surface>
        <div className="tw:min-w-0 tw:space-y-5">
          {glossary ? (
            <>
              <Surface>
                <div className="tw:flex tw:flex-wrap tw:justify-between tw:gap-3 tw:mb-3">
                  <div>
                    <h2 className="tw:text-xl tw:font-semibold">
                      {displayName(glossary)}
                    </h2>
                    <p className="tw:text-xs tw:text-quaternary">
                      {glossary.fullyQualifiedName}
                    </p>
                  </div>
                  <div className="tw:flex tw:gap-2">
                    <HistoryButton type="glossary" entity={glossary} />
                    {canEdit && (
                      <>
                        <Button
                          color="secondary"
                          size="sm"
                          onClick={() =>
                            setGlossaryEditor({ entity: glossary })
                          }>
                          {t('edit')}
                        </Button>
                        <Button
                          color="secondary"
                          size="sm"
                          onClick={() => void doArchive('glossary', glossary)}>
                          {t('archive')}
                        </Button>
                      </>
                    )}
                  </div>
                </div>
                <p className="preserve-text tw:text-sm tw:text-tertiary">
                  {glossary.description}
                </p>
              </Surface>
              <div className="term-layout">
                <Surface>
                  <div className="tw:flex tw:items-center tw:justify-between tw:mb-4">
                    <h2 className="tw:font-semibold">{t('terms')}</h2>
                    {canEdit && (
                      <Button
                        color="secondary"
                        size="sm"
                        data-testid="new-term"
                        onClick={() => setTermEditor({})}>
                        {t('newTerm')}
                      </Button>
                    )}
                  </div>
                  <TermTree
                    nodes={tree}
                    selectedId={selectedTermId}
                    onSelect={setSelectedTermId}
                  />
                  {!allTerms.loading && !terms.length && <EmptyState />}
                </Surface>
                <Surface>
                  {term ? (
                    <div className="tw:space-y-5" data-testid="term-detail">
                      <div>
                        <h2 className="tw:text-xl tw:font-semibold">
                          {displayName(term)}
                        </h2>
                        <p className="tw:text-xs tw:text-quaternary tw:break-all">
                          {term.fullyQualifiedName}
                        </p>
                      </div>
                      <div className="tw:flex tw:gap-2">
                        <HistoryButton type="glossaryTerm" entity={term} />
                        {canEdit && (
                          <>
                            <Button
                              color="secondary"
                              size="sm"
                              onClick={() => setTermEditor({ entity: term })}>
                              {t('edit')}
                            </Button>
                            <Button
                              color="secondary"
                              size="sm"
                              onClick={() =>
                                void doArchive('glossaryTerm', term)
                              }>
                              {t('archive')}
                            </Button>
                          </>
                        )}
                      </div>
                      <p className="preserve-text tw:text-sm tw:text-tertiary">
                        {term.description}
                      </p>
                      <GlossaryTermSynonyms
                        key={term.id}
                        canEdit={canEdit}
                        glossaryTerm={term}
                        onGlossaryTermUpdate={updateTerm}
                        onError={onError}
                      />
                      <section>
                        <div className="tw:flex tw:items-center tw:justify-between">
                          <h3 className="tw:font-medium">{t('references')}</h3>
                          {canEdit && (
                            <Button
                              color="link-color"
                              size="sm"
                              onClick={() => setReferenceEditor(true)}>
                              {t('edit')}
                            </Button>
                          )}
                        </div>
                        <ul className="tw:mt-2 tw:space-y-2">
                          {term.references?.map((ref) => (
                            <li key={ref.endpoint}>
                              <a
                                className="tw:text-brand-secondary"
                                href={
                                  ref.endpoint?.startsWith('http://') ||
                                  ref.endpoint?.startsWith('https://')
                                    ? ref.endpoint
                                    : undefined
                                }
                                target="_blank"
                                rel="noopener noreferrer">
                                {ref.name}
                              </a>
                            </li>
                          ))}
                        </ul>
                      </section>
                      <section>
                        <h3 className="tw:font-medium">{t('relatedTerms')}</h3>
                        <div className="tw:mt-2 tw:space-y-1">
                          {term.relatedTerms?.map((edge) => (
                            <button
                              className="tree-entry"
                              type="button"
                              key={edge.term.id}
                              onClick={() => {
                                const target = allTerms.data.find(
                                  (item) => item.id === edge.term.id
                                );
                                if (target) {
                                  setSelectedGlossaryId(target.glossary.id);
                                  setSelectedTermId(target.id);
                                }
                              }}>
                              {displayName(edge.term)}
                            </button>
                          ))}
                        </div>
                      </section>
                    </div>
                  ) : (
                    <EmptyState />
                  )}
                </Surface>
              </div>
            </>
          ) : (
            <Surface>
              <EmptyState />
            </Surface>
          )}
        </div>
      </div>
      {glossaryEditor && (
        <GlossaryEditor
          entity={glossaryEditor.entity}
          onClose={() => setGlossaryEditor(undefined)}
          onSaved={(value) => {
            setSelectedGlossaryId(value.id);
            reload();
          }}
        />
      )}
      {termEditor && glossary && (
        <TermEditor
          entity={termEditor.entity}
          glossary={glossary}
          glossaries={glossaries.data}
          terms={allTerms.data}
          onClose={() => setTermEditor(undefined)}
          onSaved={savedTerm}
        />
      )}
      {referenceEditor && term && (
        <GlossaryTermReferencesModal
          isVisible
          references={term.references ?? []}
          onClose={() => setReferenceEditor(false)}
          onSave={async (references: TermReference[]) => {
            try {
              await updateTerm({ ...term, references });
              setReferenceEditor(false);
            } catch (error: unknown) {
              onError(error);
            }
          }}
        />
      )}
    </>
  );
}
