using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Policy.Infrastructure.Migrations;

/// <inheritdoc />
public partial class AddReportingIndexes : Migration
{
    /// <inheritdoc />
    protected override void Up(MigrationBuilder migrationBuilder)
    {
        migrationBuilder.CreateIndex(
            name: "IX_Policies_EffectiveDate",
            table: "Policies",
            column: "EffectiveDate");

        migrationBuilder.CreateIndex(
            name: "IX_Policies_Status",
            table: "Policies",
            column: "Status");

        migrationBuilder.CreateIndex(
            name: "IX_Claims_FiledAtUtc",
            table: "Claims",
            column: "FiledAtUtc");

        migrationBuilder.CreateIndex(
            name: "IX_AuditEntries_Action_OccurredAtUtc",
            table: "AuditEntries",
            columns: new[] { "Action", "OccurredAtUtc" });
    }

    /// <inheritdoc />
    protected override void Down(MigrationBuilder migrationBuilder)
    {
        migrationBuilder.DropIndex(
            name: "IX_Policies_EffectiveDate",
            table: "Policies");

        migrationBuilder.DropIndex(
            name: "IX_Policies_Status",
            table: "Policies");

        migrationBuilder.DropIndex(
            name: "IX_Claims_FiledAtUtc",
            table: "Claims");

        migrationBuilder.DropIndex(
            name: "IX_AuditEntries_Action_OccurredAtUtc",
            table: "AuditEntries");
    }
}
