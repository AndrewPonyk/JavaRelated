using InvoiceFactoring.ML.Models;
using Microsoft.ML;

namespace InvoiceFactoring.ML.Training;

/// <summary>
/// Trains the LightGBM default-risk model from a labelled CSV of historical invoice
/// outcomes and saves a versioned <c>.zip</c> artifact. Run offline (locally or as an AKS
/// Job on the spot node pool), then publish the artifact to Blob Storage for serving.
///
/// <para>NOTE: default events are rare, so evaluate on AUC / AUPRC and calibration —
/// never raw accuracy (TECH-NOTES §3.6).</para>
/// </summary>
public sealed class CreditRiskTrainer
{
    private readonly MLContext _ml = new(seed: 1);

    private static readonly string[] NumericFeatures =
    {
        nameof(CreditRiskInput.InvoiceAmount),
        nameof(CreditRiskInput.PaymentTermDays),
        nameof(CreditRiskInput.DaysUntilDue),
        nameof(CreditRiskInput.DebtorPriorInvoicesPaid),
        nameof(CreditRiskInput.DebtorPriorInvoicesDefaulted),
        nameof(CreditRiskInput.BorrowerMonthlyInflow),
        nameof(CreditRiskInput.BorrowerMonthlyOutflow),
        nameof(CreditRiskInput.BorrowerAverageDailyBalance),
        nameof(CreditRiskInput.BorrowerTenureMonths)
    };

    public void TrainAndSave(string trainingCsvPath, string outputModelPath)
    {
        IDataView data = _ml.Data.LoadFromTextFile<CreditRiskInput>(
            trainingCsvPath, hasHeader: true, separatorChar: ',');

        var split = _ml.Data.TrainTestSplit(data, testFraction: 0.2, seed: 1);

        var pipeline = _ml.Transforms.Categorical
            .OneHotEncoding("IndustryEncoded", nameof(CreditRiskInput.IndustryCode))
            .Append(_ml.Transforms.Concatenate("Features",
                NumericFeatures.Append("IndustryEncoded").ToArray()))
            .Append(_ml.Transforms.NormalizeMinMax("Features"))
            .Append(_ml.BinaryClassification.Trainers.LightGbm(
                labelColumnName: "Label",
                featureColumnName: "Features",
                numberOfLeaves: 31,
                numberOfIterations: 200));

        Console.WriteLine("Training credit-risk model...");
        var model = pipeline.Fit(split.TrainSet);

        var metrics = _ml.BinaryClassification.Evaluate(
            model.Transform(split.TestSet), labelColumnName: "Label");

        Console.WriteLine($"  AUC-ROC : {metrics.AreaUnderRocCurve:F4}");
        Console.WriteLine($"  AUC-PR  : {metrics.AreaUnderPrecisionRecallCurve:F4}");
        Console.WriteLine($"  F1      : {metrics.F1Score:F4}");

        // TODO: gate model promotion on AUC-PR + calibration thresholds before saving.
        _ml.Model.Save(model, data.Schema, outputModelPath);
        Console.WriteLine($"Saved model to {outputModelPath} (version {ModelInfo.Version}).");
    }
}
