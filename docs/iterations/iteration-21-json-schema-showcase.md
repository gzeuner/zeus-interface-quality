# Iteration 21: JSON Schema showcase

Status: implemented.

Added visible JSON payload examples alongside the existing local JSON Schema:

- `delivery.json` is valid and contains a delivery ID and positive quantity.
- `delivery-invalid.json` has an empty ID, a zero quantity, and an undeclared
  property, demonstrating three distinct schema findings.
- English and German example guides and main READMEs include commands and
  expected exit codes for both samples.
- `JsonExampleTest` keeps the examples aligned with the schema and expected
  findings.
