/// `Result<T>` — the return type of every fallible operation in `data/` and
/// `domain/`.
///
/// Using a value instead of an exception for expected conditions means the
/// compiler forces callers to consider failure. See ARCHITECTURE §2.6.
library;

import '../error/failures.dart';

sealed class Result<T> {
  const Result();

  bool get isOk => this is Ok<T>;
  bool get isErr => this is Err<T>;

  /// Value if successful, else `null`. Prefer `switch` for anything important.
  T? get valueOrNull => switch (this) {
        Ok<T>(:final value) => value,
        Err<T>() => null,
      };

  Failure? get failureOrNull => switch (this) {
        Ok<T>() => null,
        Err<T>(:final failure) => failure,
      };

  T getOrElse(T fallback) => valueOrNull ?? fallback;

  Result<R> map<R>(R Function(T value) transform) => switch (this) {
        Ok<T>(:final value) => Ok<R>(transform(value)),
        Err<T>(:final failure) => Err<R>(failure),
      };

  /// Chains another fallible step, short-circuiting on the first failure.
  Result<R> flatMap<R>(Result<R> Function(T value) transform) => switch (this) {
        Ok<T>(:final value) => transform(value),
        Err<T>(:final failure) => Err<R>(failure),
      };

  R fold<R>(R Function(T value) onOk, R Function(Failure failure) onErr) =>
      switch (this) {
        Ok<T>(:final value) => onOk(value),
        Err<T>(:final failure) => onErr(failure),
      };
}

final class Ok<T> extends Result<T> {
  const Ok(this.value);
  final T value;

  @override
  String toString() => 'Ok($value)';
}

final class Err<T> extends Result<T> {
  const Err(this.failure);
  final Failure failure;

  @override
  String toString() => 'Err($failure)';
}

/// Convenience for `void` results.
typedef VoidResult = Result<void>;

const Ok<void> okVoid = Ok<void>(null);
