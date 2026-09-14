# Validation report contract v1

The JSON report is intentionally small and independent of the validator library.
It is written to standard output when the CLI is called with `--report JSON`.

```json
{
  "status": "INVALID",
  "valid": false,
  "findings": [
    {
      "instancePath": "/quantity",
      "schemaPath": "/properties/quantity/type",
      "keyword": "type",
      "message": "..."
    }
  ]
}
```

The contract guarantees:

- `status` is `VALID` or `INVALID`;
- `valid` is consistent with `status`;
- `findings` is empty for a valid result;
- each finding contains stable JSON Pointer paths, the JSON Schema keyword, and a human-readable message;
- findings are sorted by instance path, schema path, keyword, and message;
- operational problems such as unreadable files or invalid schemas are not validation findings and return exit code `2`.
