# 시스템 설계

## 1. 기본 구조

```text
Next.js frontend
  -> Spring Boot API
      -> PostgreSQL
      -> local storage
      -> Java conversion module
```

## 2. 기술 결정

- 프론트엔드는 Next.js와 TypeScript를 사용한다.
- 백엔드는 Spring Boot와 Gradle wrapper를 사용한다.
- 데이터베이스는 PostgreSQL을 사용한다.
- DB 마이그레이션은 Flyway를 사용한다.
- PDF 텍스트 추출은 Apache PDFBox를 우선 사용한다.
- DOCX 생성은 Apache POI를 우선 사용한다.
- DOCX 렌더링 페이지 수 검증은 Microsoft Word 페이지 통계, LibreOffice headless PDF export, PDFBox 페이지 카운트 도구를 사용한다.
- 변환 엔진은 백엔드 내부 모듈로 시작하되, 품질 한계가 확인되면 별도 worker로 분리한다.

## 3. 포트 정책

개발 환경 포트는 13800번대를 사용한다.

- Backend API: `13800`
- Frontend Web: `13801`
- PostgreSQL host port: `13832`

## 4. MVP 작업 흐름

1. 사용자가 프론트엔드에서 PDF를 업로드한다.
2. Spring Boot API가 파일을 로컬 저장소에 저장한다.
3. PostgreSQL에 `stored_files`와 `conversion_jobs` 레코드를 생성한다.
4. 비동기 작업이 PDF 텍스트를 추출하고 DOCX 결과를 생성한다.
5. 결과 파일을 저장하고 작업 상태를 `COMPLETED`로 변경한다.
6. 사용자는 작업 상태를 조회한 뒤 결과 파일을 다운로드한다.

## 5. 주요 테이블

### stored_files

- 업로드 파일과 결과 파일의 메타데이터를 저장한다.
- 파일명, 저장 경로, MIME 타입, 크기, 만료 시간, 삭제 여부를 관리한다.

### conversion_jobs

- 변환 요청의 상태를 저장한다.
- 상태는 `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`를 사용한다.
- 원본 파일과 결과 파일을 참조한다.

## 6. 현재 한계

- OCR은 아직 구현하지 않았다.
- 이미지 기반 PDF는 안내 문구가 포함된 DOCX로 변환된다.
- 일반 PDF 레이아웃 복원은 아직 제한적이며, 추출 텍스트와 단순 표 기반 DOCX 생성을 우선한다.
- 1장 PDF는 편집 우선 변환에서 결과도 1장으로 유지하도록 불필요한 제목과 원본 전체 미리보기 삽입을 제한한다.
- `배달의민족 회원탈퇴 요청서.pdf`는 PDF 내부 한글 텍스트 매핑이 깨져 샘플 전용 편집 가능 DOCX 양식으로 재구성한다.
- 샘플 전용 양식은 정보주체/법적보호자 세로 병합, 유의사항 제목/본문 분리, 표 grid 및 셀 폭을 명시한다.
- LibreOffice 기준 DOCX to HWP 쓰기 export filter가 없어 HWP 직접 생성은 현재 지원하지 않는다.
- 파일 자동 삭제 배치는 아직 구현하지 않았다.
