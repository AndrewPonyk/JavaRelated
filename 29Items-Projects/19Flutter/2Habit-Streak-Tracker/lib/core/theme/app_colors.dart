import 'package:flutter/material.dart';

/// Curated modern color tokens for Habit Streak Tracker
class AppColors {
  // Primary & Accents
  static const Color primary = Color(0xFF6366F1); // Indigo
  static const Color primaryDark = Color(0xFF4F46E5);
  static const Color accent = Color(0xFF10B981); // Emerald success

  // Streak & Heatmap Accents
  static const Color streakFlame = Color(0xFFF97316); // Vibrant orange
  static const Color streakFreeze = Color(0xFF06B6D4); // Cyan ice
  static const Color badgeBackground = Color(0xFFFEF3C7);

  // Backgrounds & Surfaces (Sleek Dark Mode first)
  static const Color backgroundDark = Color(0xFF0F172A); // Slate 900
  static const Color surfaceDark = Color(0xFF1E293B); // Slate 800
  static const Color cardDark = Color(0xFF334155); // Slate 700
  static const Color borderDark = Color(0xFF475569);

  // Light Mode Surfaces
  static const Color backgroundLight = Color(0xFFF8FAFC);
  static const Color surfaceLight = Color(0xFFFFFFFF);
  static const Color cardLight = Color(0xFFF1F5F9);
  static const Color borderLight = Color(0xFFE2E8F0);

  // Text Colors
  static const Color textPrimaryDark = Color(0xFFF8FAFC);
  static const Color textSecondaryDark = Color(0xFF94A3B8);
  static const Color textPrimaryLight = Color(0xFF0F172A);
  static const Color textSecondaryLight = Color(0xFF64748B);

  // Heatmap intensity levels
  static const List<Color> heatmapShadesDark = [
    Color(0xFF1E293B), // 0 completions
    Color(0xFF064E3B), // Level 1
    Color(0xFF047857), // Level 2
    Color(0xFF10B981), // Level 3
    Color(0xFF34D399), // Level 4 (Full)
  ];
}
