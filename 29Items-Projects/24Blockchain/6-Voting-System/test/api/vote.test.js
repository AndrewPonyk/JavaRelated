const request = require("supertest");
const app = require("../../src/api/server");
const { setupTestDb, cleanTestDb, seedTestData, closeDb, pool } = require("./setup");

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
  getStatus: jest.fn().mockReturnValue({ connected: true }),
}));

jest.mock("../../src/api/services/ipfs.service", () => ({
  pinJSON: jest.fn().mockResolvedValue("QmMockedCid"),
  pinFile: jest.fn().mockResolvedValue("QmMockedFileCid"),
  getJSON: jest.fn().mockResolvedValue(null),
  getFile: jest.fn().mockResolvedValue(null),
  isAvailable: jest.fn().mockResolvedValue(false),
}));

const skipIfNoDb = process.env.DATABASE_URL ? describe : describe.skip;

skipIfNoDb("Vote API", () => {
  beforeAll(async () => {
    await setupTestDb();
  });

  beforeEach(async () => {
    await cleanTestDb();
    await seedTestData();

    // Add some vote commits for testing
    await pool.query(
      `INSERT INTO vote_commits (proposal_id, voter_address, commit_hash, tx_hash, block_number)
       VALUES (1, '0xaaa', '0xhash1', '0xtx1', 50), (1, '0xbbb', '0xhash2', '0xtx2', 51)`
    );

    // Add a reveal
    await pool.query(
      `INSERT INTO vote_reveals (proposal_id, voter_address, choice, tx_hash, block_number)
       VALUES (1, '0xaaa', 0, '0xtx3', 60)`
    );

    // Update tally
    await pool.query(
      "UPDATE proposal_options SET vote_count = 1 WHERE proposal_id = 1 AND option_index = 0"
    );
  });

  afterAll(async () => {
    await closeDb();
  });

  describe("GET /api/proposals/:id/results", () => {
    it("should return vote results", async () => {
      const res = await request(app).get("/api/proposals/1/results").expect(200);

      expect(res.body.data).toHaveProperty("totalVotes");
      expect(res.body.data).toHaveProperty("options");
      expect(res.body.data.options).toHaveLength(2);
      expect(res.body.data.options[0]).toHaveProperty("label");
      expect(res.body.data.options[0]).toHaveProperty("votes");
      expect(res.body.data.options[0]).toHaveProperty("percentage");
    });

    it("should return 404 for non-existent proposal", async () => {
      await request(app).get("/api/proposals/99999/results").expect(404);
    });
  });

  describe("GET /api/proposals/:id/commits", () => {
    it("should return commit count", async () => {
      const res = await request(app).get("/api/proposals/1/commits").expect(200);

      expect(res.body.data).toHaveProperty("commitCount", 2);
      expect(res.body.data).toHaveProperty("proposalId", 1);
    });
  });

  describe("GET /api/proposals/:id/voters", () => {
    it("should return voter list", async () => {
      const res = await request(app).get("/api/proposals/1/voters").expect(200);

      expect(res.body.data).toHaveLength(2);
      expect(res.body.data[0]).toHaveProperty("voter_address");
      expect(res.body.data[0]).toHaveProperty("committed_at");
    });
  });

  describe("GET /api/proposals/:id/status", () => {
    it("should return voting status", async () => {
      const res = await request(app).get("/api/proposals/1/status").expect(200);

      expect(res.body.data).toHaveProperty("commitCount", 2);
      expect(res.body.data).toHaveProperty("revealCount", 1);
      expect(res.body.data).toHaveProperty("currentBlock");
    });
  });
});

skipIfNoDb("Delegation API", () => {
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

  describe("GET /api/delegation/:address", () => {
    it("should return delegation info", async () => {
      const addr = "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266";
      const res = await request(app).get(`/api/delegation/${addr}`).expect(200);

      expect(res.body.data).toHaveProperty("address");
      expect(res.body.data).toHaveProperty("receivedDelegations");
    });

    it("should reject invalid address", async () => {
      await request(app).get("/api/delegation/invalid").expect(400);
    });
  });

  describe("GET /api/delegation/:address/power", () => {
    it("should return voting power", async () => {
      const addr = "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266";
      const res = await request(app).get(`/api/delegation/${addr}/power`).expect(200);

      expect(res.body.data).toHaveProperty("votingPower");
      expect(res.body.data.votingPower).toBeGreaterThanOrEqual(1);
    });
  });

  describe("GET /api/delegation/:address/history", () => {
    it("should return delegation history", async () => {
      const addr = "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266";
      const res = await request(app).get(`/api/delegation/${addr}/history`).expect(200);

      expect(Array.isArray(res.body.data)).toBe(true);
    });
  });
});
