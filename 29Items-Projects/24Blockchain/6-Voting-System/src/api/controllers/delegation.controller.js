const delegationService = require("../services/delegation.service");

/**
 * GET /api/delegation/:address
 * Get delegation info for a wallet address.
 */
async function getDelegationInfo(req, res, next) {
  try {
    if (!/^0x[a-fA-F0-9]{40}$/.test(req.params.address)) {
      return res.status(400).json({
        error: { code: "INVALID_ADDRESS", message: "Invalid Ethereum address" },
      });
    }

    const info = await delegationService.getDelegationInfo(req.params.address);
    res.json({ data: info });
  } catch (err) {
    next(err);
  }
}

/**
 * GET /api/delegation/:address/power
 * Get effective voting power for an address.
 */
async function getVotingPower(req, res, next) {
  try {
    if (!/^0x[a-fA-F0-9]{40}$/.test(req.params.address)) {
      return res.status(400).json({
        error: { code: "INVALID_ADDRESS", message: "Invalid Ethereum address" },
      });
    }

    const power = await delegationService.getVotingPower(req.params.address);
    res.json({ data: { address: req.params.address.toLowerCase(), votingPower: power } });
  } catch (err) {
    next(err);
  }
}

/**
 * GET /api/delegation/:address/history
 * Get delegation history for an address.
 */
async function getDelegationHistory(req, res, next) {
  try {
    if (!/^0x[a-fA-F0-9]{40}$/.test(req.params.address)) {
      return res.status(400).json({
        error: { code: "INVALID_ADDRESS", message: "Invalid Ethereum address" },
      });
    }

    const history = await delegationService.getDelegationHistory(req.params.address);
    res.json({ data: history });
  } catch (err) {
    next(err);
  }
}

module.exports = { getDelegationInfo, getVotingPower, getDelegationHistory };
