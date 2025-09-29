# AI Personal Assistant (Android)

## Configuration

Before running the app configure the following entries in `local.properties`:

- `GOOGLE_WEB_CLIENT_ID`: optional Web client ID used when requesting a server auth code from Google Sign-In. Leave as the default placeholder if you only need on-device token retrieval.
- `OPENAI_API_KEY`: stored locally and exposed to the app via `BuildConfig.OPENAI_API_KEY` for future OpenAI integrations.

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

 event_notifications          auth_tokens             ingest_items
 --------------------         -----------             ------------
 id (PK)                      provider (PK)           id (PK)
 event_id (FK→events.id)      access_token            source
 notify_at                    refresh_token           type
 channel                      scope                   title
                              expires_at              body
                                                       timestamp
                                                       due_date
                                                       confidence
                                                       meta_json
```

- `ingest_items` includes indices on `timestamp` and `due_date` to support time based queries.
- Full-text search tables are intentionally omitted in this iteration per project direction.
- Database version 1 ships without migrations; a dedicated container is in place for future upgrades.

## Gmail Ingestion (Phase 3)

The home screen now includes a Google Sign-In button that requests the `gmail.readonly` scope and fetches an access token directly on device using Play Services. When the sign-in flow completes successfully the token is persisted through `AuthRepository` and can be reused for Gmail synchronization. Manual token entry fields remain available as a fallback for development.

To sync messages:

1. Tap **Google 계정으로 로그인** inside the **Google 로그인 설정** card and complete the consent dialog. The access token is stored automatically.
2. (Optional) Provide a manually issued access token/refresh token and press **토큰 저장** if you need to test with external credentials.
3. Press **최근 20개 동기화** within the **Gmail 수집함** card. The Gmail REST API retrieves the newest 20 messages and upserts them into `ingest_items` using the Gmail message ID as the primary key.
4. The Compose list renders the subject, snippet, received timestamp, parsed due date (if present), and confidence score. 401 responses trigger a snackbar that prompts the user to reauthenticate.


## Natural Language Parsing (Phase 4)

Phase 4 introduces a lightweight intent and date parser that enriches each `ingest_items` record:

- `TimeResolver` converts phrases such as "내일 오전 10시", "다음 주 금요일", `9/30 14:00`, and Korean date formats into Asia/Seoul epoch milliseconds.
- `IngestItemParser` combines detected timestamps with intent keywords (회의, 마감, deadline, 등) to populate `dueDate` and a heuristic `confidence` score before persisting to Room.
- The Gmail list renders the derived due date and confidence so upcoming action items stand out immediately after ingestion.

The parser intentionally favours deterministic rules so it can run offline; it can be extended with additional patterns as new data sources are introduced.

## Testing

Run the full unit test suite (includes Gmail repository coverage):

```bash
./gradlew test
```
