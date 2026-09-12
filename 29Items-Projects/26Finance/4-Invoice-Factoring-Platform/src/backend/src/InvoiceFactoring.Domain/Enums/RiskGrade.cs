namespace InvoiceFactoring.Domain.Enums;

/// <summary>
/// Discrete risk band derived from the model's probability of default (PD).
/// Drives the advance rate and discount fee in the pricing rules.
/// </summary>
public enum RiskGrade
{
    A = 0, // lowest PD — best advance rate / lowest fee
    B = 1,
    C = 2,
    D = 3,
    F = 4  // declined
}
