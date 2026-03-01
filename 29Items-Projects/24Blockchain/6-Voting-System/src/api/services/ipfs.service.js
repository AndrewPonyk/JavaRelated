const crypto = require("crypto");
const logger = require("../utils/logger");

let ipfsClient = null;

// Try to load ipfs-http-client if available
try {
  const { create } = require("ipfs-http-client");
  ipfsClient = create;
} catch (err) {
  logger.warn("ipfs-http-client not installed, using local fallback only");
}

/**
 * Get or create the IPFS client connection.
 * Falls back gracefully if IPFS is not available.
 */
function getClient() {
  if (!ipfsClient) return null;

  if (ipfsClient._instance) return ipfsClient._instance;

  const apiUrl = process.env.IPFS_API_URL || "http://127.0.0.1:5001";
  try {
    ipfsClient._instance = ipfsClient({ url: apiUrl });
    logger.info(`IPFS client connected to ${apiUrl}`);
  } catch (err) {
    logger.error(`Failed to create IPFS client: ${err.message}`);
    ipfsClient = null;
  }
  return ipfsClient._instance;
}

/**
 * Pin a JSON object to IPFS and return its CID.
 * If IPFS is unavailable, generates a deterministic hash as a local CID reference.
 * @param {object} data - The JSON data to pin
 * @returns {string} IPFS CID
 */
async function pinJSON(data) {
  const jsonStr = JSON.stringify(data);
  const client = getClient();

  if (client) {
    try {
      const result = await client.add(Buffer.from(jsonStr));
      logger.info(`IPFS pinJSON: CID=${result.path}, size=${result.size}`);
      return result.path;
    } catch (err) {
      logger.error(`IPFS pinJSON failed: ${err.message}, falling back to local hash`);
    }
  }

  // Fallback: generate deterministic SHA-256 hash as a CID reference
  const hash = crypto.createHash("sha256").update(jsonStr).digest("hex");
  const localCid = `local_${hash}`;
  logger.warn(`IPFS unavailable, using local CID: ${localCid}`);
  return localCid;
}

/**
 * Pin a file buffer to IPFS.
 * @param {Buffer} buffer - File content
 * @param {string} filename - Original filename
 * @returns {string} IPFS CID
 */
async function pinFile(buffer, filename) {
  const client = getClient();

  if (client) {
    try {
      const result = await client.add({ path: filename, content: buffer });
      logger.info(`IPFS pinFile: ${filename} -> CID=${result.cid.toString()}`);
      return result.cid.toString();
    } catch (err) {
      logger.error(`IPFS pinFile failed: ${err.message}, falling back to local hash`);
    }
  }

  const hash = crypto.createHash("sha256").update(buffer).digest("hex");
  const localCid = `local_${hash}`;
  logger.warn(`IPFS unavailable, using local CID for ${filename}: ${localCid}`);
  return localCid;
}

/**
 * Retrieve JSON content from IPFS by CID.
 * @param {string} cid - IPFS content identifier
 * @returns {object|null} Parsed JSON data, or null if retrieval fails
 */
async function getJSON(cid) {
  if (!cid || cid.startsWith("local_")) {
    logger.warn(`Cannot retrieve local CID from IPFS: ${cid}`);
    return null;
  }

  const client = getClient();
  if (!client) {
    logger.error("IPFS client not available");
    return null;
  }

  try {
    const chunks = [];
    for await (const chunk of client.cat(cid)) {
      chunks.push(chunk);
    }
    const data = Buffer.concat(chunks).toString("utf8");
    return JSON.parse(data);
  } catch (err) {
    logger.error(`IPFS getJSON failed for CID ${cid}: ${err.message}`);
    return null;
  }
}

/**
 * Retrieve raw content from IPFS by CID.
 * @param {string} cid - IPFS content identifier
 * @returns {Buffer|null} Raw content
 */
async function getFile(cid) {
  if (!cid || cid.startsWith("local_")) {
    return null;
  }

  const client = getClient();
  if (!client) return null;

  try {
    const chunks = [];
    for await (const chunk of client.cat(cid)) {
      chunks.push(chunk);
    }
    return Buffer.concat(chunks);
  } catch (err) {
    logger.error(`IPFS getFile failed for CID ${cid}: ${err.message}`);
    return null;
  }
}

/**
 * Check if IPFS is reachable.
 */
async function isAvailable() {
  const client = getClient();
  if (!client) return false;

  try {
    await client.id();
    return true;
  } catch {
    return false;
  }
}

module.exports = { pinJSON, pinFile, getJSON, getFile, isAvailable };
