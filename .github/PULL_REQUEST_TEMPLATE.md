> 제목 (Title)

```

[양식]
<Jira Ticket ID> : <Summary>

[예시]
PROJ-76 : Jira와 GitHub 상태 연동

```

---

`#in-review`

> 변경 사항 요약

*

```

[예시]
* Jira와 GitHub 상태 연동을 체계를 구축하였습니다.
* 기여 방법 문서(CONTRIBUTING.md)를 작성하였습니다.

```

> 확인 목록 (Check List)

병합 요청(Merge Request)을 하기 전에, 아래 항목을 확인해 주세요.

- [ ] **Local** 환경에서 **Build** 성공
- [ ] **Local** 환경에서 **Test** 성공
- [ ] 제목에 **Jira**에서 **Ticket**의 **ID**와 요약 포함
- [ ] 설명에 `#in-review` 태그 포함

> 참고

- **검토(Review) 후 병합(Merge)되는 경우**, \
Commit Message에 `#done` Tag를 포함하여 **Jira**에서 작업 상태를 `완료(DONE)`로 변경합니다.

```

[양식]

* Commit Message

Merge pull request #76 from user-name/branch-name (Auto Generated)

* Description

<Commit Message> (Auto Generated)

<Content>

#done

<Message>

[예시]

Merge pull request #76 from admin/proj-76-jira-github

chore: Jira와 GitHub 연동

* Jira와 GitHub 상태 연동을 확인했습니다.
* 이와 관련된 기여 방법 문서(CONTRIBUTING.md)를 확인했습니다.

#done

```

- **수정 요청(Request Changes)을 받거나, 병합 없이 닫히는(Close) 경우**, \
**Jira**에서 해당되는 작업의 상태를 다시 `진행 중(IN PROGRESS)` 상태로 변경합니다.

