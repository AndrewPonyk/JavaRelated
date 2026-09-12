<?php

declare(strict_types=1);

namespace App\Identity\UI\Http;

use Symfony\Component\Validator\Constraints as Assert;

/** Validated registration payload (boundary validation; see ARCHITECTURE §2.5). */
final class RegisterRequest
{
    #[Assert\NotBlank]
    #[Assert\Email]
    #[Assert\Length(max: 180)]
    public string $email = '';

    #[Assert\NotBlank]
    #[Assert\Length(min: 8, max: 4096)]
    public string $password = '';

    /** Self-service signup may create a customer or a seller account, never an admin. */
    #[Assert\Choice(choices: ['customer', 'seller'])]
    public string $accountType = 'customer';
}
