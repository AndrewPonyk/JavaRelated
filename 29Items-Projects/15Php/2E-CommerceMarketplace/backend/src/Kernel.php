<?php

declare(strict_types=1);

namespace App;

use Symfony\Bundle\FrameworkBundle\Kernel\MicroKernelTrait;
use Symfony\Component\HttpKernel\Kernel as BaseKernel;

/**
 * Application kernel.
 *
 * Bundles are declared in config/bundles.php; per-environment configuration is
 * loaded from config/packages and config/{packages}/{env}. Each bounded context
 * registers its services through config/services.yaml (autowired by namespace).
 */
final class Kernel extends BaseKernel
{
    use MicroKernelTrait;
}
