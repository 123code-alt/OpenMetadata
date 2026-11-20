/* Licensed under the Apache License, Version 2.0. See LICENSE. */
import { Button } from '@openmetadata/ui-core-components';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { api } from '../../api';
import { paths, type Entity, type EntityType } from '../../types';
import { EditorDialog, useError } from '../../ui';

interface Revision {
  name: string;
  description?: string;
  answer?: string;
  revision: number;
  updatedBy: string;
  updatedAt: number;
}

export function HistoryButton({
  type,
  entity,
}: {
  type: EntityType;
  entity: Entity;
}) {
  const { t } = useTranslation('standalone');
  const onError = useError();
  const [versions, setVersions] = useState<Revision[]>();
  const open = async () => {
    try {
      setVersions(
        (
          await api<{ versions: Revision[] }>(
            `${paths[type]}/${entity.id}/versions`
          )
        ).versions
      );
    } catch (error: unknown) {
      onError(error);
    }
  };
  return (
    <>
      <Button color="secondary" size="sm" onClick={() => void open()}>
        {t('history')}
      </Button>
      {versions && (
        <EditorDialog
          title={t('history')}
          onClose={() => setVersions(undefined)}
          onSave={() => setVersions(undefined)}
          saving={false}>
          {versions.map((version) => (
            <article
              className="tw:rounded-lg tw:border tw:border-secondary tw:p-3"
              key={version.revision}>
              <h3 className="tw:font-semibold">
                {version.name} · {t('revision', { revision: version.revision })}
              </h3>
              <p className="tw:text-xs tw:text-quaternary">
                {version.updatedBy} ·{' '}
                {new Date(version.updatedAt).toLocaleString()}
              </p>
              <p className="preserve-text tw:mt-3 tw:text-sm">
                {version.answer ?? version.description}
              </p>
            </article>
          ))}
        </EditorDialog>
      )}
    </>
  );
}
