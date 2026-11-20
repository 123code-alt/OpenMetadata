/* Licensed under the Apache License, Version 2.0. See LICENSE. */
import {
  Button,
  Card,
  Dialog,
  Modal,
  ModalOverlay,
} from '@openmetadata/ui-core-components';
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from 'react';
import { useTranslation } from 'react-i18next';
import { all } from './api';
import type { EntityType, Session } from './types';

export const SessionContext = createContext<Session | null>(null);
export const ErrorContext = createContext<(error: unknown) => void>(() => {});
export const useError = () => useContext(ErrorContext);
export const useSession = () => {
  const session = useContext(SessionContext);
  if (!session) {
    throw new Error('Session unavailable');
  }
  return session;
};

export function useCollection<T>(type: EntityType, query = '') {
  const [data, setData] = useState<T[]>([]);
  const [loading, setLoading] = useState(true);
  const [generation, setGeneration] = useState(0);
  const onError = useError();
  useEffect(() => {
    let active = true;
    setLoading(true);
    all<T>(type, query)
      .then((items) => {
        if (active) {
          setData(items);
        }
      })
      .catch((error: unknown) => {
        if (active) {
          onError(error);
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, [type, query, generation, onError]);
  const reload = useCallback(() => {
    setGeneration((value) => value + 1);
  }, []);
  return { data, loading, reload };
}

export function Surface({
  children,
  className = '',
}: {
  children: ReactNode;
  className?: string;
}) {
  return <Card className={`tw:p-5 ${className}`}>{children}</Card>;
}

export function EditorDialog({
  title,
  children,
  onClose,
  onSave,
  saving,
  error,
}: {
  title: string;
  children: ReactNode;
  onClose: () => void;
  onSave?: () => void;
  saving: boolean;
  error?: string;
}) {
  const { t } = useTranslation('standalone');
  return (
    <ModalOverlay
      isOpen
      onOpenChange={(open) => {
        if (!open && !saving) {
          onClose();
        }
      }}>
      <Modal className="tw:max-w-xl">
        <Dialog aria-label={title}>
          <Dialog.Header title={title} />
          <Dialog.Content>
            {error && (
              <p className="tw:text-error-primary" role="alert">
                {error}
              </p>
            )}
            {children}
          </Dialog.Content>
          <Dialog.Footer>
            <Button color="secondary" isDisabled={saving} onClick={onClose}>
              {t(onSave ? 'cancel' : 'close')}
            </Button>
            {onSave && (
              <Button
                data-testid="save-entity"
                isLoading={saving}
                onClick={onSave}>
                {t('save')}
              </Button>
            )}
          </Dialog.Footer>
        </Dialog>
      </Modal>
    </ModalOverlay>
  );
}

export function EmptyState() {
  const { t } = useTranslation('standalone');
  return (
    <p className="tw:py-12 tw:text-center tw:text-quaternary">{t('empty')}</p>
  );
}

export function Heading({
  title,
  subtitle,
  actions,
}: {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
}) {
  return (
    <header className="tw:mb-5 tw:flex tw:flex-wrap tw:items-center tw:justify-between tw:gap-4">
      <div>
        <h1 className="tw:text-display-xs tw:font-semibold tw:text-primary">
          {title}
        </h1>
        {subtitle && (
          <p className="tw:mt-1 tw:text-sm tw:text-tertiary">{subtitle}</p>
        )}
      </div>
      {actions}
    </header>
  );
}
