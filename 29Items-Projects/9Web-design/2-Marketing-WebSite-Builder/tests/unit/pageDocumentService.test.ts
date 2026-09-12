import { describe, expect, it } from "vitest";
import {
  buildSuggestionDocument,
  createBlock,
  createDefaultDocument,
  mapFigmaNodesToDocument,
  normalizeDocument
} from "@/services/pageDocumentService";

describe("pageDocumentService", () => {
  it("creates a default page document with core marketing blocks", () => {
    const document = createDefaultDocument();

    expect(document.version).toBe(1);
    expect(document.blocks.map((block) => block.type)).toEqual(["HERO", "CTA", "FORM"]);
    expect(document.blocks[0].props.headline).toContain("Build landing pages");
  });

  it("creates category-specific blocks", () => {
    const block = createBlock("PRICING");

    expect(block.type).toBe("PRICING");
    expect(block.props.price).toBe("$249/mo");
    expect(block.id).toMatch(/^pricing-/);
  });

  it("normalizes missing block ids and props", () => {
    const document = normalizeDocument({
      version: 2,
      blocks: [{ id: "", type: "CTA", props: {} }]
    });

    expect(document.version).toBe(2);
    expect(document.blocks[0].id).toMatch(/^cta-/);
    expect(document.blocks[0].props).toEqual({});
  });

  it("adds trust content when conversion rate is weak", () => {
    const suggestion = buildSuggestionDocument(createDefaultDocument(), 0.03);

    expect(suggestion.version).toBe(2);
    expect(suggestion.blocks.some((block) => block.type === "TESTIMONIALS")).toBe(true);
    expect(suggestion.blocks.some((block) => block.type === "FAQ")).toBe(true);
  });

  it("maps Figma nodes into builder blocks", () => {
    const document = mapFigmaNodesToDocument([
      { id: "1:2", name: "Hero Frame", type: "FRAME" },
      { id: "1:3", name: "Pricing Cards", type: "COMPONENT" },
      { id: "1:4", name: "FAQ", type: "FRAME" }
    ]);

    expect(document.blocks.map((block) => block.type)).toEqual(["HERO", "PRICING", "FAQ"]);
    expect(document.blocks[1].props.body).toContain("component");
  });
});
