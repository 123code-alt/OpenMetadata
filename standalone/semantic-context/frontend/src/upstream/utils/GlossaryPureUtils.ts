/* Licensed under the Apache License, Version 2.0. See LICENSE.
 * Adapted from 2.0.8 GlossaryPureUtils; global i18n replaced by the host instance. */
import i18n from 'i18next';

export function referenceURLValidator(
  _: unknown,
  value: string
): Promise<void> {
  if (!value || value.startsWith('http://') || value.startsWith('https://')) {
    return Promise.resolve();
  }
  return Promise.reject(
    new Error(i18n.t('message.url-must-start-with-http-or-https'))
  );
}
