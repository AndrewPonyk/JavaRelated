/// Load/data/error tri-state for a provider-held value. Kept separate from
/// [Result] (`core/utils/result.dart`): `Result` is a one-shot operation
/// outcome, `AsyncState` is what a widget renders *right now* — including the
/// in-flight loading frame `Result` has no concept of.
library;

import '../../core/error/failures.dart';

sealed class AsyncState<T> {
  const AsyncState();

  T? get valueOrNull => switch (this) {
        AsyncData<T>(:final value) => value,
        _ => null,
      };
}

class AsyncLoading<T> extends AsyncState<T> {
  const AsyncLoading();
}

class AsyncData<T> extends AsyncState<T> {
  const AsyncData(this.value);
  final T value;
}

class AsyncFailure<T> extends AsyncState<T> {
  const AsyncFailure(this.failure);
  final Failure failure;
}
