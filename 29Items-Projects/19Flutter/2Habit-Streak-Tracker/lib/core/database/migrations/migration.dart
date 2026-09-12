import 'package:sqflite/sqflite.dart';

/// Contract for executable database migrations
abstract class Migration {
  int get version;
  Future<void> up(Database db);
  Future<void> down(Database db);
}
