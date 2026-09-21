# HTTP profile contract v1

The HTTP adapter sends one optional local JSON request body and validates one
JSON response. The profile is a local JSON document with `format: "http"` and
`version: 1`.

```json
{
  "format": "http",
  "version": 1,
  "method": "POST",
  "url": "http://127.0.0.1:8080/deliveries",
  "requestHeaders": {
    "Content-Type": "application/json",
    "Accept": "application/json"
  },
  "requestSchema": "delivery-request.schema.json",
  "expectedStatus": 201,
  "requiredResponseHeaders": ["Content-Type", "X-Request-Id"],
  "responseContentType": "application/json",
  "responseSchema": "delivery-response.schema.json",
  "timeoutSeconds": 5
}
```

## Fields

- `method` is one of `GET`, `POST`, `PUT`, `PATCH`, or `DELETE`.
- `url` must use `http` or `https`, contain a host, and contain neither
  credentials nor a fragment.
- `requestHeaders` is optional. Header names must be valid HTTP token names;
  values must be single-line strings.
- `requestSchema` is optional and must be a relative path below the profile
  directory. When present, `--input` is required and is validated before the
  request is sent.
- `expectedStatus` is the one expected status code from 100 through 599.
- `requiredResponseHeaders` is an optional case-insensitive list of headers.
- `responseContentType` is optional and compares the response media type while
  ignoring parameters such as `charset=utf-8`.
- `responseSchema` is required and must be a relative local path below the
  profile directory. The response body must be UTF-8 JSON and is validated
  against this schema.
- `timeoutSeconds` is optional, defaults to `10`, and is limited to `1..120`.

Schema references are local files only. HTTP redirects are not followed.
Request header values and response bodies are never written into the report or
error output.

## Result semantics

The shared report contract remains unchanged. Contract violations return
`INVALID` with stable paths such as:

- `/request/body/quantity` for request-schema findings;
- `/response/status` for an unexpected status;
- `/response/headers/X-Request-Id` for a missing response header;
- `/response/body/accepted` for a response-schema finding.

Transport failures, invalid profiles, unreadable files, malformed request JSON,
and request timeouts return exit code `2` without a validation report. A
malformed response body is a response contract finding and returns `INVALID`.
