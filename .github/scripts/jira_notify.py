import json, os, urllib.request, urllib.error
from typing import Dict

JIRA_BASE_URL = os.environ['JIRA_BASE_URL']
TODAY         = os.environ['TODAY']
D1            = os.environ['D1']
D3            = os.environ['D3']

with open('/tmp/all_issues.json') as f:
    all_data = json.load(f)
with open('/tmp/due_issues.json') as f:
    due_data = json.load(f)

if 'errorMessages' in all_data or 'errorMessages' in due_data:
    print('::error::Jira API 에러:', all_data.get('errorMessages') or due_data.get('errorMessages'))
    exit(1)

# ────────────────────────────────────────────
# 1. 전체 현황 통계
# ────────────────────────────────────────────
TARGET_LABELS  = ['FE', 'BE', '기획', 'infra']
STATUS_KEYS    = ['todo', 'in_progress', 'in_review', 'done']
STATUS_DISPLAY = {'todo': '📋 대기', 'in_progress': '🔄 진행', 'in_review': '👀 리뷰', 'done': '✅ 완료'}

status_counts: Dict[str, int]             = {k: 0 for k in STATUS_KEYS}
label_stats:   Dict[str, Dict[str, int]] = {lbl: {k: 0 for k in STATUS_KEYS} for lbl in TARGET_LABELS}
user_stats:    Dict[str, Dict[str, int]] = {}

def classify_status(fields):
    cat_key     = fields['status']['statusCategory']['key']
    status_name = fields['status']['name']
    if cat_key == 'done':
        return 'done'
    if status_name in ('검토 중', 'In Review', 'In review'):
        return 'in_review'
    if cat_key == 'indeterminate':
        return 'in_progress'
    return 'todo'

for issue in all_data.get('issues', []):
    skey = classify_status(issue['fields'])
    status_counts[skey] += 1
    for lbl in issue['fields'].get('labels', []):
        if lbl in label_stats:
            label_stats[lbl][skey] += 1
    assignee = (issue['fields'].get('assignee') or {}).get('displayName', '미배정')
    if assignee not in user_stats:
        user_stats[assignee] = {k: 0 for k in STATUS_KEYS}
    user_stats[assignee][skey] += 1

total      = sum(status_counts.values())
done_count = status_counts['done']
progress   = int(done_count / total * 100) if total > 0 else 0
bar_filled = progress // 10
prog_bar   = '█' * bar_filled + '░' * (10 - bar_filled)

# ── 상태별 텍스트 ──
status_text = '  |  '.join(
    f"{STATUS_DISPLAY[k]} **{status_counts[k]}**개"
    for k in STATUS_KEYS
)

# ── 레이블별 텍스트 ──
label_lines = []
for lbl in TARGET_LABELS:
    s = label_stats[lbl]
    if sum(s.values()) == 0:
        continue
    done_lbl  = s['done']
    total_lbl = sum(s.values())
    prog_lbl  = int(done_lbl / total_lbl * 100) if total_lbl > 0 else 0
    parts = [f"{STATUS_DISPLAY[k]} {s[k]}" for k in STATUS_KEYS if s[k] > 0]
    label_lines.append(f"`{lbl}` ({prog_lbl}%)  {' / '.join(parts)}")

label_text = '\n'.join(label_lines) if label_lines else '레이블 없음'

# ── 담당자별 텍스트 ──
user_lines = []
for name, s in sorted(user_stats.items(), key=lambda x: -sum(x[1].values())):
    if name == '미배정' and sum(s.values()) == 0:
        continue
    total_usr = sum(s.values())
    done_usr  = s['done']
    prog_usr  = int(done_usr / total_usr * 100) if total_usr > 0 else 0
    active_parts = [f"{STATUS_DISPLAY[k]} {s[k]}" for k in STATUS_KEYS if s[k] > 0]
    user_lines.append(f"`{name}` ({prog_usr}%)  {' / '.join(active_parts)}")

user_text = '\n'.join(user_lines) if user_lines else '담당자 없음'

# ── 요약 embed ──
summary_embed = {
    'color': 5793266,
    'title': '📊 Cash Chat 프로젝트 현황',
    'fields': [
        {
            'name': f'진척률  {progress}%  ({done_count} / {total})',
            'value': f'`{prog_bar}`',
            'inline': False,
        },
        {
            'name': '전체 상태',
            'value': status_text,
            'inline': False,
        },
        {
            'name': '레이블별 현황',
            'value': label_text,
            'inline': False,
        },
        {
            'name': '담당자별 현황',
            'value': user_text,
            'inline': False,
        },
    ],
    'footer': {'text': 'Cash Chat · Jira 자동 알림'},
    'timestamp': f'{TODAY}T00:00:00Z',
}

