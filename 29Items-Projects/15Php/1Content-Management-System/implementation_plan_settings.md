# Implementation Plan: Dynamic Site Configuration

## Objective
Enable the site title (and potentially other settings) to be configurable directly from the Admin Panel, without editing the `.env` file. These settings should be stored in the database and accessible via an API.

## 1. Database Schema
Create a `settings` table to store key-value pairs.

```php
Schema::create('settings', function (Blueprint $table) {
    $table->id();
    $table->string('key')->unique();
    $table->text('value')->nullable();
    $table->timestamps();
});
```

## 2. Backend Implementation
*   **Model**: Create `App\Models\Setting`.
*   **Seeder**: Seed initial default values (e.g., `site_name` = 'InSight CMS').
*   **Controller**: Create `App\Http\Controllers\Api\SettingController`.
    *   `index()`: Returns all settings (publicly accessible or specific keys for public).
    *   `update()`: Updates a setting (Admin only).
*   **Routes**: Add API routes for fetching and updating settings.

## 3. Frontend Integration
### Public Side
*   **Context/Hook**: Create a global `SettingsContext` or hook to fetch settings on app load.
*   **PublicLayout**: Consume the `site_name` setting to display the dynamic title.

### Admin Side
*   **Settings Page**: Create a new page at `/admin/settings` (currently a placeholder).
*   **Form**: A form to edit the `site_name`.

## 4. Execution Steps
1.  **Migration**: Create the `settings` table.
2.  **API**: Implement `SettingController` and routes.
3.  **Admin UI**: Implement the Settings page in React.
4.  **Public UI**: Update `PublicLayout` to fetch the title from the API instead of `window.AppConfig`.

## 5. Security Note
*   The `update` endpoint must be protected (authentication required).
*   The `index` endpoint might need to filter sensitive keys if we store secrets there later. For now, we only store public config.

Please approve this plan to proceed.
