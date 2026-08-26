# Temple App Tables For Neon

This document summarizes the tables currently used by the app, based on the SQLite schema and repository code in this project.

## Recommended Tables To Move To Neon First

These are the tables that matter most for multi-device sync:

- `light_members`
- `donations`
- `sync_state`

If you want to sync more of the app later, add the other tables listed below.

## Core Sync Tables

### `light_members`

Purpose:

- Main member / household records.

Important columns:

- `id`
- `name`
- `phone`
- `city`
- `dist`
- `address`
- `zip_code`
- `birth_date`
- `lunar_birth_date`
- `age`
- `zodiac`
- `zodiac_year`
- `birth_time`
- `note`
- `contact_person`
- `id_number`
- `sort_order`
- `ding`
- `kou`
- `is_mail`
- `gender`
- `is_deleted`
- `uuid`
- `updated_at`
- `deleted_at`
- `version`
- `device_id`
- `sync_status`

### `donations`

Purpose:

- Donation / receipt records linked to `light_members`.

Important columns:

- `id`
- `member_id`
- `receipt_no`
- `donate_date`
- `extra_no`
- `amount`
- `summary`
- `donate_note`
- `other_note`
- `donor_no`
- `light_no`
- `should_pay`
- `donate_type`
- `creator`
- `is_deleted`
- `uuid`
- `updated_at`
- `deleted_at`
- `version`
- `device_id`
- `sync_status`

Foreign key:

- `member_id -> light_members.id`

### `sync_state`

Purpose:

- Store local sync progress for the desktop app.

Important columns:

- `id`
- `last_pull_at`
- `last_push_at`
- `last_sync_at`
- `last_sync_token`
- `device_id`
- `conflict_policy`
- `remote_base_url`
- `updated_at`

## Authentication And Permission Tables

These tables are used for login and system permissions.

### `app_roles`

- `role_code`
- `role_name`

### `app_functions`

- `function_code`
- `function_name`
- `enabled`

### `role_functions`

- `role_code`
- `function_code`

### `app_users`

- `id`
- `username`
- `display_name`
- `password_hash`
- `role_code`
- `enabled`
- `created_by`
- `created_at`
- `updated_by`
- `updated_at`

### `app_user_audits`

- `id`
- `user_id`
- `action`
- `changed_by`
- `changed_at`
- `snapshot`

### `login_records`

- `id`
- `user_id`
- `username`
- `display_name`
- `role_code`
- `computer_name`
- `login_date`
- `login_time`
- `logout_date`
- `logout_time`
- `status`
- `created_at`

## Report And Reference Tables

These tables support reporting, classification, and user-facing maintenance screens.

### `dictionary_categories`

- `id`
- `code`
- `name`
- `type`
- `enabled`
- `sort_order`
- `created_by`
- `created_at`
- `updated_by`
- `updated_at`

### `dictionary_items`

- `id`
- `category_id`
- `parent_item_id`
- `code`
- `name`
- `description`
- `amount`
- `direction`
- `default_amount`
- `enabled`
- `sort_order`
- `source_table`
- `source_id`
- `created_by`
- `created_at`
- `updated_by`
- `updated_at`

### `dictionary_audits`

- `id`
- `target_table`
- `target_id`
- `action`
- `old_value`
- `new_value`
- `changed_by`
- `changed_at`

### `donation_supplements`

- `id`
- `donation_id`
- `supplement_date`
- `supplement_no`
- `source_type`
- `created_by`
- `created_at`
- `updated_by`
- `updated_at`
- `is_deleted`

### `donation_supplement_audits`

- `id`
- `supplement_id`
- `donation_id`
- `action`
- `changed_by`
- `changed_at`
- `snapshot`

### `light_numbers`

- `id`
- `member_id`
- `light_type`
- `light_date`
- `amount`
- `creator`
- `is_deleted`

### `light_number_audits`

- `id`
- `light_number_id`
- `member_id`
- `action`
- `changed_by`
- `changed_at`
- `snapshot`

### `household_light_records`

- `id`
- `member_id`
- `record_type`
- `record_date`
- `amount`
- `created_by`
- `created_at`
- `updated_by`
- `updated_at`
- `is_deleted`

### `household_light_audits`

- `id`
- `record_id`
- `member_id`
- `action`
- `changed_by`
- `changed_at`
- `snapshot`

### `merit_categories`

- `id`
- `code`
- `name`
- `is_delete`
- `created_by`
- `created_at`
- `updated_by`
- `updated_at`

### `merit_category_audits`

- `id`
- `category_id`
- `action`
- `code`
- `name`
- `changed_by`
- `changed_at`

### `merit_box_openings`

- `id`
- `open_date`
- `opened_by`
- `amount`
- `note`
- `created_at`

### `system_settings`

- `id`
- `setting_group`
- `setting_key`
- `setting_value`
- `updated_by`
- `updated_at`

### `system_settings_audits`

- `id`
- `setting_group`
- `setting_key`
- `old_value`
- `new_value`
- `changed_by`
- `changed_at`

### `address_villages`

- `id`
- `county`
- `district`
- `village`
- `code`

### `address_roads`

- `id`
- `county`
- `district`
- `road`
- `code`

### `address_presets`

- `id`
- `name`
- `county`
- `district`
- `road`
- `note`

### `custom_chars`

- `id`
- `char_name`
- `char_value`
- `created_at`

## Notes For Neon

- If you only want the minimum tables for syncing, create `light_members`, `donations`, and `sync_state` first.
- The app currently uses many SQLite-only support tables for reports and management screens.
- Some tables, like `app_users`, `role_functions`, and `system_settings`, are useful if you want the whole app state centralized later.
- If you do not plan to sync a table, you can keep it local for now.

## Suggested Next Step

If you want, I can turn this into:

- a PostgreSQL `schema.sql` for Neon
- or a staged migration plan starting with the three core sync tables
