/// Renders the three states every screen in this app can be in — loading,
/// error, data (with an optional empty variant) — so no screen hand-rolls its
/// own spinner/error switch (PROJECT-PLAN §3 "Definition of Done" (3)).
library;

import 'package:flutter/material.dart';

import '../../core/error/error_handler.dart';
import '../providers/async_state.dart';

class AsyncValueView<T> extends StatelessWidget {
  const AsyncValueView({
    super.key,
    required this.state,
    required this.data,
    this.isEmpty,
    this.empty,
    this.loading,
  });

  final AsyncState<T> state;
  final Widget Function(BuildContext context, T value) data;

  /// If provided alongside [empty], an [AsyncData] whose value satisfies this
  /// predicate renders [empty] instead of [data].
  final bool Function(T value)? isEmpty;
  final WidgetBuilder? empty;
  final WidgetBuilder? loading;

  @override
  Widget build(BuildContext context) {
    return switch (state) {
      AsyncLoading<T>() => loading?.call(context) ??
          const Center(child: CircularProgressIndicator()),
      AsyncFailure<T>(:final failure) => _ErrorView(message: ErrorHandler.userMessage(failure)),
      AsyncData<T>(:final value) => (isEmpty != null && empty != null && isEmpty!(value))
          ? empty!(context)
          : data(context, value),
    };
  }
}

class _ErrorView extends StatelessWidget {
  const _ErrorView({required this.message});
  final String message;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.error_outline, size: 40, color: Theme.of(context).colorScheme.error),
            const SizedBox(height: 12),
            Text(message, textAlign: TextAlign.center),
          ],
        ),
      ),
    );
  }
}
