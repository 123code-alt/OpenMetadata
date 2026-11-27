/*
 *  Copyright 2025 Collate.
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

import { ComponentType, FC, lazy, LazyExoticComponent, Suspense } from 'react';
import Loader from '../components/common/Loader/Loader';
import { EntityType } from '../enums/entity.enum';

const lazyComponentMap: Partial<
  Record<EntityType, LazyExoticComponent<ComponentType>>
> = {
  [EntityType.DIRECTORY]: lazy(
    () => import('../pages/DirectoryDetailsPage/DirectoryDetailsPage')
  ),
  [EntityType.FILE]: lazy(
    () => import('../pages/FileDetailsPage/FileDetailsPage')
  ),
  [EntityType.SPREADSHEET]: lazy(
    () => import('../pages/SpreadsheetDetailsPage/SpreadsheetDetailsPage')
  ),
  [EntityType.WORKSHEET]: lazy(
    () => import('../pages/WorksheetDetailsPage/WorksheetDetailsPage')
  ),
};

export function getEntityDetailComponent(entityType: string): FC | null {
  const LazyComponent = lazyComponentMap[entityType as EntityType];

  if (!LazyComponent) {
    return null;
  }

  const WrappedComponent: FC = (props) => (
    <Suspense fallback={<Loader fullScreen />}>
      <LazyComponent {...props} />
    </Suspense>
  );

  return WrappedComponent;
}
