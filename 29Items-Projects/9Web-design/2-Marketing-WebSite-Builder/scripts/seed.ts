import { prisma } from "@/lib/db";
import { toInputJson } from "@/lib/json";
import { createDefaultDocument, createBlock } from "@/services/pageDocumentService";

async function main() {
  const user = await prisma.user.upsert({
    where: { email: "demo@builder.local" },
    update: { name: "Demo Marketer" },
    create: {
      email: "demo@builder.local",
      name: "Demo Marketer"
    }
  });

  const organization = await prisma.organization.upsert({
    where: { slug: "demo-marketing" },
    update: { name: "Demo Marketing Team" },
    create: {
      name: "Demo Marketing Team",
      slug: "demo-marketing"
    }
  });

  await prisma.membership.upsert({
    where: {
      userId_organizationId: {
        userId: user.id,
        organizationId: organization.id
      }
    },
    update: { role: "OWNER" },
    create: {
      userId: user.id,
      organizationId: organization.id,
      role: "OWNER"
    }
  });

  const site = await prisma.site.upsert({
    where: {
      organizationId_slug: {
        organizationId: organization.id,
        slug: "spring-launch"
      }
    },
    update: { name: "Spring Launch" },
    create: {
      organizationId: organization.id,
      name: "Spring Launch",
      slug: "spring-launch",
      status: "DRAFT",
      pages: {
        create: {
          title: "Home",
          slug: "home",
          document: toInputJson(createDefaultDocument())
        }
      }
    }
  });

  await prisma.blockTemplate.createMany({
    data: [
      {
        organizationId: organization.id,
        name: "Conversion Hero",
        category: "HERO",
        document: toInputJson({ version: 1, blocks: [createBlock("HERO")] })
      },
      {
        organizationId: organization.id,
        name: "Lead Capture Form",
        category: "FORM",
        document: toInputJson({ version: 1, blocks: [createBlock("FORM")] })
      }
    ],
    skipDuplicates: true
  });

  await prisma.conversionEvent.createMany({
    data: [
      {
        organizationId: organization.id,
        siteId: site.id,
        pageSlug: "home",
        eventName: "view",
        source: "seed"
      },
      {
        organizationId: organization.id,
        siteId: site.id,
        pageSlug: "home",
        eventName: "form_submit",
        source: "seed"
      }
    ]
  });
}

main()
  .then(async () => {
    await prisma.$disconnect();
  })
  .catch(async (error) => {
    console.error(error);
    await prisma.$disconnect();
    process.exit(1);
  });
