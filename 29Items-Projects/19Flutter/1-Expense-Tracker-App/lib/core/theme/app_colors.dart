/// Colour tokens.
///
/// The category palette is **Tableau 10**, chosen because it stays
/// distinguishable under the common forms of colour-vision deficiency. Charts
/// must never encode meaning in colour alone — legends and labels carry it too
/// (docs/TECH-NOTES.md §3.6 "FL Chart").
library;

import 'package:flutter/material.dart';

abstract final class AppColors {
  static const Color seed = Color(0xFF4E79A7);

  /// Budget-state colours. Paired with distinct icons/labels in
  /// [BudgetProgressBar] so colour is never the only signal.
  static const Color budgetSafe = Color(0xFF59A14F);
  static const Color budgetWarning = Color(0xFFF28E2B);
  static const Color budgetOver = Color(0xFFE15759);

  /// Colour-blind-safe categorical palette, used for category slices/bars.
  static const List<Color> categoryPalette = <Color>[
    Color(0xFF4E79A7),
    Color(0xFFF28E2B),
    Color(0xFF59A14F),
    Color(0xFFE15759),
    Color(0xFF76B7B2),
    Color(0xFF9C755F),
    Color(0xFFEDC948),
    Color(0xFFB07AA1),
    Color(0xFFFF9DA7),
    Color(0xFFBAB0AC),
  ];

  /// Deterministic colour for a category that has no explicit colour set, so the
  /// same category keeps the same colour across rebuilds and sessions.
  static Color forCategoryId(String id) =>
      categoryPalette[id.hashCode.abs() % categoryPalette.length];

  static const Color staleBanner = Color(0xFFF5C77E);
}
