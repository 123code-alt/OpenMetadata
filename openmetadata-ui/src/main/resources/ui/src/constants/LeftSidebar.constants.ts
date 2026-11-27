/*
 *  Copyright 2023 Collate.
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import { ReactComponent as GovernIcon } from '../assets/svg/bank.svg';
import { ReactComponent as ClassificationIcon } from '../assets/svg/classification.svg';
import { ReactComponent as MemoriesIcon } from '../assets/svg/common/memories.svg';
import { ReactComponent as KnowledgeCenterIcon } from '../assets/svg/context-center.svg';
import { ReactComponent as GlossaryIcon } from '../assets/svg/glossary.svg';
import { ReactComponent as LineageIcon } from '../assets/svg/ic-lineage.svg';
import { ReactComponent as SettingsIcon } from '../assets/svg/ic-settings-v1.svg';
import { ReactComponent as LogoutIcon } from '../assets/svg/logout.svg';
import { ReactComponent as ArchiveIcon } from '../assets/svg/sidebar-icons/archive.svg';
import { ReactComponent as ArticlesIcon } from '../assets/svg/sidebar-icons/articles.svg';
import { ReactComponent as DashboardIcon } from '../assets/svg/sidebar-icons/dashboard.svg';
import { ReactComponent as DocumentsIcon } from '../assets/svg/sidebar-icons/documents.svg';
import { LeftSidebarItem } from '../components/MyData/LeftSidebar/LeftSidebar.interface';
import { SidebarItem } from '../enums/sidebar.enum';
import { ENTITY_PATH, ROUTES } from './constants';

export const SIDEBAR_NESTED_KEYS = {
  [ROUTES.OBSERVABILITY_ALERTS]: ROUTES.OBSERVABILITY_ALERTS,
  [ROUTES.ONTOLOGY_EXPLORER]: ROUTES.ONTOLOGY_EXPLORER,
  [ROUTES.CONTEXT_CENTER_OVERVIEW]: ROUTES.CONTEXT_CENTER,
  [ROUTES.CONTEXT_CENTER_ARTICLES]: ROUTES.CONTEXT_CENTER,
  [ROUTES.CONTEXT_CENTER_DOCUMENTS]: ROUTES.CONTEXT_CENTER,
  [ROUTES.CONTEXT_CENTER_MEMORIES]: ROUTES.CONTEXT_CENTER,
  [ROUTES.CONTEXT_CENTER_INTEGRATIONS]: ROUTES.CONTEXT_CENTER,
  [ROUTES.CONTEXT_CENTER_ARCHIVE]: ROUTES.CONTEXT_CENTER,
};

export const SIDEBAR_ENTITY_PATH_ALIASES: Record<string, string> = {
  [`/${ENTITY_PATH.metrics}`]: ROUTES.METRICS,
  [`/${ENTITY_PATH.tags}`]: ROUTES.TAGS,
  '/glossary-term': ROUTES.GLOSSARY,
  '/observability/alert': ROUTES.OBSERVABILITY_ALERTS,
  '/test-case': ROUTES.INCIDENT_MANAGER,
};

export const SIDEBAR_LIST: Array<LeftSidebarItem> = [
  {
    key: 'governance',
    title: 'label.govern',
    icon: GovernIcon,
    dataTestId: SidebarItem.GOVERNANCE,
    children: [
      {
        key: ROUTES.GLOSSARY,
        title: 'label.glossary',
        redirect_url: ROUTES.GLOSSARY,
        icon: GlossaryIcon,
        dataTestId: `app-bar-item-${SidebarItem.GLOSSARY}`,
      },
      {
        key: ROUTES.ONTOLOGY_EXPLORER,
        title: 'label.ontology-explorer',
        redirect_url: ROUTES.ONTOLOGY_EXPLORER,
        icon: LineageIcon,
        dataTestId: `app-bar-item-${SidebarItem.ONTOLOGY_EXPLORER}`,
      },
      {
        key: ROUTES.TAGS,
        title: 'label.classification',
        redirect_url: ROUTES.TAGS,
        icon: ClassificationIcon,
        dataTestId: `app-bar-item-${SidebarItem.TAGS}`,
      },
    ],
  },
  {
    key: ROUTES.CONTEXT_CENTER,
    title: 'label.context-center',
    icon: KnowledgeCenterIcon,
    dataTestId: SidebarItem.CONTEXT_CENTER,
    children: [
      {
        key: ROUTES.CONTEXT_CENTER_OVERVIEW,
        title: 'label.overview',
        redirect_url: ROUTES.CONTEXT_CENTER_OVERVIEW,
        icon: DashboardIcon,
        dataTestId: `app-bar-item-${SidebarItem.OVERVIEW}`,
      },
      {
        key: ROUTES.CONTEXT_CENTER_ARTICLES,
        title: 'label.article-plural',
        redirect_url: ROUTES.CONTEXT_CENTER_ARTICLES,
        icon: ArticlesIcon,
        dataTestId: `app-bar-item-${SidebarItem.ARTICLES}`,
      },
      {
        key: ROUTES.CONTEXT_CENTER_DOCUMENTS,
        title: 'label.document-plural',
        redirect_url: ROUTES.CONTEXT_CENTER_DOCUMENTS,
        icon: DocumentsIcon,
        dataTestId: `app-bar-item-${SidebarItem.DOCUMENTS}`,
      },
      {
        key: ROUTES.CONTEXT_CENTER_MEMORIES,
        title: 'label.memory-plural',
        redirect_url: ROUTES.CONTEXT_CENTER_MEMORIES,
        icon: MemoriesIcon,
        dataTestId: `app-bar-item-${SidebarItem.MEMORIES}`,
      },
      {
        key: ROUTES.CONTEXT_CENTER_ARCHIVE,
        title: 'label.archive',
        redirect_url: ROUTES.CONTEXT_CENTER_ARCHIVE,
        icon: ArchiveIcon,
        dataTestId: `app-bar-item-context-center-archive`,
      },
    ],
  },
];

export const SETTING_ITEM = {
  key: ROUTES.SETTINGS,
  title: 'label.setting-plural',
  redirect_url: ROUTES.SETTINGS,
  icon: SettingsIcon,
  dataTestId: `app-bar-item-${SidebarItem.SETTINGS}`,
};

export const LOGOUT_ITEM = {
  key: SidebarItem.LOGOUT,
  title: 'label.logout',
  icon: LogoutIcon,
  dataTestId: `app-bar-item-${SidebarItem.LOGOUT}`,
};
