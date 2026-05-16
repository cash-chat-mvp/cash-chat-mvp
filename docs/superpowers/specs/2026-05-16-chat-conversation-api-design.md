# Chat Conversation API Design

## Goal

CC-161 changes chat history from a raw message dump into GPT-style conversations: a user sees a list of chat rooms, opens one room to load its messages, and continues chatting in that same room.

## API Contract

- `POST /api/v1/chat/conversations`
  - Creates a conversation for the authenticated user.
  - Request: `{ "title": "English study tips" }`
  - Response: `{ "conversationId": 7, "title": "English study tips", "createdAt": "...", "updatedAt": "..." }`
- `GET /api/v1/chat/conversations`
  - Returns the authenticated user's conversation list ordered by most recently updated first.
  - Response item: `{ "conversationId": 7, "title": "English study tips", "lastMessage": "Study a little every day", "createdAt": "...", "updatedAt": "..." }`
- `GET /api/v1/chat/conversations/{conversationId}/messages`
  - Returns only the selected room's messages, ordered oldest to newest.
  - Response item: `{ "messageId": 10, "role": "USER", "content": "...", "status": "COMPLETED", "createdAt": "..." }`
- `POST /api/v1/chat/stream`
  - Continues requiring `conversationId`.
  - The backend validates ownership, saves the user message in that conversation, streams the assistant reply, and stores the final assistant message.

## New Chat Flow

When the user starts from the idle chat screen, the frontend creates a room first, then streams the first message:

1. `POST /api/v1/chat/conversations` with the first prompt as the title.
2. Save `conversationId` on the client.
3. `POST /api/v1/chat/stream` with `{ "conversationId": 7, "message": "English study tips" }`.

This avoids rooms created by merely opening the screen, but a stream failure after room creation can still leave an empty room. Cleanup or atomic create-and-send can be added later if the frontend needs strict empty-room prevention.

## Existing Chat Flow

1. `GET /api/v1/chat/conversations` for the side menu or chat history screen.
2. User selects a room.
3. `GET /api/v1/chat/conversations/{conversationId}/messages`.
4. Future sends call `POST /api/v1/chat/stream` with the selected `conversationId`.

## Security And Errors

- All conversation endpoints use the authenticated principal.
- A user cannot list, open, or stream into another user's conversation.
- Missing `conversationId` on stream remains `400 Bad Request`.
- Unknown or foreign `conversationId` values MUST return `404 Not Found`; fetch and authorization checks treat inaccessible conversations as not found to avoid leaking room existence.

## Testing

- Web MVC tests cover create/list/messages endpoints and stream request validation.
- Service tests cover ownership checks, list ordering assumptions, message mapping, and conversation timestamp updates when streaming.
