/* Licensed under the Apache License, Version 2.0. See LICENSE. */
import {
  expect,
  test,
  type APIRequestContext,
  type Page,
} from '@playwright/test';
import { mkdir } from 'node:fs/promises';
import path from 'node:path';

interface Entity {
  id: string;
  name: string;
  revision: number;
  fullyQualifiedName: string;
  description: string;
}
const cleanup: { collection: string; id: string }[] = [];

test.afterEach(async ({ request }) => {
  for (const item of cleanup.splice(0).reverse()) {
    const response = await request.get(`/api/v1/${item.collection}/${item.id}`);
    if (response.ok()) {
      const current = (await response.json()) as Entity;
      await request.delete(
        `/api/v1/${item.collection}/${item.id}?revision=${current.revision}&recursive=true`
      );
    }
  }
});

async function captured(page: Page, name: string) {
  const directory = process.env.SCM_CAPTURE_DIR;
  if (directory) {
    await mkdir(directory, { recursive: true });
    await page.screenshot({ path: path.join(directory, name), fullPage: true });
  }
}

async function saveAndRead(page: Page, collection: string): Promise<Entity> {
  const response = page.waitForResponse(
    (response) =>
      response.url().endsWith(`/api/v1/${collection}`) &&
      response.request().method() === 'POST'
  );
  await page.getByTestId('save-entity').click();
  const saved = await response;
  expect(saved.status()).toBe(201);
  return saved.json() as Promise<Entity>;
}

async function post(
  request: APIRequestContext,
  collection: string,
  command: unknown
): Promise<Entity> {
  const response = await request.post(`/api/v1/${collection}`, {
    data: command,
  });
  expect(response.status()).toBe(201);
  const entity = (await response.json()) as Entity;
  cleanup.push({ collection, id: entity.id });
  return entity;
}

test('creates glossary, edits upstream synonym/reference widgets, and packs real context', async ({
  page,
  request,
}) => {
  const unique = String(Date.now());
  await page.goto('/#/glossary');
  await page.getByTestId('new-glossary').click();
  let dialog = page.getByRole('dialog');
  await dialog.getByLabel(/^名称/).fill(`业务指标_${unique}`);
  await dialog.getByLabel(/^显示名称/).fill('企业业务指标');
  await dialog
    .getByLabel(/^描述/)
    .fill('统一企业业务指标的定义、别名与统计口径。');
  const glossary = await saveAndRead(page, 'glossaries');
  cleanup.push({ collection: 'glossaries', id: glossary.id });
  await expect(page.getByRole('dialog')).toHaveCount(0);

  await page.getByTestId('new-term').click();
  dialog = page.getByRole('dialog');
  await dialog.getByLabel(/^名称/).fill('净收入');
  await dialog
    .getByLabel(/^描述/)
    .fill('已确认收入扣除已确认退款；按原订单所属月份统计。');
  const term = await saveAndRead(page, 'glossaryTerms');
  await expect(page.getByTestId('term-detail')).toBeVisible();
  await page.getByTestId('synonym-add-button').click();
  await page.locator('#synonyms-select').fill('净营收');
  await page.locator('#synonyms-select').press('Enter');
  await page.getByTestId('save-synonym-btn').click();
  await expect(
    page.getByTestId('term-detail').getByText('净营收', { exact: true })
  ).toBeVisible();

  const references = page
    .locator('section')
    .filter({
      has: page.getByRole('heading', { name: '外部引用', exact: true }),
    });
  await references.getByRole('button', { name: '编辑', exact: true }).click();
  const referenceModal = page.getByTestId('glossary-term-references-modal');
  await referenceModal.locator('input').nth(0).fill('企业指标口径');
  await referenceModal
    .locator('input')
    .nth(1)
    .fill('https://example.com/revenue');
  await referenceModal.getByTestId('save-btn').click();
  await expect(
    page.getByRole('link', { name: '企业指标口径', exact: true })
  ).toBeVisible();
  await captured(page, 'glossary.png');

  await page.getByRole('link', { name: '知识文章', exact: true }).click();
  await page.getByTestId('new-article').click();
  dialog = page.getByRole('dialog');
  await dialog.getByLabel(/^名称/).fill(`收入规则_${unique}`);
  await dialog.getByLabel(/^显示名称/).fill('收入统计规则');
  await dialog
    .getByLabel(/^正文/)
    .fill('统计收入时保留原订单月份，并扣除已确认退款。');
  const article = await saveAndRead(page, 'pages');
  cleanup.push({ collection: 'pages', id: article.id });

  await page.getByRole('link', { name: '记忆', exact: true }).click();
  await page.getByTestId('new-memory').click();
  dialog = page.getByRole('dialog');
  await dialog.getByLabel(/^标题/).fill('收入统计约定');
  await dialog.getByLabel(/^问题或指令/).fill('如何统计净收入？');
  await dialog
    .getByLabel(/^答案或指导/)
    .fill('按原订单月份核算，并扣除已确认退款。');
  await dialog
    .getByRole('combobox', { name: '来源文章或文档', exact: true })
    .selectOption(`page:${article.id}`);
  const memory = await saveAndRead(page, 'contextCenter/memories');
  cleanup.push({ collection: 'contextCenter/memories', id: memory.id });
  await expect(page.getByText('收入统计约定', { exact: true })).toBeVisible();
  await captured(page, 'memories.png');

  await page.getByRole('link', { name: '文档', exact: true }).click();
  const uploaded = page.waitForResponse((response) =>
    response.url().endsWith('/api/v1/contextCenter/drive/files/upload')
  );
  await page
    .getByTestId('file-input')
    .setInputFiles({
      name: `收入口径_${unique}.md`,
      mimeType: 'text/markdown',
      buffer: Buffer.from('收入核算说明：退款沿用原订单月份。', 'utf8'),
    });
  const upload = await uploaded;
  expect(upload.status()).toBe(201);
  const file = (await upload.json()) as Entity;
  cleanup.push({ collection: 'contextCenter/drive/files', id: file.id });
  await expect(page.getByTestId('document-preview')).toContainText(
    '收入核算说明'
  );

  await page.getByRole('link', { name: 'AI 上下文预览', exact: true }).click();
  for (const entity of [term, article, memory, file]) {
    await page.getByTestId(`select-source-${entity.id}`).check();
  }
  await page.getByTestId('pack-context').click();
  await expect(page.getByTestId('context-preview')).toContainText('净收入');
  await expect(page.getByTestId('context-preview')).toContainText(
    '收入核算说明'
  );
  await expect(page.getByTestId('context-preview')).toContainText(
    '收入统计约定'
  );
  await captured(page, 'context-preview.png');

  await page.goto('/#/glossary');
  await page.reload();
  await page.getByTestId(`glossary-${glossary.id}`).click();
  await page.getByRole('button', { name: '净收入', exact: true }).click();
  await expect(
    page.getByTestId('term-detail').getByText('净营收', { exact: true })
  ).toBeVisible();
  const stored = await request.get(`/api/v1/glossaryTerms/${term.id}`);
  expect((await stored.json()).references[0].endpoint).toBe(
    'https://example.com/revenue'
  );
});

