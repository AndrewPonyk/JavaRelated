{#-
  Standard custom-schema override:
    prod  → use the custom schema name as-is  (analytics.staging, analytics.marts, ...)
    other → prefix with the target schema      (dev_andrii_staging, ci_pr_42_marts, ...)
  so developers and CI never collide with production schemas.
-#}
{% macro generate_schema_name(custom_schema_name, node) -%}

    {%- set default_schema = target.schema -%}

    {%- if custom_schema_name is none -%}
        {{ default_schema }}
    {%- elif target.name == 'prod' -%}
        {{ custom_schema_name | trim }}
    {%- else -%}
        {{ default_schema }}_{{ custom_schema_name | trim }}
    {%- endif -%}

{%- endmacro %}
