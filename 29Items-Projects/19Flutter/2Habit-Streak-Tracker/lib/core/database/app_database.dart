import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:sqflite_common_ffi_web/sqflite_ffi_web.dart';
import '../constants/db_constants.dart';
import 'migrations/migration.dart';
import 'migrations/v1_schema.dart';
import 'migrations/v2_add_freeze.dart';

/// Database Manager handling initialization, foreign keys, and migrations
class AppDatabase {
  static final AppDatabase instance = AppDatabase._internal();
  static Database? _database;

  AppDatabase._internal();

  /// Ordered registry of database migrations
  final List<Migration> _migrations = [V1Schema(), V2AddFreeze()];

  Future<Database> get database async {
    if (_database != null) return _database!;
    _database = await _initDatabase();
    return _database!;
  }

  Future<Database> _initDatabase({String? customPath}) async {
    // Configure SQLite factory based on platform
    if (kIsWeb) {
      databaseFactory = databaseFactoryFfiWeb;
    } else if (Platform.isWindows || Platform.isLinux) {
      sqfliteFfiInit();
      databaseFactory = databaseFactoryFfi;
    }

    String dbPath;
    if (customPath != null) {
      dbPath = customPath;
    } else if (kIsWeb) {
      dbPath = DbConstants.databaseName;
    } else if (Platform.isWindows || Platform.isLinux) {
      try {
        final appDocDir = await getApplicationDocumentsDirectory();
        dbPath = p.join(appDocDir.path, DbConstants.databaseName);
      } catch (_) {
        final fallbackDir = Directory(
          p.join(Directory.current.path, '.dart_tool', 'sqflite_test'),
        );
        if (!fallbackDir.existsSync()) {
          fallbackDir.createSync(recursive: true);
        }
        dbPath = p.join(fallbackDir.path, DbConstants.databaseName);
      }
    } else {
      dbPath = p.join(await getDatabasesPath(), DbConstants.databaseName);
    }

    return await openDatabase(
      dbPath,
      version: DbConstants.databaseVersion,
      onConfigure: _onConfigure,
      onCreate: _onCreate,
      onUpgrade: _onUpgrade,
    );
  }

  /// Ensure foreign keys are enabled on every connection
  Future<void> _onConfigure(Database db) async {
    await db.execute('PRAGMA foreign_keys = ON;');
  }

  /// Run all migrations from version 1 to current
  Future<void> _onCreate(Database db, int version) async {
    for (final migration in _migrations) {
      if (migration.version <= version) {
        await migration.up(db);
      }
    }
  }

  /// Sequentially apply upgrades across intermediate versions
  Future<void> _onUpgrade(Database db, int oldVersion, int newVersion) async {
    for (final migration in _migrations) {
      if (migration.version > oldVersion && migration.version <= newVersion) {
        await migration.up(db);
      }
    }
  }

  /// Close connection for testing or app teardown
  Future<void> close() async {
    final db = _database;
    if (db != null) {
      await db.close();
      _database = null;
    }
  }
}
