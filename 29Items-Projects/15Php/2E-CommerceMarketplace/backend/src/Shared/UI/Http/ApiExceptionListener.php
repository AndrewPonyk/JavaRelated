<?php

declare(strict_types=1);

namespace App\Shared\UI\Http;

use App\Shared\Domain\Exception\NotFoundException;
use App\Shared\Domain\Security\AccessDeniedException;
use DomainException;
use Symfony\Component\EventDispatcher\Attribute\AsEventListener;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\HttpKernel\Event\ExceptionEvent;
use Symfony\Component\HttpKernel\Exception\HttpExceptionInterface;
use Symfony\Component\Security\Core\Exception\AccessDeniedException as SecurityAccessDeniedException;
use Symfony\Component\Validator\Exception\ValidationFailedException;
use Throwable;

/**
 * Single place that maps exceptions to JSON HTTP responses, so controllers can
 * stay thin and the client never sees a stack trace. A correlation id is echoed
 * back for support/tracing.
 */
#[AsEventListener(event: 'kernel.exception')]
final readonly class ApiExceptionListener
{
    public function onKernelException(ExceptionEvent $event): void
    {
        $request = $event->getRequest();
        if (!str_starts_with($request->getPathInfo(), '/api')) {
            return; // let non-API routes use the default error handling
        }

        $e = $event->getThrowable();
        [$status, $type] = $this->classify($e);

        $payload = [
            'error' => [
                'type' => $type,
                'message' => $status < 500 ? $e->getMessage() : 'Internal server error',
                'correlationId' => $request->headers->get('X-Correlation-Id', '—'),
            ],
        ];

        $event->setResponse(new JsonResponse($payload, $status));
    }

    /** @return array{int, string} */
    private function classify(Throwable $e): array
    {
        return match (true) {
            $e instanceof ValidationFailedException => [Response::HTTP_UNPROCESSABLE_ENTITY, 'validation_error'],
            $e instanceof AccessDeniedException,
            $e instanceof SecurityAccessDeniedException => [Response::HTTP_FORBIDDEN, 'forbidden'],
            $e instanceof NotFoundException => [Response::HTTP_NOT_FOUND, 'not_found'],
            $e instanceof DomainException => [Response::HTTP_CONFLICT, 'domain_error'],
            $e instanceof HttpExceptionInterface => [$e->getStatusCode(), 'http_error'],
            default => [Response::HTTP_INTERNAL_SERVER_ERROR, 'server_error'],
        };
    }
}
