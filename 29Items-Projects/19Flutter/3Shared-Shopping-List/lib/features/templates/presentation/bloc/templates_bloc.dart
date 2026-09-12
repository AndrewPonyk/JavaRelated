import 'dart:async';
import '../../domain/repositories/templates_repository.dart';
import 'templates_event.dart';
import 'templates_state.dart';

class TemplatesBloc {
  final TemplatesRepository _repository;
  TemplatesState _state = const TemplatesState();
  final _stateController = StreamController<TemplatesState>.broadcast();

  TemplatesBloc({required TemplatesRepository repository})
      : _repository = repository;

  TemplatesState get state => _state;
  Stream<TemplatesState> get stream => _stateController.stream;

  void emit(TemplatesState newState) {
    _state = newState;
    if (!_stateController.isClosed) {
      _stateController.add(newState);
    }
  }

  void add(TemplatesEvent event) {
    if (event is LoadTemplatesEvent) {
      _onLoadTemplates();
    } else if (event is ApplyTemplateEvent) {
      _onApplyTemplate(event);
    }
  }

  Future<void> _onLoadTemplates() async {
    emit(state.copyWith(status: TemplatesStatus.loading));
    try {
      final templates = await _repository.getTemplates();
      emit(state.copyWith(
        status: TemplatesStatus.loaded,
        templates: templates,
      ));
    } catch (e) {
      emit(state.copyWith(
        status: TemplatesStatus.failure,
        errorMessage: e.toString(),
      ));
    }
  }

  Future<void> _onApplyTemplate(ApplyTemplateEvent event) async {
    emit(state.copyWith(status: TemplatesStatus.applying));
    try {
      await _repository.applyTemplateToList(
        templateId: event.templateId,
        targetListId: event.targetListId,
        userId: event.userId,
      );
      final template =
          state.templates.firstWhere((t) => t.id == event.templateId);
      emit(state.copyWith(
        status: TemplatesStatus.applied,
        lastAppliedTemplateName: template.name,
      ));
    } catch (e) {
      emit(state.copyWith(
        status: TemplatesStatus.failure,
        errorMessage: e.toString(),
      ));
    }
  }

  void close() {
    _stateController.close();
  }
}
