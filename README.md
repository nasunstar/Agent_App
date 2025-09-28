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

Phase 3 introduces a Jetpack Compose driven home screen and a Gmail ingestion client powered by Retrofit + Kotlinx Serialization. To exercise the flow:

1. Complete the Google OAuth authorization code flow separately and obtain an access token with the `https://www.googleapis.com/auth/gmail.readonly` scope. A refresh token is optional but can be stored for future automation.
2. Launch the Android app. The Compose home screen contains two cards: **Google 로그인 설정** for credential storage and **Gmail 수집함** for inbox monitoring. Enter the access token (and optional refresh token, scope, or expiry) and press **토큰 저장**. The token metadata is persisted in Room for reuse.
3. Press **최근 20개 동기화** within the **Gmail 수집함** card. The app calls the Gmail REST API, retrieves the most recent 20 messages, and upserts them into the `ingest_items` table using the Gmail message ID as the primary key.
4. Stored messages render immediately in the Compose list with subject, snippet, and received timestamp. If Gmail returns `401 Unauthorized`, the UI surfaces a snackbar prompting the user to refresh credentials.

> ⚠️ **Security note:** Real deployments must exchange the Google server auth code server-side and load tokens into the app's encrypted storage. The manual token entry workflow above is provided strictly for local development.

## Testing

Run the full unit test suite (includes Gmail repository coverage):

```bash
./gradlew test
```
