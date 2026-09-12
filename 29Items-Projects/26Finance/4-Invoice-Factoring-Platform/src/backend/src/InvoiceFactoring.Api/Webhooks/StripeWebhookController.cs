using InvoiceFactoring.Application.Features.Payments.Commands.ConfirmPayout;
using InvoiceFactoring.Infrastructure.Payments.Stripe;
using MediatR;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Options;
using Stripe;

namespace InvoiceFactoring.Api.Webhooks;

/// <summary>
/// Receives Stripe webhooks. The signature is verified against the webhook secret, and the
/// event is the source of truth for payment state — never the client (TECH-NOTES §3.6).
/// </summary>
[ApiController]
[Route("api/webhooks/stripe")]
[AllowAnonymous] // authenticated by Stripe signature, not by JWT
public sealed class StripeWebhookController : ControllerBase
{
    private readonly StripeOptions _options;
    private readonly ISender _mediator;
    private readonly ILogger<StripeWebhookController> _logger;

    public StripeWebhookController(
        IOptions<StripeOptions> options,
        ISender mediator,
        ILogger<StripeWebhookController> logger)
    {
        _options = options.Value;
        _mediator = mediator;
        _logger = logger;
    }

    [HttpPost]
    public async Task<IActionResult> Handle(CancellationToken ct)
    {
        var payload = await new StreamReader(Request.Body).ReadToEndAsync(ct);

        Event stripeEvent;
        try
        {
            stripeEvent = EventUtility.ConstructEvent(
                payload,
                Request.Headers["Stripe-Signature"],
                _options.WebhookSecret);
        }
        catch (StripeException ex)
        {
            _logger.LogWarning(ex, "Rejected Stripe webhook with invalid signature.");
            return BadRequest();
        }

        // TODO: persist stripeEvent.Id to a processed-events store to dedupe replays.
        switch (stripeEvent.Type)
        {
            case "payout.paid":
            case "payout.failed":
                var payout = (Payout)stripeEvent.Data.Object;
                await _mediator.Send(
                    new ConfirmPayoutCommand(payout.Id, Succeeded: stripeEvent.Type == "payout.paid"), ct);
                break;

            default:
                _logger.LogInformation("Unhandled Stripe event type {Type}.", stripeEvent.Type);
                break;
        }

        return Ok();
    }
}
