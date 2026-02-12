# Implementation Plan: Client-Side CMS (Public Frontend)

## Objective
Develop a public-facing "Client CMS" interface that allows visitors to view the published content. This will sit alongside the existing Admin interface.

## 1. Directory Structure
We will create a clear separation between Admin and Public views in the `resources/js` folder:

```
resources/js/
├── Layouts/
│   ├── AdminLayout.jsx   (Existing)
│   └── PublicLayout.jsx  (New - Navbar, Footer for visitors)
└── Pages/
    ├── Dashboard.jsx     (Existing Admin Dashboard)
    ├── Articles/         (Existing Admin Article Management)
    └── Public/           (New Public Pages)
        ├── Home.jsx      (Landing page, showcases latest articles)
        └── Article.jsx   (Single article read-only view)
```

## 2. Routing Strategy
To host both the Admin panel and the Public site, we need to adjust the URL structure.

*   **Current State:**
    *   `/` -> Admin Dashboard
    *   `/articles` -> Admin Article List

*   **Proposed State:**
    *   `/` -> **Public Home Page** (List of latest articles)
    *   `/article/:slug` -> **Public Article View**
    *   `/admin` -> **Admin Dashboard**
    *   `/admin/articles` -> **Admin Article List**

> **Note:** This change requires updating the internal links within the Admin pages (e.g., the "Back" buttons) to include the `/admin` prefix.

## 3. Component Details

### A. PublicLayout.jsx
*   **Header**: Brand logo ("InSight"), Navigation links (Home, About).
*   **Footer**: Copyright info, simple links.
*   **Style**: Clean, white/minimalist design using Tailwind CSS.

### B. Home.jsx (Public Landing)
*   **Hero Section**: Welcoming banner.
*   **Article Grid**: Fetches published articles via `axios.get('/api/articles?status=published')`.
*   **Cards**: Show title, excerpt, author, and reading time.

### C. Article.jsx (Reader View)
*   **Header**: Large title, author info, published date.
*   **Content**: Displays the article text (preserving line breaks).
*   **Sidebar/Footer**: Author bio.

## 4. Execution Steps
1.  **Create Files**: Generate `PublicLayout.jsx`, `Pages/Public/Home.jsx`, and `Pages/Public/Article.jsx`.
2.  **Update Admin Components**: Refactor `AdminLayout`, `Index`, `Create`, and `Show` to use `/admin` based routes.
3.  **Update Router**: Modify `app.jsx` to implement the new routing structure.
4.  **Verify**: Ensure both Public (`localhost/`) and Admin (`localhost/admin/`) interfaces load correctly.

## 5. Risk Assessment
*   **Broken Links**: Existing admin bookmarks will need to be updated to `/admin`.
*   **API Compatibility**: The current API returns all articles. We will filter for "published" articles on the client-side temporarily until the backend filter is confirmed.

Please approve this plan to proceed.
