import 'package:flutter_test/flutter_test.dart';
import 'package:habit_streak_tracker/core/database/app_database.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  sqfliteFfiInit();
  databaseFactory = databaseFactoryFfi;

  group('AppDatabase Lifecycle Tests', () {
    test(
      'AppDatabase instance initializes and returns open SQLite database',
      () async {
        final appDb = AppDatabase.instance;
        final db = await appDb.database;

        expect(db.isOpen, isTrue);

        final version = await db.getVersion();
        expect(version, equals(2));

        await appDb.close();
      },
    );
  });
}
