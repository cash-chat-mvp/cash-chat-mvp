# 채팅 SSE — nginx HTTP/2 `RST_STREAM(INTERNAL_ERROR)`로 응답 종료 직후 스트림 끊김

> 대상: BE / 인프라 담당자
> 작성: FE (feature/CC-349)
> 상태: FE 측 우회 적용 완료, **서버 측 정식 수정 필요**

## 한 줄 요약

채팅 응답(토큰)은 정상적으로 전부 수신되지만, **응답이 끝나는 즉시 nginx가 HTTP/2 스트림을
`RST_STREAM(INTERNAL_ERROR)`로 리셋**한다. 클라이언트(OkHttp)는 이를 `StreamResetException`으로
받아 정상 종료를 "오류"로 처리하고, 매번 "응답이 끊겼어요. 다시 시도해주세요." UI가 뜬다.

## 증상

- 채팅을 보낼 때마다 **항상** 재시도/연결 끊김 UI 노출
- 토큰은 화면에 정상 렌더링됨 → 스트리밍 자체는 동작
- 응답이 짧든 길든 **완료 직후** 발생

## 근거 (FE 진단 로그)

```
CashChatStream: response status=200  contentType=text/event-stream
CashChatStream: line[1]="event:message"
CashChatStream: line[2]="data:Hello"
CashChatStream: line[3]=""
CashChatStream: line[4]="event:message"
CashChatStream: line[5]="data:!"
CashChatStream: Exception StreamResetException message=stream was reset: INTERNAL_ERROR assistantAdded=true
okhttp3.internal.http2.StreamResetException: stream was reset: INTERNAL_ERROR
    at okhttp3.internal.http2.Http2Stream$FramingSource.read(Http2Stream.kt:355)
    at io.ktor.client.engine.okhttp.OkHttpEngineKt$toChannel$1$...
```

핵심 포인트:

- `200 / text/event-stream` 응답 정상 수신, 토큰(`Hello`, `!`)도 정상 수신
- 스택트레이스가 `okhttp3.internal.http2.Http2Stream` → **클라이언트↔nginx 구간이 HTTP/2**
- 마지막 토큰 직후 `RST_STREAM(INTERNAL_ERROR, code=2)` 수신 → 정상 `END_STREAM`이 아님

## 원인 분석

`proxy_buffering off` 로 SSE를 **HTTP/2 클라이언트**에 중계할 때, 업스트림(백엔드) 응답이 끝나
업스트림 커넥션이 닫히는 시점에 nginx가 깨끗한 `END_STREAM` 대신 `RST_STREAM(INTERNAL_ERROR)`을
내보내는, nginx + HTTP/2 + SSE 조합에서 알려진 동작이다.

