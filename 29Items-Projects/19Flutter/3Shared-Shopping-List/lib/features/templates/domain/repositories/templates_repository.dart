import '../entities/staple_template.dart';

abstract class TemplatesRepository {
  /// Loads all available weekly staple templates
  Future<List<StapleTemplate>> getTemplates();

  /// Applies a template to a list, adding its items
  Future<void> applyTemplateToList({
    required String templateId,
    required String targetListId,
    required String userId,
  });
}
