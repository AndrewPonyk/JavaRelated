# API Documentation

All API responses use a stable envelope:

```json
{ "ok": true, "data": {} }
```

Errors return:

```json
{
  "ok": false,
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "Invalid request payload",
    "details": {}
  }
}
```

Local development uses a demo actor. Production callers should provide `x-user-email` and `x-user-name` until the auth adapter is replaced with a hosted identity provider.

## Organizations

- `GET /api/organizations`: List organizations for the actor.
- `POST /api/organizations`: Create an organization and owner membership.
- `GET /api/organizations/:id`: Read an organization.
- `PATCH /api/organizations/:id`: Update name or slug.
- `DELETE /api/organizations/:id`: Delete organization and owned resources.

Create payload:

```json
{ "name": "Demo Marketing Team", "slug": "demo-marketing" }
```

## Sites

- `GET /api/sites?organizationId=:id`: List sites.
- `POST /api/sites`: Create a site with a default home page.
- `GET /api/sites/:id`: Read site summary.
- `PATCH /api/sites/:id`: Update name, slug, or status.
- `DELETE /api/sites/:id`: Delete site.

Create payload:

```json
{ "organizationId": "org_id", "name": "Spring Launch", "slug": "spring-launch" }
```

Publish payload:

```json
{ "status": "PUBLISHED" }
```

## Pages

- `GET /api/sites/:id/pages`: List pages for a site.
- `POST /api/sites/:id/pages`: Create page.
- `PATCH /api/sites/:id/pages/:pageId`: Update page metadata or document.
- `DELETE /api/sites/:id/pages/:pageId`: Delete page.

Page documents are versioned JSON:

```json
{
  "version": 1,
  "blocks": [
    {
      "id": "hero-1",
      "type": "HERO",
      "props": {
        "headline": "Launch faster",
        "body": "Build controlled campaign pages.",
        "buttonLabel": "Get started",
        "buttonHref": "#lead-form"
      }
    }
  ]
}
```

## Templates

- `GET /api/templates?organizationId=:id`: List reusable block templates.
- `POST /api/templates`: Create template.
- `PATCH /api/templates/:id`: Update template.
- `DELETE /api/templates/:id`: Delete template.

Categories: `HERO`, `CTA`, `PRICING`, `TESTIMONIALS`, `FORM`, `FAQ`.

## Experiments

- `GET /api/experiments?organizationId=:id`: List experiments.
- `POST /api/experiments`: Create experiment with variants.
- `PATCH /api/experiments/:id`: Update status, name, or variants.
- `DELETE /api/experiments/:id`: Delete experiment.

Variant traffic share must total `100`.

## Analytics

- `POST /api/conversion-events`: Record a public conversion or page event.
- `GET /api/analytics?organizationId=:id&siteId=:id`: Return total events, event counts, source counts, and conversion rate.

## Suggestions

- `GET /api/sites/:id/suggestions`: List generated suggestions.
- `POST /api/sites/:id/suggestions`: Generate a layout suggestion from current page content and conversion history.

## Figma Import

- `POST /api/figma/import`: Convert selected Figma nodes into a builder page.

Payload:

```json
{
  "organizationId": "org_id",
  "siteId": "site_id",
  "name": "Imported Figma Page",
  "nodes": [{ "id": "1:2", "name": "Hero Frame", "type": "FRAME" }]
}
```
