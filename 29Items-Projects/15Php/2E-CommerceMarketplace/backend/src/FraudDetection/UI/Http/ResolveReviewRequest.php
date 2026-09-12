<?php

declare(strict_types=1);

namespace App\FraudDetection\UI\Http;

/** Reviewer decision payload: `cleared` true ⇒ legitimate; false ⇒ confirmed fraud. */
final class ResolveReviewRequest
{
    public bool $cleared = false;
}