# ────────────────────────────────────────────
# 2. 마감 현황 대시보드 (담당자별 집계)
# ────────────────────────────────────────────
DUE_SLOTS   = ['overdue', 'today', 'd1', 'd3']
DUE_DISPLAY = {
    'overdue': '🚨 초과',
    'today':   '🔴 오늘',
    'd1':      '🟠 D-1',
    'd3':      '🟡 D-3',
}

due_issues:    list                      = due_data.get('issues', [])
due_by_user:   Dict[str, Dict[str, int]] = {}
total_by_slot: Dict[str, int]            = {s: 0 for s in DUE_SLOTS}

for issue in due_issues:
    f        = issue['fields']
    assignee = (f.get('assignee') or {}).get('displayName', '미배정')
    duedate  = f.get('duedate', '')

    if duedate < TODAY:
        slot = 'overdue'
    elif duedate == TODAY:
        slot = 'today'
    elif duedate == D1:
        slot = 'd1'
    else:
        slot = 'd3'

    total_by_slot[slot] += 1
    if assignee not in due_by_user:
        due_by_user[assignee] = {s: 0 for s in DUE_SLOTS}
    due_by_user[assignee][slot] += 1

# ── 마감 현황 embed ──
if due_by_user:
    header_parts = [
        f"{DUE_DISPLAY[s]} **{total_by_slot[s]}**건"
        for s in DUE_SLOTS if total_by_slot[s] > 0
    ]
    header_line = '  |  '.join(header_parts)

    def sort_key(item):
        s = item[1]
        return -(s['overdue'] * 1000 + s['today'] * 100 + s['d1'] * 10 + s['d3'])

    user_due_lines = []
    for name, s in sorted(due_by_user.items(), key=sort_key):
        parts = [
            f"{DUE_DISPLAY[slot]} {s[slot]}건"
            for slot in DUE_SLOTS if s[slot] > 0
        ]
        user_due_lines.append(f"`{name}`  {'  |  '.join(parts)}")

    due_embed = {
        'color': 15158332 if total_by_slot['overdue'] > 0 else 15105570,
        'title': '⏰ 마감 현황',
        'fields': [
            {
                'name': '전체',
                'value': header_line,
                'inline': False,
            },
            {
                'name': '담당자별',
                'value': '\n'.join(user_due_lines),
                'inline': False,
            },
        ],
        'footer': {'text': 'Cash Chat · Jira 자동 알림'},
    }
    all_embeds = [summary_embed, due_embed]
else:
    all_embeds = [summary_embed]

# ────────────────────────────────────────────
# 3. 메시지 분리 구성
# ────────────────────────────────────────────
overdue_count = total_by_slot['overdue']
upcoming      = len(due_issues) - overdue_count

due_parts = []
if overdue_count:
    due_parts.append(f'🚨 **마감 지난 이슈 {overdue_count}건**')
if upcoming:
    due_parts.append(f'⏰ **마감 임박 {upcoming}건**')
due_summary = '  |  '.join(due_parts) if due_parts else '✅ 마감 이슈 없음'

# 메시지 1: 프로젝트 현황 대시보드
msg1 = {'embeds': [summary_embed]}

# 메시지 2: 마감 현황 (마감 이슈 있을 때만)
msg2 = {'content': f'> {due_summary}', 'embeds': [due_embed]} if due_by_user else None

# ────────────────────────────────────────────
# 4. Discord 전송 (헬퍼 함수)
# ────────────────────────────────────────────
webhook_url = os.environ.get('DISCORD_WEBHOOK', '')
if not webhook_url:
    print('DISCORD_WEBHOOK 없음 — 알림 건너뜀')
    exit(0)

def send_discord(payload_dict: dict, label: str) -> None:
    payload = json.dumps(payload_dict)
    req = urllib.request.Request(
        webhook_url,
        data=payload.encode(),
        headers={
            'Content-Type': 'application/json',
            'User-Agent': 'DiscordBot (https://github.com/cash-chat-mvp/cash-chat-mvp, 1.0)',
        },
        method='POST',
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            if resp.status < 200 or resp.status >= 300:
                raise RuntimeError(f'HTTP {resp.status}')
        print(f'Discord 전송 완료: {label}')
    except urllib.error.HTTPError as e:
        body = e.read().decode('utf-8', errors='replace')
        print(f'::error::Discord 전송 실패 [{label}]: HTTP {e.code} {e.reason}')
        print(f'::error::Discord 응답 본문: {body}')
        exit(1)
    except urllib.error.URLError as e:
        print(f'::error::Discord 전송 실패 [{label}] (네트워크): {e}')
        exit(1)

send_discord(msg1, '프로젝트 현황')
if msg2:
    send_discord(msg2, '마감 현황')
