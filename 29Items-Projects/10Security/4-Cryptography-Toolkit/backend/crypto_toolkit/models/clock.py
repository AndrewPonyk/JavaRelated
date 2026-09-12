"""Shared column-default clock.

The sqlite DATETIME dialect wants tz-naive values, so we read the modern
(non-deprecated) UTC clock and strip tzinfo — replacing datetime.utcnow().
"""

import datetime as dt


def utc_now() -> dt.datetime:
    return dt.datetime.now(dt.UTC).replace(tzinfo=None)
