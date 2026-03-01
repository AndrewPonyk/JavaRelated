const request = require("supertest");
const app = require("../../src/api/server");
const { setupTestDb, cleanTestDb, seedTestData, closeDb } = require("./setup");

// Mock blockchain service to avoid requiring an actual Ethereum node
jest.mock("../../src/api/services/blockchain.service", () => ({
  initialize: jest.fn(),
  startEventListener: jest.fn(),
  stopEventListeners: jest.fn(),
  getOnChainProposal: jest.fn().mockResolvedValue(null),
  getOnChainResult: jest.fn().mockResolvedValue(null),
  getEffectiveDelegate: jest.fn().mockResolvedValue(null),
  getOnChainVotingPower: jest.fn().mockResolvedValue(1),
  getBlockNumber: jest.fn().mockResolvedValue(100),
  verifySignature: jest.fn().mockReturnValue("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"),
  getStatus: jest.fn().mockReturnValue({ connected: true, votingSystem: false }),
}));

// Mock IPFS to avoid requiring a running IPFS node
jest.mock("../../src/api/services/ipfs.service", () => ({
  pinJSON: jest.fn().mockResolvedValue("QmMockedCid123"),
  pinFile: jest.fn().mockResolvedValue("QmMockedFileCid"),
  getJSON: jest.fn().mockResolvedValue({ title: "test" }),
  getFile: jest.fn().mockResolvedValue(null),
  isAvailable: jest.fn().mockResolvedValue(false),
}));

const skipIfNoDb = process.env.DATABASE_URL ? describe : describe.skip;

skipIfNoDb("Proposal API", () => {
  beforeAll(async () => {
    await setupTestDb();
  });

  beforeEach(async () => {
    await cleanTestDb();
    await seedTestData();
  });

  afterAll(async () => {
    await closeDb();
  });

  describe("GET /api/proposals", () => {
    it("should return a list of proposals", async () => {
      const res = await request(app).get("/api/proposals").expect(200);

      expect(res.body).toHaveProperty("data");
      expect(Array.isArray(res.body.data)).toBe(true);
      expect(res.body.data.length).toBeGreaterThanOrEqual(1);
      expect(res.body).toHaveProperty("pagination");
    });

    it("should filter by phase", async () => {
      const res = await request(app).get("/api/proposals?phase=draft").expect(200);

      res.body.data.forEach((p) => {
        expect(p.phase).toBe("draft");
      });
    });

    it("should support pagination", async () => {
      const res = await request(app).get("/api/proposals?page=1&limit=5").expect(200);
      expect(res.body.data.length).toBeLessThanOrEqual(5);
    });

    it("should return empty array for non-existent phase", async () => {
      const res = await request(app).get("/api/proposals?phase=tallied").expect(200);
      expect(res.body.data).toHaveLength(0);
    });
  });

  describe("GET /api/proposals/:id", () => {
    it("should return a proposal with options", async () => {
      const res = await request(app).get("/api/proposals/1").expect(200);

      expect(res.body.data).toHaveProperty("title", "Test Proposal");
      expect(res.body.data).toHaveProperty("options");
      expect(res.body.data.options).toHaveLength(2);
    });

    it("should return 404 for non-existent proposal", async () => {
      const res = await request(app).get("/api/proposals/99999").expect(404);
      expect(res.body.error.code).toBe("NOT_FOUND");
    });
  });

  describe("POST /api/proposals", () => {
    it("should reject unauthenticated requests", async () => {
      await request(app)
        .post("/api/proposals")
        .send({ title: "Test", description: "A test proposal", options: ["Yes", "No"] })
        .expect(401);
    });

    it("should create a proposal with valid auth", async () => {
      // First get a nonce
      const nonceRes = await request(app)
        .post("/api/auth/nonce")
        .send({ walletAddress: "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266" })
        .expect(200);

      const res = await request(app)
        .post("/api/proposals")
        .set("x-wallet-address", "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266")
        .set("x-signature", "0xmockedsignature")
        .set("x-nonce", nonceRes.body.data.nonce)
        .send({
          title: "New Proposal",
          description: "This is a test proposal description",
          options: ["Yes", "No", "Abstain"],
        })
        .expect(201);

      expect(res.body.data).toHaveProperty("title", "New Proposal");
      expect(res.body.data.option_count).toBe(3);
    });

    it("should reject invalid proposal data", async () => {
      const nonceRes = await request(app)
        .post("/api/auth/nonce")
        .send({ walletAddress: "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266" })
        .expect(200);

      await request(app)
        .post("/api/proposals")
        .set("x-wallet-address", "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266")
        .set("x-signature", "0xmockedsignature")
        .set("x-nonce", nonceRes.body.data.nonce)
        .send({ title: "", description: "test", options: ["Yes"] }) // invalid: empty title, <2 options
        .expect(400);
    });
  });

  describe("DELETE /api/proposals/:id", () => {
    it("should reject unauthenticated delete", async () => {
      await request(app).delete("/api/proposals/1").expect(401);
    });
  });
});

describe("Health Check", () => {
  it("should return ok status", async () => {
    const res = await request(app).get("/health").expect(200);
    expect(res.body.status).toBe("ok");
    expect(res.body).toHaveProperty("timestamp");
    expect(res.body).toHaveProperty("blockchain");
  });
});

describe("Auth API", () => {
  describe("POST /api/auth/nonce", () => {
    it("should reject invalid wallet address", async () => {
      await request(app)
        .post("/api/auth/nonce")
        .send({ walletAddress: "not-an-address" })
        .expect(400);
    });
  });
});
