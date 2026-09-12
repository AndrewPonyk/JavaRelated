# Local/self-managed Elasticsearch image.
# LTR_MODE=off and LTR_MODE=native need nothing extra. For LTR_MODE=plugin,
# uncomment the RUN below with the plugin release that EXACTLY matches the ES
# version (TECH-NOTES §3.6 #1) — releases:
#   https://github.com/o19s/elasticsearch-learning-to-rank/releases
# On Elastic Cloud this image is not used; use LTR_MODE=native there.

FROM docker.elastic.co/elasticsearch/elasticsearch:8.14.3

# RUN bin/elasticsearch-plugin install --batch \
#     https://github.com/o19s/elasticsearch-learning-to-rank/releases/download/<version>/ltr-plugin-<version>-es8.14.3.zip
