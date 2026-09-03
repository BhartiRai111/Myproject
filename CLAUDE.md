# StoreHub — Project Notes for Claude

## Debugging rule: Login, Registration, User Management, user records

Whenever there is an error or validation failure related to **Login,
Registration, User Management, or user records**, do not just fix it —
first identify and state the exact root cause, then fix it.

- Inspect the actual frontend value, the request payload, backend
  validation (DTO annotations), the entity, and the database record
  where relevant. Don't guess.
- Name the exact field and the exact reason. E.g.: "Registration failed
  because `mobile` was `06388724377` (11 digits); the backend requires
  exactly 10 digits." Not just "Validation failed."
- If the value has an unexpected character — stray space, leading/
  trailing whitespace, a stray quote (`'`/`"`), or other unexpected
  character — say so explicitly, and show expected vs. actual format
  when it's safe to do so.
- If a DB record has null/unexpected data, identify the specific
  record/field.
- If frontend and backend disagree on shape/format, explain the
  mismatch precisely.
- If there are multiple plausible causes, verify which one is actually
  happening before fixing — don't fix the first guess.
- After identifying the cause, fix it properly, and improve the UI
  error message so the user can see the real reason without opening
  devtools (see `Register.tsx` / `UserFormModal.tsx` for the pattern:
  surface the backend's `fieldErrors` map per-field via
  `isInvalid`/`Form.Control.Feedback` instead of only showing the
  generic top-level `message`).

## Notable backend validation (for quick reference when diagnosing)

- `mobile`: exactly 10 digits, `^[0-9]{10}$` (see `RegisterRequest`,
  `UserCreateRequest`, `UserUpdateRequest`).
- `password`: minimum 6 characters.
- `email`: standard email format (`@Email`).
- Field-level errors come back from the backend as
  `ApiError.fieldErrors` (a `Map<String, String>` keyed by DTO field
  name) whenever `MethodArgumentNotValidException` is thrown — see
  `GlobalExceptionHandler`. Always check this map on 400 responses
  before assuming the cause.
