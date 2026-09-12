import '../../domain/entities/staple_template.dart';

enum TemplatesStatus { initial, loading, loaded, applying, applied, failure }

class TemplatesState {
  final TemplatesStatus status;
  final List<StapleTemplate> templates;
  final String? lastAppliedTemplateName;
  final String? errorMessage;

  const TemplatesState({
    this.status = TemplatesStatus.initial,
    this.templates = const [],
    this.lastAppliedTemplateName,
    this.errorMessage,
  });

  TemplatesState copyWith({
    TemplatesStatus? status,
    List<StapleTemplate>? templates,
    String? lastAppliedTemplateName,
    String? errorMessage,
  }) {
    return TemplatesState(
      status: status ?? this.status,
      templates: templates ?? this.templates,
      lastAppliedTemplateName:
          lastAppliedTemplateName ?? this.lastAppliedTemplateName,
      errorMessage: errorMessage ?? this.errorMessage,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is TemplatesState &&
          runtimeType == other.runtimeType &&
          status == other.status &&
          templates.length == other.templates.length;

  @override
  int get hashCode => status.hashCode ^ templates.length.hashCode;
}
