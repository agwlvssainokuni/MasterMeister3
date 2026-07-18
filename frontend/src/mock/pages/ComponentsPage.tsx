/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { useState } from "react";
import { useTranslation } from "react-i18next";
import {
  Alert,
  Badge,
  Button,
  Card,
  Checkbox,
  CodeBlock,
  ConfirmDialog,
  Dropdown,
  EmptyState,
  FormField,
  IconButton,
  KeyValueList,
  Modal,
  Pagination,
  PasswordInput,
  RadioGroup,
  SearchInput,
  Select,
  Spinner,
  Switch,
  Tabs,
  TextArea,
  TextInput,
  Tooltip,
  useToast,
} from "../../design-system/components";
import styles from "../MockCatalog.module.css";

export function ComponentsPage() {
  const { t } = useTranslation("mock");
  const { showToast } = useToast();
  const [modalOpen, setModalOpen] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [radio, setRadio] = useState("read");
  const [switchOn, setSwitchOn] = useState(true);
  const [tab, setTab] = useState("first");
  const [page, setPage] = useState(2);

  return (
    <div>
      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Button / IconButton</h2>
        <div className={styles.row}>
          <Button variant="primary">Primary</Button>
          <Button variant="secondary">Secondary</Button>
          <Button variant="danger">Danger</Button>
          <Button variant="ghost">Ghost</Button>
          <Button variant="primary" size="sm">
            Small
          </Button>
          <Button variant="primary" loading>
            Loading
          </Button>
          <Button variant="primary" disabled>
            Disabled
          </Button>
          <IconButton aria-label="edit">✎</IconButton>
          <IconButton aria-label="delete" variant="danger">
            🗑
          </IconButton>
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Inputs</h2>
        <div className={styles.row} style={{ maxWidth: 720 }}>
          <FormField label="TextInput" help="ヘルプテキスト / help text">
            <TextInput placeholder="入力してください" />
          </FormField>
          <FormField label="Invalid" error="入力内容に誤りがあります">
            <TextInput defaultValue="invalid value" />
          </FormField>
          <FormField label="Disabled">
            <TextInput disabled defaultValue="disabled" />
          </FormField>
        </div>
        <div className={styles.row} style={{ maxWidth: 720 }}>
          <FormField label="PasswordInput" required>
            <PasswordInput defaultValue="secret" />
          </FormField>
          <FormField label="Select">
            <Select defaultValue="mysql">
              <option value="mysql">MySQL</option>
              <option value="mariadb">MariaDB</option>
              <option value="postgres">PostgreSQL</option>
            </Select>
          </FormField>
          <FormField label="SearchInput">
            <SearchInput placeholder="検索..." />
          </FormField>
        </div>
        <div className={styles.row} style={{ maxWidth: 720 }}>
          <FormField label="TextArea">
            <TextArea placeholder="複数行の入力" />
          </FormField>
        </div>
        <div className={styles.row}>
          <Checkbox label="Checkbox" defaultChecked />
          <Checkbox label="Disabled" disabled />
          <RadioGroup
            name="perm"
            value={radio}
            onChange={setRadio}
            options={[
              { value: "read", label: "READ" },
              { value: "update", label: "UPDATE" },
              { value: "none", label: "NONE", disabled: true },
            ]}
          />
          <Switch checked={switchOn} onChange={setSwitchOn} label="Switch" />
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Badge / Alert / Spinner</h2>
        <div className={styles.row}>
          <Badge>neutral</Badge>
          <Badge tone="primary">primary</Badge>
          <Badge tone="success">有効</Badge>
          <Badge tone="warning">承認待ち</Badge>
          <Badge tone="danger">無効</Badge>
          <Spinner size="sm" />
          <Spinner />
          <Spinner size="lg" />
        </div>
        <div className={styles.row} style={{ flexDirection: "column", alignItems: "stretch" }}>
          <Alert tone="info">情報: スキーマ取込は数分かかることがあります。</Alert>
          <Alert tone="success">成功: 変更を反映しました。</Alert>
          <Alert tone="warning">警告: このテーブルには主キーがありません。</Alert>
          <Alert tone="danger">エラー: 接続に失敗しました。設定を確認してください。</Alert>
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Card / KeyValueList / EmptyState / CodeBlock</h2>
        <div className={styles.row} style={{ alignItems: "flex-start" }}>
          <div style={{ width: 360 }}>
            <Card title="接続情報">
              <KeyValueList
                items={[
                  { key: "ホスト", value: "db.example.local" },
                  { key: "ポート", value: "3306" },
                  { key: "状態", value: <Badge tone="success">接続OK</Badge> },
                ]}
              />
            </Card>
          </div>
          <div style={{ width: 360 }}>
            <Card title="EmptyState">
              <EmptyState action={<Button size="sm">{`再読み込み`}</Button>} />
            </Card>
          </div>
        </div>
        <div style={{ maxWidth: 720 }}>
          <CodeBlock
            code={`SELECT c.id, c.name, c.email\nFROM customers c\nWHERE c.status = :status\nORDER BY c.id`}
          />
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Modal / ConfirmDialog / Toast</h2>
        <div className={styles.row}>
          <Button onClick={() => setModalOpen(true)}>Modal を開く</Button>
          <Button variant="danger" onClick={() => setConfirmOpen(true)}>
            ConfirmDialog(danger)
          </Button>
          <Button onClick={() => showToast("success", "保存しました")}>Toast: success</Button>
          <Button onClick={() => showToast("info", "処理を開始しました")}>Toast: info</Button>
          <Button onClick={() => showToast("warning", "セッションがまもなく切れます")}>
            Toast: warning
          </Button>
          <Button onClick={() => showToast("danger", "反映に失敗しました")}>Toast: danger</Button>
        </div>
        <Modal
          open={modalOpen}
          title="モーダルタイトル"
          onClose={() => setModalOpen(false)}
          footer={
            <>
              <Button onClick={() => setModalOpen(false)}>キャンセル</Button>
              <Button variant="primary" onClick={() => setModalOpen(false)}>
                保存
              </Button>
            </>
          }
        >
          <FormField label="表示名">
            <TextInput defaultValue="管理者" />
          </FormField>
        </Modal>
        <ConfirmDialog
          open={confirmOpen}
          title="削除の確認"
          message="この操作は取り消せません。本当に削除しますか?"
          tone="danger"
          confirmLabel="削除"
          onConfirm={() => {
            setConfirmOpen(false);
            showToast("success", "削除しました");
          }}
          onCancel={() => setConfirmOpen(false)}
        />
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Dropdown / Tooltip / Tabs / Pagination</h2>
        <div className={styles.row}>
          <Dropdown
            trigger="操作 ▾"
            items={[
              { key: "edit", label: "編集", onSelect: () => showToast("info", "編集を選択") },
              {
                key: "delete",
                label: "削除",
                danger: true,
                onSelect: () => showToast("danger", "削除を選択"),
              },
            ]}
          />
          <Tooltip content="ツールチップの説明文">
            <Button variant="ghost">hover / focus me</Button>
          </Tooltip>
          <Pagination page={page} totalPages={12} onChange={setPage} />
        </div>
        <div style={{ maxWidth: 560 }}>
          <Tabs
            activeKey={tab}
            onChange={setTab}
            items={[
              { key: "first", label: t("query.tabBuilder"), content: <p>タブ 1 の内容</p> },
              { key: "second", label: t("query.tabSql"), content: <p>タブ 2 の内容</p> },
            ]}
          />
        </div>
      </section>
    </div>
  );
}
