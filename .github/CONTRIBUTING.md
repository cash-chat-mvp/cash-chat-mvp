> Git Branch 전략

`Repository Work Flow`
- **main** : 안정적인 서비스를 배포 (원본 레포지토리에서 직접 관리)
- **dev** : 작업 상황을 통합하고, 변경 사항을 확인 (원본 레포지토리에서 직접 작업)

`Forking Work Flow`
- **topic** : 새로운 기능을 구현하거나 설계를 진행 (개인 Fork에서 브랜치 생성 후 원본 레포지토리에 PR)
- **hot-fix** : 긴급하게 장애를 위한 수정이 필요한 경우 (개인 Fork에서 브랜치 생성 후 원본 레포지토리에 PR)

> 작업 흐름 (Work Flow)

**Jira**와의 원활한 연동을 위해 `Smart Commit`을 활용합니다.

- `main`, `dev` 관련 작업은 원본 레포지토리 브랜치에서 직접 진행합니다.
- `topic`, `hot-fix` 관련 작업은 개인 Fork 브랜치에서 작업 후 원본 레포지토리로 PR을 생성합니다.

- **병합 요청(Merge Request)을 하는 경우**, \
먼저 **Local** 환경에서 **Build** 및 **Test** 성공 여부를 확인합니다. \
그리고 제목에는 **Jira**에 명시된 **Ticket**의 **ID**와 작업 내용의 요약을 반드시 포함하고, \
설명에 상세 작업 내용을 명확하게 작성하고, `#in-review` Tag를 포함하여 **Jira**에서 작업 상태를 `검토 중(IN REVIEW)` 상태로 변경합니다.


```

[양식]

* Title

- [Jira Ticket ID] : <Summary>

* Description

<#Command>

<Message>

[예시]

[PROJ-76] Jira와 GitHub 상태 연동

#in-review

* Jira와 GitHub 상태 연동을 체계를 구축하였습니다.
* 기여 방법 문서(CONTRIBUTING.md)를 작성하였습니다.

```

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

> 참고

**GitHub**의 `issue` 기능은 사용하지 않습니다. 
