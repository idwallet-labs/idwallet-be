# idwallet-be

IDWallet의 자격증명과 제출 흐름을 담당하는 Kotlin Spring Boot MVC API입니다.

## 기술 스택

- Kotlin
- Spring Boot MVC
- PostgreSQL schema + Flyway migration
- Docker
- GitHub Actions CI

## API 범위

- 자격증명 목록 조회
- 자격증명 상세 조회
- 제출 요청 생성
- 제출 요청 상태 조회
- 제출 응답 승인

## 개인정보 경계

- 자격증명은 `payloadHash`와 metadata만 제공합니다.
- 원문 credential payload는 API로 반환하지 않습니다.
