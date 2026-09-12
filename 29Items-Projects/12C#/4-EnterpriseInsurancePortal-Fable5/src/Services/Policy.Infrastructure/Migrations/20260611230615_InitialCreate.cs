using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Policy.Infrastructure.Migrations;

/// <inheritdoc />
public partial class InitialCreate : Migration
{
    /// <inheritdoc />
    protected override void Up(MigrationBuilder migrationBuilder)
    {
        migrationBuilder.CreateTable(
            name: "AuditEntries",
            columns: table => new
            {
                Id = table.Column<long>(type: "bigint", nullable: false)
                    .Annotation("SqlServer:Identity", "1, 1"),
                Action = table.Column<string>(type: "nvarchar(100)", maxLength: 100, nullable: false),
                EntityName = table.Column<string>(type: "nvarchar(100)", maxLength: 100, nullable: false),
                EntityId = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                Actor = table.Column<string>(type: "nvarchar(200)", maxLength: 200, nullable: false),
                Details = table.Column<string>(type: "nvarchar(max)", nullable: true),
                OccurredAtUtc = table.Column<DateTime>(type: "datetime2", nullable: false)
            },
            constraints: table =>
            {
                table.PrimaryKey("PK_AuditEntries", x => x.Id);
            });

        migrationBuilder.CreateTable(
            name: "Customers",
            columns: table => new
            {
                Id = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                FirstName = table.Column<string>(type: "nvarchar(100)", maxLength: 100, nullable: false),
                LastName = table.Column<string>(type: "nvarchar(100)", maxLength: 100, nullable: false),
                Email = table.Column<string>(type: "nvarchar(320)", maxLength: 320, nullable: false),
                DateOfBirth = table.Column<DateOnly>(type: "date", nullable: false)
            },
            constraints: table =>
            {
                table.PrimaryKey("PK_Customers", x => x.Id);
            });

        migrationBuilder.CreateTable(
            name: "OutboxMessages",
            columns: table => new
            {
                Id = table.Column<long>(type: "bigint", nullable: false)
                    .Annotation("SqlServer:Identity", "1, 1"),
                EventType = table.Column<string>(type: "nvarchar(200)", maxLength: 200, nullable: false),
                Key = table.Column<string>(type: "nvarchar(100)", maxLength: 100, nullable: false),
                Payload = table.Column<string>(type: "nvarchar(max)", nullable: false),
                CreatedAtUtc = table.Column<DateTime>(type: "datetime2", nullable: false),
                ProcessedAtUtc = table.Column<DateTime>(type: "datetime2", nullable: true)
            },
            constraints: table =>
            {
                table.PrimaryKey("PK_OutboxMessages", x => x.Id);
            });

        migrationBuilder.CreateTable(
            name: "Quotes",
            columns: table => new
            {
                Id = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                CustomerId = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                BrokerId = table.Column<string>(type: "nvarchar(50)", maxLength: 50, nullable: false),
                ProductCode = table.Column<string>(type: "nvarchar(30)", maxLength: 30, nullable: false),
                StateCode = table.Column<string>(type: "nvarchar(2)", maxLength: 2, nullable: false),
                RiskFactorsJson = table.Column<string>(type: "nvarchar(max)", nullable: false),
                Premium = table.Column<decimal>(type: "decimal(18,2)", precision: 18, scale: 2, nullable: false),
                RateTableVersion = table.Column<string>(type: "nvarchar(50)", maxLength: 50, nullable: false),
                Status = table.Column<string>(type: "nvarchar(20)", maxLength: 20, nullable: false),
                IssuedAtUtc = table.Column<DateTime>(type: "datetime2", nullable: false),
                ExpiresAtUtc = table.Column<DateTime>(type: "datetime2", nullable: false)
            },
            constraints: table =>
            {
                table.PrimaryKey("PK_Quotes", x => x.Id);
                table.ForeignKey(
                    name: "FK_Quotes_Customers_CustomerId",
                    column: x => x.CustomerId,
                    principalTable: "Customers",
                    principalColumn: "Id",
                    onDelete: ReferentialAction.Cascade);
            });

        migrationBuilder.CreateTable(
            name: "Policies",
            columns: table => new
            {
                Id = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                PolicyNumber = table.Column<string>(type: "nvarchar(30)", maxLength: 30, nullable: false),
                QuoteId = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                CustomerId = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                AnnualPremium = table.Column<decimal>(type: "decimal(18,2)", precision: 18, scale: 2, nullable: false),
                EffectiveDate = table.Column<DateOnly>(type: "date", nullable: false),
                ExpiryDate = table.Column<DateOnly>(type: "date", nullable: false),
                Status = table.Column<string>(type: "nvarchar(20)", maxLength: 20, nullable: false),
                CancellationReason = table.Column<string>(type: "nvarchar(500)", maxLength: 500, nullable: true),
                CancelledAtUtc = table.Column<DateTime>(type: "datetime2", nullable: true)
            },
            constraints: table =>
            {
                table.PrimaryKey("PK_Policies", x => x.Id);
                table.ForeignKey(
                    name: "FK_Policies_Customers_CustomerId",
                    column: x => x.CustomerId,
                    principalTable: "Customers",
                    principalColumn: "Id",
                    onDelete: ReferentialAction.Restrict);
                table.ForeignKey(
                    name: "FK_Policies_Quotes_QuoteId",
                    column: x => x.QuoteId,
                    principalTable: "Quotes",
                    principalColumn: "Id",
                    onDelete: ReferentialAction.Restrict);
            });

        migrationBuilder.CreateTable(
            name: "Claims",
            columns: table => new
            {
                Id = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                PolicyId = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                Description = table.Column<string>(type: "nvarchar(2000)", maxLength: 2000, nullable: false),
                ClaimedAmount = table.Column<decimal>(type: "decimal(18,2)", precision: 18, scale: 2, nullable: false),
                ApprovedAmount = table.Column<decimal>(type: "decimal(18,2)", precision: 18, scale: 2, nullable: true),
                Status = table.Column<string>(type: "nvarchar(20)", maxLength: 20, nullable: false),
                FiledAtUtc = table.Column<DateTime>(type: "datetime2", nullable: false),
                ResolvedAtUtc = table.Column<DateTime>(type: "datetime2", nullable: true)
            },
            constraints: table =>
            {
                table.PrimaryKey("PK_Claims", x => x.Id);
                table.ForeignKey(
                    name: "FK_Claims_Policies_PolicyId",
                    column: x => x.PolicyId,
                    principalTable: "Policies",
                    principalColumn: "Id",
                    onDelete: ReferentialAction.Cascade);
            });

        migrationBuilder.CreateIndex(
            name: "IX_AuditEntries_EntityName_EntityId",
            table: "AuditEntries",
            columns: new[] { "EntityName", "EntityId" });

        migrationBuilder.CreateIndex(
            name: "IX_AuditEntries_OccurredAtUtc",
            table: "AuditEntries",
            column: "OccurredAtUtc");

        migrationBuilder.CreateIndex(
            name: "IX_Claims_PolicyId",
            table: "Claims",
            column: "PolicyId");

        migrationBuilder.CreateIndex(
            name: "IX_Customers_Email",
            table: "Customers",
            column: "Email",
            unique: true);

        migrationBuilder.CreateIndex(
            name: "IX_OutboxMessages_ProcessedAtUtc",
            table: "OutboxMessages",
            column: "ProcessedAtUtc",
            filter: "[ProcessedAtUtc] IS NULL");

        migrationBuilder.CreateIndex(
            name: "IX_Policies_CustomerId",
            table: "Policies",
            column: "CustomerId");

        migrationBuilder.CreateIndex(
            name: "IX_Policies_PolicyNumber",
            table: "Policies",
            column: "PolicyNumber",
            unique: true);

        migrationBuilder.CreateIndex(
            name: "IX_Policies_QuoteId",
            table: "Policies",
            column: "QuoteId",
            unique: true);

        migrationBuilder.CreateIndex(
            name: "IX_Quotes_BrokerId_Status",
            table: "Quotes",
            columns: new[] { "BrokerId", "Status" });

        migrationBuilder.CreateIndex(
            name: "IX_Quotes_CustomerId",
            table: "Quotes",
            column: "CustomerId");
    }

    /// <inheritdoc />
    protected override void Down(MigrationBuilder migrationBuilder)
    {
        migrationBuilder.DropTable(
            name: "AuditEntries");

        migrationBuilder.DropTable(
            name: "Claims");

        migrationBuilder.DropTable(
            name: "OutboxMessages");

        migrationBuilder.DropTable(
            name: "Policies");

        migrationBuilder.DropTable(
            name: "Quotes");

        migrationBuilder.DropTable(
            name: "Customers");
    }
}
