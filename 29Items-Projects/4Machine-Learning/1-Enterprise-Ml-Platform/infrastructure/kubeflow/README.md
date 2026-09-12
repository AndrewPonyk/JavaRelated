# Kubeflow on EKS

Manifests / kustomize overlays for installing Kubeflow Pipelines and wiring it
to the platform.

## TODO
- [ ] Pin a Kubeflow manifests release and add a `kustomization.yaml` overlay.
- [ ] Configure the pipeline artifact store to the platform S3 bucket.
- [ ] Bind pipeline pods to an IRSA role with S3 + SageMaker + MLflow access.
- [ ] Provision GPU node pools via Karpenter (scale-to-zero when idle).
- [ ] Schedule the recurring drift-check pipeline (see `pipelines/components`).

Compile and upload a pipeline:

```bash
python pipelines/training_pipeline.py     # -> training_pipeline.yaml
# then upload training_pipeline.yaml via the KFP UI or `kfp pipeline upload`
```
