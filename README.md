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

## Testing

Run the full unit test suite:

```bash
./gradlew test
```
