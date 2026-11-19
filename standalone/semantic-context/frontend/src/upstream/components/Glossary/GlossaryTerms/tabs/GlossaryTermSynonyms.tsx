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

import { CheckOutlined, CloseOutlined } from '@ant-design/icons';
import { Button, Select, Space, Typography } from 'antd';
import { cloneDeep, isEmpty, isEqual } from 'lodash';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { NO_DATA_PLACEHOLDER } from '../../../../constants/constants';
import { GlossaryTerm } from '../../../../generated/entity/data/glossaryTerm';
import ExpandableCard from '../../../common/ExpandableCard/ExpandableCard';
import {
  EditIconButton,
  PlusIconButton,
} from '../../../common/IconButtons/EditIconButton';
import TagButton from '../../../common/TagButton/TagButton.component';

interface SynonymsProps {
  glossaryTerm: GlossaryTerm;
  onGlossaryTermUpdate: (value: GlossaryTerm) => Promise<void>;
  onError: (error: unknown) => void;
  canEdit: boolean;
}

const GlossaryTermSynonyms = ({
  glossaryTerm,
  onGlossaryTermUpdate,
  onError,
  canEdit,
}: SynonymsProps) => {
  const [isViewMode, setIsViewMode] = useState<boolean>(true);
  const [synonyms, setSynonyms] = useState<string[]>([]);
  const [saving, setSaving] = useState<boolean>(false);
  const { t } = useTranslation();

  const getSynonyms = () =>
    !canEdit || !isEmpty(synonyms) ? (
      <div className="d-flex flex-wrap">
        {synonyms.map((synonym) => (
          <TagButton
            className="glossary-synonym-tag"
            key={synonym}
            label={synonym}
          />
        ))}

        {!canEdit && synonyms.length === 0 && <div>{NO_DATA_PLACEHOLDER}</div>}
      </div>
    ) : null;

  const handleCancel = () => {
    setSynonyms(glossaryTerm.synonyms || []);
    setIsViewMode(true);
  };

  const handleSynonymsSave = async () => {
    setSaving(true);
    try {
      if (!isEqual(synonyms, glossaryTerm.synonyms)) {
        await onGlossaryTermUpdate({ ...cloneDeep(glossaryTerm), synonyms });
      }
      setIsViewMode(true);
    } catch (error: unknown) {
      onError(error);
    } finally {
      setSaving(false);
    }
  };

  useEffect(() => {
    setSynonyms(
      (glossaryTerm.synonyms ?? []).filter((synonym) => !isEmpty(synonym))
    );
  }, [glossaryTerm]);

  const header = (
    <div className="d-flex items-center gap-2">
      <Typography.Text className="text-sm font-medium">
        {t('label.synonym-plural')}
      </Typography.Text>
      {canEdit &&
        isViewMode &&
        (isEmpty(synonyms) ? (
          <PlusIconButton
            data-testid="synonym-add-button"
            size="small"
            title={t('label.add-entity', {
              entity: t('label.synonym-plural'),
            })}
            onClick={() => {
              setIsViewMode(false);
            }}
          />
        ) : (
          <EditIconButton
            newLook
            data-testid="edit-button"
            size="small"
            title={t('label.edit-entity', {
              entity: t('label.synonym-plural'),
            })}
            onClick={() => setIsViewMode(false)}
          />
        ))}
    </div>
  );

  return (
    <ExpandableCard
      cardProps={{
        title: header,
      }}
      dataTestId="synonyms-container"
      isExpandDisabled={isEmpty(synonyms)}>
      {isViewMode ? (
        getSynonyms()
      ) : (
        <>
          <Space className="justify-end w-full m-b-xs" size={8}>
            <Button
              className="w-6 p-x-05"
              data-testid="cancel-synonym-btn"
              icon={<CloseOutlined size={12} />}
              size="small"
              onClick={handleCancel}
            />
            <Button
              className="w-6 p-x-05"
              data-testid="save-synonym-btn"
              icon={<CheckOutlined size={12} />}
              loading={saving}
              size="small"
              type="primary"
              onClick={handleSynonymsSave}
            />
          </Space>

          <Select
            className="glossary-select w-full"
            id="synonyms-select"
            mode="tags"
            open={false}
            placeholder={t('label.add-entity', {
              entity: t('label.synonym-plural'),
            })}
            value={synonyms}
            onChange={(value) => setSynonyms(value)}
          />
        </>
      )}
    </ExpandableCard>
  );
};

export default GlossaryTermSynonyms;
