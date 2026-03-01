const { body, param, query } = require("express-validator");

/**
 * Validation rules for proposal creation.
 */
const createProposalRules = [
  body("title")
    .isString()
    .trim()
    .isLength({ min: 3, max: 500 })
    .withMessage("Title must be 3-500 characters"),
  body("description")
    .isString()
    .trim()
    .isLength({ min: 10 })
    .withMessage("Description must be at least 10 characters"),
  body("options")
    .isArray({ min: 2, max: 20 })
    .withMessage("Must have 2-20 voting options"),
  body("options.*")
    .isString()
    .trim()
    .isLength({ min: 1, max: 200 })
    .withMessage("Each option must be 1-200 characters"),
];

/**
 * Validation for Ethereum address parameters.
 */
const addressParam = [
  param("address")
    .matches(/^0x[a-fA-F0-9]{40}$/)
    .withMessage("Invalid Ethereum address"),
];

/**
 * Validation for pagination query params.
 */
const paginationRules = [
  query("page").optional().isInt({ min: 1 }).toInt(),
  query("limit").optional().isInt({ min: 1, max: 100 }).toInt(),
];

module.exports = { createProposalRules, addressParam, paginationRules };
