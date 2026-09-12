<?php

declare(strict_types=1);

namespace App\FraudDetection\Domain\Model;

enum FraudDecision: string
{
    case Allow = 'ALLOW';
    case Review = 'REVIEW';   // route to a human review queue
    case Block = 'BLOCK';     // prevent payment capture
}
