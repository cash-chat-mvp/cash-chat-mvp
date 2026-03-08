> **Git Work Flow**

`Branching 전략`

\* 안정적인 서비스를 배포하고, 작업 상황 통합 및 변경 사항을 확인합니다.

\- main : 안정적인 서비스를 배포

\- dev : 작업 상황을 통합하고, 변경 사항을 확인

`Fork-Based Collaboration` 

\* 저장소(Repository)를 개인 작업 공간으로 복제(Fork)하여 권한을 분리하고 독립적으로 개발합니다.

\- topic : 새로운 기능을 구현하거나 설계를 진행

\- hot-fix : 긴급하게 장애를 위한 수정이 필요한 경우

<br>

> **Commit Message 규칙** 

``` markdown
[ISSUE-#] type : description
```

\* Commit Message는 위의 형식을 준수해야 합니다.

\- feat : 기능 구현

\- fix : 오류 수정

\- refactor : 코드 Refactoring

\- docs : 문서 작성

\- chore : 기타 (Package 관리, Build 설정 등)

<br>

> **병합 요청 (Merge Request) 방법** 

``` markdown
`제목 (Title)`
[ISSUE-#] Summary

`설명 (Description)`
* 배경 (Context)
* 변경 사항 (Change)
- 세부 사항 (Detail)
```

\* 제목에 Jira의 Issue를 반드시 포함하세요.

\- 설명에 배경, 변경 사항, 세부 사항 등을 작성합니다.

<br>

> **작업 흐름 (Work Flow)** 

\* GitHub Actions를 통해 Jira와 연동되어 Issue의 상태가 갱신됩니다.

\- GitHub에서 병합 요청 시, Jira의 Issue가 검토 중(In Review) 상태로 전환됩니다.

\- GitHub에서 검토한 후, 병합된 경우 Jira의 Issue가 완료(Done) 상태로 전환됩니다.

\- GitHub에서 검토를 통과하지 못해 수정 요청(Change Requested)을 받거나, 병합 없이 닫히는(Close) 경우 Jira의 Issue가 진행 중(In Progress) 상태로 다시 전환됩니다.

<br>

> **참고** 

GitHub의 Issue 기능은 사용하지 않으며, Jira를 통해 작업이 관리됩니다.