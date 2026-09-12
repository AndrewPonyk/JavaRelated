import { describe, expect, it } from "vitest";
import {
  createExperimentSchema,
  createOrganizationSchema,
  createSiteSchema,
  siteDocumentSchema
} from "@/lib/validation";

describe("validation schemas", () => {
  it("accepts valid organization and site payloads", () => {
    expect(createOrganizationSchema.parse({ name: "Acme", slug: "acme" })).toEqual({
      name: "Acme",
      slug: "acme"
    });
    expect(
      createSiteSchema.parse({
        organizationId: "org_1",
        name: "Launch",
        slug: "launch"
      })
    ).toEqual({
      organizationId: "org_1",
      name: "Launch",
      slug: "launch"
    });
  });

  it("rejects unsafe slugs", () => {
    expect(() => createOrganizationSchema.parse({ name: "Acme", slug: "Acme!" })).toThrow();
  });

  it("defaults missing document values", () => {
    expect(siteDocumentSchema.parse({}).blocks).toEqual([]);
  });

  it("validates experiment variants", () => {
    const parsed = createExperimentSchema.parse({
      organizationId: "org_1",
      siteId: "site_1",
      name: "Hero Test",
      variants: [
        { name: "A", trafficShare: 50, pageDocument: { version: 1, blocks: [] } },
        { name: "B", trafficShare: 50, pageDocument: { version: 1, blocks: [] } }
      ]
    });

    expect(parsed.variants).toHaveLength(2);
  });
});
