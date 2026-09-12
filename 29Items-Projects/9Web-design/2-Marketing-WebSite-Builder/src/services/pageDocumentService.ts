import type { BlockCategory, BuilderBlock, SiteDocument } from "@/domain/site";

const defaultProps: Record<BlockCategory, BuilderBlock["props"]> = {
  HERO: {
    headline: "Build landing pages without waiting on engineering",
    body: "Compose tested blocks, ship previews, and publish conversion-focused pages.",
    buttonLabel: "Start building",
    buttonHref: "#lead-form",
    alignment: "left",
    background: "#eff6ff"
  },
  CTA: {
    headline: "Ready to launch?",
    body: "Publish a focused landing page for the next campaign.",
    buttonLabel: "Request demo",
    buttonHref: "#lead-form",
    alignment: "center",
    background: "#ecfdf5"
  },
  PRICING: {
    headline: "Growth plan",
    body: "Everything marketing teams need to ship and learn.",
    price: "$249/mo",
    buttonLabel: "Choose plan",
    buttonHref: "#lead-form"
  },
  TESTIMONIALS: {
    headline: "Trusted by launch teams",
    testimonial: "We publish campaign pages in hours instead of weeks.",
    body: "VP Marketing, B2B SaaS"
  },
  FORM: {
    headline: "Get the launch checklist",
    body: "Leave your work email and we will send the checklist.",
    fields: ["workEmail", "company"]
  },
  FAQ: {
    question: "Can developers control the output?",
    answer: "Yes. Design tokens, approved blocks, and review workflows keep pages consistent."
  }
};

export function createDefaultDocument(): SiteDocument {
  return {
    version: 1,
    blocks: [
      createBlock("HERO"),
      createBlock("CTA"),
      createBlock("FORM")
    ]
  };
}

export function createBlock(type: BlockCategory): BuilderBlock {
  return {
    id: `${type.toLowerCase()}-${crypto.randomUUID()}`,
    type,
    props: defaultProps[type]
  };
}

export function normalizeDocument(document: SiteDocument): SiteDocument {
  return {
    version: document.version || 1,
    blocks: document.blocks.map((block) => ({
      id: block.id || `${block.type.toLowerCase()}-${crypto.randomUUID()}`,
      type: block.type,
      props: block.props ?? {}
    }))
  };
}

export function buildSuggestionDocument(document: SiteDocument, conversionRate: number): SiteDocument {
  const nextBlocks = [...document.blocks];

  if (conversionRate < 0.08 && !nextBlocks.some((block) => block.type === "TESTIMONIALS")) {
    nextBlocks.splice(1, 0, createBlock("TESTIMONIALS"));
  }

  if (!nextBlocks.some((block) => block.type === "FAQ")) {
    nextBlocks.push(createBlock("FAQ"));
  }

  return {
    version: document.version + 1,
    blocks: nextBlocks
  };
}

export function mapFigmaNodesToDocument(nodes: Array<{ id: string; name: string; type: string }>): SiteDocument {
  const blocks = nodes.map((node, index): BuilderBlock => {
    const normalizedName = node.name.toLowerCase();
    const type: BuilderBlock["type"] = normalizedName.includes("price") || normalizedName.includes("pricing")
      ? "PRICING"
      : normalizedName.includes("faq")
        ? "FAQ"
        : normalizedName.includes("form")
          ? "FORM"
          : index === 0
            ? "HERO"
            : "CTA";

    return {
      id: `figma-${node.id}`,
      type,
      props: {
        headline: node.name,
        body: `Imported from Figma ${node.type.toLowerCase()} node.`,
        buttonLabel: type === "CTA" || type === "HERO" ? "Learn more" : undefined,
        buttonHref: "#lead-form"
      }
    };
  });

  return {
    version: 1,
    blocks
  };
}
