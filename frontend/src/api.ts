/* Licensed under the Apache License, Version 2.0. See LICENSE. */
import { paths, type Entity, type EntityType, type PageResult } from './types';

let token = sessionStorage.getItem('semantic-context-token') ?? '';

export function setToken(value: string): void {
  token = value;
  if (value) {
    sessionStorage.setItem('semantic-context-token', value);
  } else {
    sessionStorage.removeItem('semantic-context-token');
  }
}

export class ApiError extends Error {
  constructor(readonly status: number, message: string) {
    super(message);
  }
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`/api/v1/${path}`, {
    ...init,
    headers: {
      ...(init.body instanceof File
        ? {}
        : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init.headers,
    },
  });
  if (!response.ok) {
    const text = await response.text();
    let message = text;
    try {
      const parsed: unknown = JSON.parse(text);
      if (
        typeof parsed === 'object' &&
        parsed !== null &&
        'message' in parsed &&
        typeof parsed.message === 'string'
      ) {
        message = parsed.message;
      }
    } catch {
      /* Servers can return a plain-text error. */
    }
    throw new ApiError(response.status, message || response.statusText);
  }
  return response.json() as Promise<T>;
}

export async function all<T>(type: EntityType, query = ''): Promise<T[]> {
  const collected: T[] = [];
  let after: string | undefined;
  do {
    const params = new URLSearchParams(query);
    params.set('limit', '1000');
    if (after) {
      params.set('after', after);
    }
    const page = await api<PageResult<T>>(
      `${paths[type]}?${params.toString()}`
    );
    collected.push(...page.data);
    after = page.paging.after;
  } while (after);
  return collected;
}

export function create<T>(type: EntityType, command: unknown): Promise<T> {
  return api<T>(paths[type], { method: 'POST', body: JSON.stringify(command) });
}

export function update<T>(
  type: EntityType,
  entity: Entity,
  command: unknown
): Promise<T> {
  return api<T>(`${paths[type]}/${entity.id}?revision=${entity.revision}`, {
    method: 'PUT',
    body: JSON.stringify(command),
  });
}

export function archive(
  type: EntityType,
  entity: Entity,
  recursive = false
): Promise<Entity> {
  return api<Entity>(
    `${paths[type]}/${entity.id}?revision=${entity.revision}&recursive=${recursive}`,
    { method: 'DELETE' }
  );
}

export async function downloadFile(file: Entity): Promise<void> {
  const response = await fetch(
    `/api/v1/${paths.contextFile}/${file.id}/content`,
    {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    }
  );
  if (!response.ok) {
    throw new ApiError(response.status, response.statusText);
  }
  const url = URL.createObjectURL(await response.blob());
  const link = document.createElement('a');
  link.href = url;
  link.download = displayFileName(file);
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function displayFileName(file: Entity): string {
  return file.displayName || file.name || 'document.txt';
}
