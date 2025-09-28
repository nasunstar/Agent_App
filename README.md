# AI Personal Assistant (Android)

## Local Database Schema

```
users                 notes                    contacts
-----                 -----                    --------
id (PK)               id (PK)                  id (PK)
name                  user_id (FK→users.id)    name
email                 title                    email
created_at            body                     phone
                      created_at               meta_json
                      updated_at

event_types           events                   event_details
-----------           ------                   -------------
id (PK)               id (PK)                  id (PK)
type_name (unique)    user_id (FK)             event_id (FK→events.id)
                      type_id (FK→event_types) description
                      title
                      start_at
                      end_at
                      location
                      status

 event_notifications          auth_tokens                   ingest_items
 --------------------         -----------                   ------------
 id (PK)                      provider (PK)                 id (PK)
 event_id (FK→events.id)      account_email                 source
 notify_at                    access_token (secure key)     type
 channel                      refresh_token (secure key)    title
                              scope                         body
                              expires_at                    timestamp
                              server_auth_code              due_date
                              id_token (secure key)         confidence
                              updated_at                    meta_json
```

- `ingest_items` includes indices on `timestamp` and `due_date` to support time based queries.
- Full-text search tables are intentionally omitted in this iteration per project direction.
- Database version 2 migrates the `auth_tokens` table to record account metadata while delegating raw token
  values to the encrypted preferences layer.

## Google Sign-In & Secure Storage

- `GoogleAuthClient` wraps Play Services' sign-in flow and generates placeholder access/refresh tokens from the
  received `serverAuthCode`. No real network calls or secrets are committed.
- `AuthRepository` stores token metadata in Room and persists the opaque token values through `CryptoPrefs`, which
  is backed by `EncryptedSharedPreferences` and the Android Keystore.
- `LoginViewModel` and the Compose-based `LoginScreen` expose a simple UI to trigger sign-in, show loading/error
  states, and allow signing out. Provide your own OAuth client id via `GoogleAuthClient.DEFAULT_WEB_CLIENT_ID`.

## Testing

Run the full unit test suite:

```bash
./gradlew test
```