test('keeps the editor open when another request changes the revision', async ({
  page,
  request,
}) => {
  const glossary = await post(request, 'glossaries', {
    name: `concurrent_${Date.now()}`,
    description: 'concurrency behavior',
  });
  const term = await post(request, 'glossaryTerms', {
    name: '共享更新术语',
    description: 'original definition',
    glossary: glossary.fullyQualifiedName,
  });
  await page.goto('/#/glossary');
  await page.getByTestId(`glossary-${glossary.id}`).click();
  await page.getByRole('button', { name: '共享更新术语', exact: true }).click();
  await page
    .getByTestId('term-detail')
    .getByRole('button', { name: '编辑', exact: true })
    .first()
    .click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel(/^描述/).fill('browser attempted update');
  const changed = await request.put(
    `/api/v1/glossaryTerms/${term.id}?revision=${term.revision}`,
    {
      data: {
        name: term.name,
        description: 'accepted server update',
        glossary: glossary.fullyQualifiedName,
      },
    }
  );
  expect(changed.status()).toBe(200);
  await page.getByTestId('save-entity').click();
  await expect(dialog.getByRole('alert')).toContainText('Entity changed');
  await expect(dialog).toBeVisible();
  const current = await request.get(`/api/v1/glossaryTerms/${term.id}`);
  expect((await current.json()).description).toBe('accepted server update');
});

test('rejects an invalid bearer token and reconnects through loopback development mode', async ({
  page,
}) => {
  await page.addInitScript(() =>
    sessionStorage.setItem('semantic-context-token', 'invalid-fixture-token')
  );
  await page.goto('/');
  await expect(page.getByLabel(/^API 令牌/)).toBeVisible();
  await expect(page.getByRole('alert')).toContainText('API token');
  await page.getByRole('button', { name: '连接', exact: true }).click();
  await expect(
    page.getByRole('link', { name: '术语库', exact: true })
  ).toBeVisible();
});
