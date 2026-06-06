# PDF 문서 변환 서비스

PDF 파일을 수정 가능한 문서 형식으로 변환하는 웹 서비스 프로젝트다.

현재 1차 완성본은 `배달의민족 회원탈퇴 요청서.pdf`를 편집 가능한 DOCX 양식으로 변환하며, Microsoft Word와 LibreOffice Writer 기준 1장 결과를 유지한다.

## 기술 스택

- Frontend: Next.js, TypeScript
- Backend: Spring Boot, Gradle
- Database: PostgreSQL
- Conversion PoC: Apache PDFBox, Apache POI
- Page verification: Microsoft Word page statistics, LibreOffice headless export, Apache PDFBox

## 구조

```text
frontend/   Next.js 웹 앱
backend/    Spring Boot API 서버와 변환 모듈
docs/       PRD, ROADMAP, GOAL, 진행 문서
```

## 포트 정책

개발 환경 포트는 13800번대를 사용한다.

- Backend API: `http://localhost:13800`
- Frontend Web: `http://localhost:13801`
- PostgreSQL host port: `localhost:13832`

## 개발 실행

PostgreSQL:

```powershell
docker compose up -d postgres
```

Backend:

```powershell
cd backend
.\gradlew.bat bootRun
```

Frontend:

```powershell
cd frontend
npm install
npm run dev
```

## 검증

Backend:

```powershell
backend\gradlew.bat -p backend build
```

Frontend:

```powershell
cd frontend
npm run build
```

## 초기 API

- `POST /api/conversion-jobs`: PDF 업로드 및 변환 작업 생성
- `GET /api/conversion-jobs/{jobId}`: 변환 작업 상태 조회
- `GET /api/conversion-jobs/{jobId}/download`: 완료된 결과 파일 다운로드

## 문서

- [고객 사용 가이드](./docs/USER_GUIDE.md)
- [제품 요구사항](./docs/PRD.md)
- [로드맵](./docs/ROADMAP.md)
- [시스템 설계](./docs/ARCHITECTURE.md)
