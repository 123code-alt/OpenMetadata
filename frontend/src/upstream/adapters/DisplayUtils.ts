/* Licensed under the Apache License, Version 2.0. See LICENSE. */
export function getEntityName(entity: {
  displayName?: string;
  name?: string;
}): string {
  return entity.displayName || entity.name || '';
}
export function stripMarkdown(value: string): string {
  return value.replace(/<[^>]*>/g, '').replace(/[#*_`]/g, '');
}
export function getShortRelativeTime(timestamp: number): string {
  return new Intl.DateTimeFormat(navigator.language, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(timestamp);
}
