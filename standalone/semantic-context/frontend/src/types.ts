/* Licensed under the Apache License, Version 2.0. See LICENSE. */
import type { Glossary as UpstreamGlossary } from './upstream/generated/entity/data/glossary';
import type { GlossaryTerm as UpstreamTerm } from './upstream/generated/entity/data/glossaryTerm';
import type { ContextMemory as UpstreamMemory } from './upstream/generated/entity/context/contextMemory';
import type { Page as UpstreamPage } from './upstream/generated/entity/data/page';
import type { ContextFile as UpstreamFile } from './upstream/generated/entity/data/contextFile';

export type Glossary = UpstreamGlossary & { revision: number };
export type Term = UpstreamTerm & { revision: number };
export type Memory = UpstreamMemory & { revision: number };
export type Article = UpstreamPage & { id: string; revision: number };
export type ContextFile = UpstreamFile & {
  revision: number;
  fileName: string;
  size: number;
  extractedText: string;
  headContentId: string;
};
export type Entity = Glossary | Term | Memory | Article | ContextFile;
export type EntityType =
  | 'glossary'
  | 'glossaryTerm'
  | 'page'
  | 'contextMemory'
  | 'contextFile';
export interface SourceReference {
  id: string;
  type: EntityType;
}
export interface PackedContext {
  markdown: string;
  sources: (SourceReference & {
    revision: number;
    fullyQualifiedName: string;
    displayName?: string;
    contentId?: string;
  })[];
  omitted: (SourceReference & { reason: string })[];
  maxChars: number;
  usedChars: number;
}
export interface Session {
  id: string;
  name: string;
  isAdmin: boolean;
  scopes: string[];
}
export interface Principal {
  id: string;
  name: string;
  type: 'user';
}
export interface PageResult<T> {
  data: T[];
  paging: { total: number; after?: string };
}

export const paths: Record<EntityType, string> = {
  glossary: 'glossaries',
  glossaryTerm: 'glossaryTerms',
  page: 'pages',
  contextMemory: 'contextCenter/memories',
  contextFile: 'contextCenter/drive/files',
};

export function displayName(entity: {
  displayName?: string;
  name?: string;
}): string {
  return entity.displayName || entity.name || '';
}