관련 설정 (PR [#185](https://github.com/cash-chat-mvp/cash-chat-mvp/pull/185)):

```nginx
location = /api/v1/chat/stream {
    proxy_pass __BACKEND_UPSTREAM__;
    proxy_http_version 1.1;          # 업스트림(nginx→BE) 구간
    proxy_set_header Connection "";  # 업스트림 keep-alive
    proxy_buffering off;
    proxy_cache off;
    proxy_read_timeout 60s;
}
```

- `proxy_http_version 1.1` / `Connection ""` 는 **nginx→백엔드** 구간 설정이다.
- **클라이언트→nginx** 구간은 TLS ALPN으로 **HTTP/2(h2)** 가 협상되며, RST_STREAM은 이 구간에서 발생한다.
- 백엔드 애플리케이션 오류라면 SSE `event: error` 로 내려오지 (ChatController의 `.onErrorResume`),
  전송 계층 `RST_STREAM` 으로 나타나지 않는다 → **전송/프록시 계층 문제**로 판단.

## 검증해 줄 것 (BE/인프라)

1. **nginx error log** 에서 해당 요청 시점의 `http2` 관련 메시지 확인
   (예: upstream prematurely closed, `RST_STREAM` 송신 로그 등).
2. **nginx 버전** 확인 — HTTP/2 + 무버퍼 SSE 관련 알려진 버그/회귀가 있는 버전인지.
3. 업스트림 응답 종료 방식 확인 — reactor-netty가 응답을 `Transfer-Encoding: chunked` 종료 청크로
   정상 마감하는지, keep-alive 커넥션이 어떻게 닫히는지.
4. 하트비트(`:keep-alive`, 15s) 머지 스트림이 payload 완료 시 **함께 정상 complete** 되는지
   (`withHeartbeat` 의 `Flux.merge` + `takeUntilOther`), 완료 이후 추가 신호가 새지 않는지.

## 서버 측 수정 후보 (택1 또는 병행)

- **A. SSE 응답에 명시적 종료/길이 신호 제공** — 스트림 끝에 `event: done` 같은 종료 이벤트를 보내
  클라이언트가 종료를 데이터로 인지하게 하고, 서버는 깨끗이 닫도록 보장.
- **B. nginx 측 HTTP/2 SSE 종료 처리 보정** — nginx 버전 업그레이드 또는 해당 location에서
  HTTP/2 관련 동작 조정. (정식 해법)
- **C. (최후) 해당 엔드포인트만 HTTP/2 비활성화** — SSE location에 한해 HTTP/1.1로 응답.

## FE 측 현재 조치 (우회)

- **채팅 HTTP 클라이언트를 HTTP/1.1로 강제** (OkHttp `protocols(listOf(Protocol.HTTP_1_1))`).
  HTTP/1.1은 스트림 리셋 개념이 없어 연결/청크 종료로 정상 마감되므로 `RST_STREAM`이 발생하지 않는다.
- 보조로 `HttpTimeout`(socket 60s, request 무제한)을 설정해 느린 응답 중 하트비트(15s)가
  소켓 read 타임아웃을 리셋하도록 함.
- 위치: `apps/frontend/shared/src/.../core/network/HttpClientEngine.android.kt`, `HttpClientFactory.kt`

> 이 우회는 Android(OkHttp/HTTP-2 경로)에서 확인된 문제에 대한 것이다. iOS(Darwin/NSURLSession)는
> HTTP 버전 강제가 제한적이라 동일 증상 시 별도 대응이 필요하며, **근본 해결은 서버 측**이 바람직하다.

## 참고

- 클라이언트 에러: `okhttp3.internal.http2.StreamResetException: stream was reset: INTERNAL_ERROR`
- 관련 PR: nginx SSE 설정 [#185](https://github.com/cash-chat-mvp/cash-chat-mvp/pull/185)
- 관련 코드: `apps/backend/.../chat/web/controller/ChatController.kt`,
  `ServerSentEventHeartbeat.kt`, `infra/deploy/nginx/.../conf.d/*.conf.template`

---

# 이슈 2 (별건) — 인증 엔드포인트 행(hang)으로 로그인/토큰 갱신 무응답

> 발견: 2026-06-17, FE 디바이스 테스트 중 (이슈 1과 무관한 별개 서버 장애)
> 상태: **서버 측 점검 필요** (FE 수정 사항 아님)

## 한 줄 요약

`POST /api/auth/guest`, `POST /api/auth/refresh` 등 **DB를 건드리는 auth POST 요청이 응답 없이
행(hang)** 된다. 앱이 시작 시 토큰을 못 받아 채팅을 포함한 인증 필요 기능이 전부 막힌다.

## 증상 (앱 로그)

```
okhttp.OkHttpClient  --> POST https://cashchat.duckdns.org/api/auth/refresh (55-byte body)
okhttp.OkHttpClient  <-- HTTP FAILED: java.net.SocketTimeoutException: timeout   (~10초 후)
okhttp.OkHttpClient  --> POST .../api/auth/refresh ...   (재시도 반복)
okhttp.OkHttpClient  <-- HTTP FAILED: java.io.IOException: Canceled
```

## 근거 (서버 직접 호출, 2026-06-17 06:49 UTC)

| 요청 | 결과 |
|---|---|
| `GET /` | **HTTP/2 401 즉시 응답** (`server: nginx/1.27.5`) — nginx·웹 계층 정상 |
| `POST /api/auth/refresh` (dummy token) ×2 | **15초+ 무응답** (`http_code=000`, timeout) |
| `POST /api/auth/guest?deviceToken=...` | **15초+ 무응답** (`http_code=000`, timeout) |

- `GET /` 는 Spring Security가 인증 없이 즉시 401을 돌려줌 → **웹 계층은 살아있음**.
- 더미 토큰이면 즉시 401/400을 줘야 할 auth POST가 모두 멈춤 → **DB/서비스 계층에서 막힘**.

## 분석

웹 계층은 응답하는데 **DB 접근이 필요한 요청만 행**되는 전형적 패턴. 후보:

- **DB 커넥션 풀 고갈** (반납 안 되는 커넥션 누수, 풀 사이즈 부족)
- **DB 다운/네트워크 단절** (MySQL 접속 대기 중 행)
- 스레드 풀 고갈 / 외부 호출(OAuth 등) 무한 대기

## 점검 요청 (BE/인프라)

1. 백엔드 컨테이너/프로세스 상태 및 재시작 필요 여부
2. **MySQL 상태 + 커넥션 풀** (HikariCP active/idle/pending 지표, `max-lifetime`/`connection-timeout`)
3. auth 요청이 막히는 지점 — 애플리케이션 로그 / 스레드 덤프
4. 풀 고갈이라면 누수 지점(트랜잭션 미종료, 커넥션 미반납) 추적

## FE 영향

- FE 수정 사항 아님. 서버 정상화 시 현재 빌드로 정상 동작 예상.
- 다만 앱의 auth OkHttp 클라이언트 read timeout이 ~10s라 서버 지연 시 즉시 실패·재시도 반복 →
  서버 복구 후에도 재시도 폭주(Canceled 루프)가 보이면 FE 재시도 정책 별도 검토 가능.
