/*
 *  Copyright 2022 Collate.
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
import type { GlossaryTerm } from '../generated/entity/data/glossaryTerm';

export const buildTree = (
  data: GlossaryTerm[],
  // FQN of the container the flat list was fetched under (the glossary or the
  // term being viewed). Its direct children reference it as their parent but it
  // is not itself part of the data, so they must be treated as genuine roots
  // rather than orphans of a not-yet-loaded page.
  rootParentFqn?: string
): GlossaryTerm[] => {
  const nodes: Record<string, GlossaryTerm> = {};

  data.forEach((obj) => {
    nodes[obj.fullyQualifiedName ?? ''] = {
      ...obj,
      children: obj.children?.length ? [] : undefined,
    };
  });

  const tree: GlossaryTerm[] = [];
  data.forEach((obj) => {
    const current = nodes[obj.fullyQualifiedName ?? ''];
    const parentFqn = obj.parent?.fullyQualifiedName;
    const parentNode = parentFqn ? nodes[parentFqn] : undefined;

    if (parentNode) {
      // Push the live node (mutated in place), not a shallow copy: children can
      // be attached to this node on a later page, and a snapshot would keep the
      // node's original (possibly undefined) children array, orphaning any
      // grandchildren added afterwards.
      (parentNode.children ??= []).push(
        Object.assign(current, { type: 'glossaryTerm' })
      );

      return;
    }

    // A term that references a parent term absent from this data set is an
    // orphan of a not-yet-loaded page (progressive expand-all paginates all
    // levels by name, so a descendant can arrive before its parent). Hold it
    // back instead of promoting it to a spurious root and corrupting the
    // hierarchy; it attaches once its parent's page loads. Gate on the parent
    // FQN's presence — not a populated parent.type, which the API may omit —
    // and treat as genuine roots the terms whose parent is the glossary itself
    // or the view's root container (its direct children are never in the data).
    const isOrphanOfUnloadedParent =
      Boolean(parentFqn) &&
      obj.parent?.type !== 'glossary' &&
      parentFqn !== rootParentFqn;

    if (!isOrphanOfUnloadedParent) {
      tree.push(current);
    }
  });

  return tree;
};
