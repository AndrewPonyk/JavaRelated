"""Field-level encryption for PHI (HIPAA — ARCHITECTURE §2.5).

Values are encrypted with Fernet (AES-128-CBC + HMAC) before they touch the
database and decrypted transparently on load, so PHI is protected even in a DB
dump. The key is derived from ``settings.FIELD_ENCRYPTION_KEY`` (in production a
KMS-sourced secret); any sufficiently random string works as input.

Trade-off: ciphertext is non-deterministic, so encrypted columns cannot be used
for equality/range lookups. That's intentional — we never query by PHI; the
de-identified ``subject_code`` is the queryable key.
"""
from __future__ import annotations

import base64
import functools
import hashlib

from cryptography.fernet import Fernet, InvalidToken
from django.conf import settings
from django.core.validators import MaxLengthValidator
from django.db import models


@functools.lru_cache(maxsize=1)
def _fernet() -> Fernet:
    raw = (settings.FIELD_ENCRYPTION_KEY or "insecure-dev-key").encode("utf-8")
    key = base64.urlsafe_b64encode(hashlib.sha256(raw).digest())
    return Fernet(key)


class EncryptedCharField(models.TextField):
    """A char-like field whose value is stored encrypted at rest.

    ``max_length`` is preserved as a *plaintext* validation bound; storage uses
    TEXT because ciphertext is longer than the plaintext.
    """

    def __init__(self, *args, **kwargs):
        self.max_length = kwargs.pop("max_length", None)
        super().__init__(*args, **kwargs)
        if self.max_length is not None:
            self.validators.append(MaxLengthValidator(self.max_length))

    def deconstruct(self):
        name, path, args, kwargs = super().deconstruct()
        if self.max_length is not None:
            kwargs["max_length"] = self.max_length
        return name, path, args, kwargs

    def get_prep_value(self, value):
        value = super().get_prep_value(value)
        if value in (None, ""):
            return value
        return _fernet().encrypt(str(value).encode("utf-8")).decode("utf-8")

    def from_db_value(self, value, expression, connection):
        if value in (None, ""):
            return value
        try:
            return _fernet().decrypt(value.encode("utf-8")).decode("utf-8")
        except (InvalidToken, ValueError):
            # Tolerate pre-existing plaintext (e.g. migrated legacy data).
            return value
