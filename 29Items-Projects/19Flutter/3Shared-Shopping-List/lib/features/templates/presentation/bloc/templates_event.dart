abstract class TemplatesEvent {
  const TemplatesEvent();
}

class LoadTemplatesEvent extends TemplatesEvent {
  const LoadTemplatesEvent();
}

class ApplyTemplateEvent extends TemplatesEvent {
  final String templateId;
  final String targetListId;
  final String userId;

  const ApplyTemplateEvent({
    required this.templateId,
    required this.targetListId,
    required this.userId,
  });
}
