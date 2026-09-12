using Policy.Domain.Exceptions;

namespace Policy.Domain.Entities;

public class Customer
{
    public Guid Id { get; set; }
    public required string FirstName { get; set; }
    public required string LastName { get; set; }
    public required string Email { get; set; }
    public DateOnly DateOfBirth { get; set; } // PII — protected per ARCHITECTURE.md §2.5

    public ICollection<InsurancePolicy> Policies { get; set; } = [];
    public ICollection<Quote> Quotes { get; set; } = [];

    public string FullName => $"{FirstName} {LastName}";

    public int AgeOn(DateOnly date)
    {
        var age = date.Year - DateOfBirth.Year;
        if (date < DateOfBirth.AddYears(age))
        {
            age--;
        }

        return age;
    }

    public static Customer Create(string firstName, string lastName, string email, DateOnly dateOfBirth, DateOnly today)
    {
        var customer = new Customer
        {
            Id = Guid.NewGuid(),
            FirstName = firstName.Trim(),
            LastName = lastName.Trim(),
            Email = email.Trim().ToLowerInvariant(),
            DateOfBirth = dateOfBirth,
        };

        if (customer.AgeOn(today) < 18)
        {
            throw new DomainException("Customers must be at least 18 years old.");
        }

        return customer;
    }
}
