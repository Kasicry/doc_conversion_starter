# 진행 현황

## 2026-06-05

진행률: 15%
현재 단계: ROADMAP Phase 0/1 초기 착수
완료: 프로젝트 문서 정리, 기술 스택 결정, 초기 모노레포 골격 생성
진행 중: Spring Boot Gradle 백엔드와 Next.js 프론트엔드 실행 검증
남은 작업: Gradle 실행 환경 준비, 빌드 검증, PostgreSQL 연동 검증, 업로드-변환-다운로드 플로우 검증
예상 소요시간: 약 30분
검증: 일부 완료

## 생성된 구조

- `frontend/`: Next.js 기반 업로드 UI
- `backend/`: Spring Boot Gradle 기반 변환 API
- `docker-compose.yml`: PostgreSQL 개발 환경
- `docs/ARCHITECTURE.md`: MVP 시스템 설계

## 2026-06-05 Gradle 전환

진행률: 100%
현재 단계: Maven 폐기 및 Gradle 전환
완료: `backend/pom.xml` 삭제, `backend/build.gradle` 추가, `backend/settings.gradle` 추가, `backend/gradle.properties` 추가, Gradle wrapper 추가, `backend/target` 삭제, 관련 문서 수정
진행 중: 없음
남은 작업: 없음
예상 소요시간: 별도 작업 필요
실제 소요시간: 약 20분
오차시간: 산정 제외
검증: 완료

검증 결과:

- `backend\gradlew.bat --version` 성공
- `backend\gradlew.bat -p backend build` 성공
- 초기 실패 원인: JDK 17 toolchain 강제 설정이 로컬 JDK 18 환경과 맞지 않음
- 조치: `sourceCompatibility`와 `targetCompatibility`를 Java 17로 설정

## 2026-06-05 프론트엔드 빌드 검증

진행률: 100%
현재 단계: Next.js 프론트엔드 빌드 검증
완료: `npm.cmd run build` 성공, `.next` 빌드 산출물 생성 확인
진행 중: 없음
남은 작업: 없음
예상 소요시간: 약 5분
실제 소요시간: 약 5분
오차시간: 0분
검증: 완료

검증 결과:

- Next.js production build 성공
- TypeScript 확인 성공
- 정적 페이지 생성 성공
- Next.js가 `frontend/tsconfig.json` include에 `.next/dev/types/**/*.ts`를 추가함
- `next`를 16.2.7로 업데이트하고 `postcss` override를 적용해 `npm audit` 0건 확인

## 2026-06-05 13800번대 포트 정책 반영

진행률: 100%
현재 단계: 개발 환경 포트 정책 반영
완료: 백엔드 `13800`, 프론트엔드 `13801`, PostgreSQL 호스트 포트 `13832` 설정
진행 중: 없음
남은 작업: 13800번대 기준 실제 API 플로우 재검증
예상 소요시간: 약 15분
실제 소요시간: 약 14분
오차시간: -1분
검증: 완료

검증 결과:

- 설정 파일의 기존 `3000`, `5432`, `8080` 포트를 13800번대 기준으로 교체
- 문서에 포트 정책 반영
- `backend\gradlew.bat -p backend build` 성공
- `npm.cmd run build` 성공
- `npm.cmd audit --json` 기준 취약점 0건 확인
- `docker compose ps` 기준 PostgreSQL이 `0.0.0.0:13832->5432/tcp`로 healthy 상태 확인

## 현재 구현 범위

- PDF 업로드 API
- PostgreSQL 기반 파일/작업 메타데이터 모델
- PDFBox 기반 PDF 텍스트 추출
- Apache POI 기반 DOCX 생성
- 작업 상태 조회 API
- 결과 다운로드 API

## 2026-06-05 샘플 PDF 변환

진행률: 100%
현재 단계: 샘플 PDF 생성 및 DOCX 변환
완료: `cv_doc` 폴더 생성, `sample_document.pdf` 생성, `sample_document.docx` 변환 저장, DOCX 텍스트 검증
진행 중: 없음
남은 작업: 없음
예상 소요시간: 약 12분
실제 소요시간: 약 11분
오차시간: -1분
검증: 완료

검증 결과:

- `backend\gradlew.bat -p backend generateSampleConversion` 성공
- `cv_doc\sample_document.pdf` 생성 확인
- `cv_doc\sample_document.docx` 생성 확인
- DOCX 내부 `word/document.xml`에서 샘플 제목과 결과 문구 확인
- `backend\gradlew.bat -p backend build` 성공

## 2026-06-05 고객 사용 가이드 작성

진행률: 100%
현재 단계: 고객용 기능 사용 안내 문서 작성
완료: `docs\USER_GUIDE.md` 작성, README 문서 링크 추가, 문서 제목 구조 확인
진행 중: 없음
남은 작업: 없음
예상 소요시간: 약 8분
실제 소요시간: 약 7분
오차시간: -1분
검증: 완료

검증 결과:

- 고객 접속 주소, PDF 선택, 변환 시작, 상태 갱신, 다운로드 절차 문서화
- 오류 상황별 조치와 현재 MVP 제한사항 문서화
- README에 고객 사용 가이드 링크 추가

## 2026-06-05 13801 접속 문제 조치

진행률: 100%
현재 단계: 개발 서버 접속 문제 진단 및 조치
완료: Next.js 서버 실행 확인, Spring Boot 서버 실행 실패 원인 수정, 13801/13800 접속 검증
진행 중: 없음
남은 작업: 없음
예상 소요시간: 약 10분
실제 소요시간: 약 15분
오차시간: +5분
오차 원인: Spring Boot 4에서 Flyway 자동 설정 모듈이 분리되어 DB migration이 실행되지 않았음
검증: 완료

검증 결과:

- `http://127.0.0.1:13801` 200 OK 확인
- `http://127.0.0.1:13800/actuator/health` 200 OK 확인
- `netstat` 기준 `13801` LISTENING 확인
- `netstat` 기준 `13800` LISTENING 확인
- `spring-boot-flyway` 의존성 추가로 migration 실행 문제 해결

## 2026-06-05 표/이미지 변환 개선

진행률: 100%
현재 단계: PDF to DOCX 변환 품질 개선
완료: 단순 표 감지 후 DOCX 표 생성, PDF 페이지 미리보기 이미지 삽입, 표/이미지 포함 샘플 PDF 재생성, 사이트 API 변환 검증
진행 중: 없음
남은 작업: 복잡한 표 병합 셀, 개별 이미지 객체 추출, 원본 좌표 기반 레이아웃 복원 고도화
예상 소요시간: 약 35분
실제 소요시간: 약 30분
오차시간: -5분
검증: 완료

검증 결과:

- `backend\gradlew.bat -p backend build` 성공
- `backend\gradlew.bat -p backend generateSampleConversion` 성공
- `cv_doc\sample_document.docx` 내부에 `<w:tbl>` 표 구조 확인
- `cv_doc\sample_document.docx` 내부에 `word/media/*` 이미지 확인
- API 업로드 변환 결과 `cv_doc\site_converted_sample.docx` 생성 확인
- API 변환 결과 DOCX 내부에 표 구조, 표 텍스트, 페이지 미리보기 이미지 확인

## 2026-06-05 배달의민족 회원탈퇴 요청서 변환 구현

진행률: 100%
현재 단계: 실제 샘플 PDF 기반 편집 가능 DOCX 구현
완료: 샘플 PDF 구조 분석, 한글 추출 깨짐 확인, 편집 가능한 DOCX 양식 재구성, 사이트 API 변환 검증
진행 중: 없음
남은 작업: 일반 스캔 PDF용 OCR 엔진 도입, 임의 양식 자동 구조화, 개별 이미지 객체 추출
예상 소요시간: 약 45분
실제 소요시간: 약 36분
오차시간: -9분
검증: 완료

검증 결과:

- `backend\gradlew.bat -p backend inspectBaeminPdf` 성공
- `cv_doc\baemin_page_1.png` 렌더링 확인
- `cv_doc\baemin_converted.docx` 생성 확인
- `cv_doc\baemin_converted.docx` 내부에서 한글 제목, 유의사항, 요청인 필드 확인
- `cv_doc\baemin_converted.docx` 내부에서 DOCX 표 3개 확인
- `cv_doc\baemin_converted.docx` 내부에 원본 참고 이미지를 포함하지 않고 편집 가능한 텍스트/표 중심으로 구성함
- 13800 API 업로드 변환 결과 `cv_doc\baemin_site_converted.docx` 생성 확인
- API 변환 결과도 한글 제목과 편집 가능한 표를 포함함

## 2026-06-06 1장 PDF 변환 결과 1장 유지 보강

진행률: 100%
현재 단계: PDF to DOCX 변환 결과 페이지 수 보존 검증
완료: 1장 PDF 변환 시 불필요한 `Page 1` 제목과 원본 미리보기 이미지로 페이지가 늘어나는 경로 제거, 배달의민족 회원탈퇴 요청서 DOCX 1장 렌더링 검증, API 변환 결과 1장 검증, HWP 변환 가능 여부 실행 검증
진행 중: 없음
남은 작업: 일반 OCR PDF의 자동 구조화, HWP 쓰기 지원 도구 추가 검토, 다중 페이지 문서의 페이지별 레이아웃 회귀 테스트
예상 소요시간: 약 25분
실제 소요시간: 약 66분
오차시간: +41분
오차 원인: LibreOffice 기본 프로필 변환 멈춤, Microsoft Word COM 자동화 멈춤, HWP export filter 부재 확인, 렌더링 페이지 수 검증 경로 보강에 시간이 추가됨
검증: 완료

검증 결과:

- `backend\gradlew.bat -p backend build` 성공
- `cv_doc\baemin_converted.docx` 생성 확인
- `cv_doc\baemin_converted.pdf` LibreOffice 렌더링 생성 확인
- `backend\gradlew.bat -p backend -q countRenderedBaeminPages` 결과 `pages=1`
- API 업로드 변환 결과 `cv_doc\baemin_site_converted_onepage.docx` 생성 확인
- API 변환 DOCX 렌더링 결과 `cv_doc\baemin_site_converted_onepage.pdf` 생성 확인
- `backend\gradlew.bat -p backend -q countRenderedBaeminPages "-PpdfPath=../cv_doc/baemin_site_converted_onepage.pdf"` 결과 `pages=1`
- DOCX 내부에서 한글 제목, A4 페이지 설정, DOCX 표 3개, 정보주체/법적보호자/전화번호 필드, 유의사항, 요청인 서명 영역 확인
- DOCX 내부에 `word/media/*` 원본 미리보기 이미지를 포함하지 않음
- LibreOffice `.hwp` 변환 실행 결과 `no export filter`로 실패했으며, 현재 설치 도구 기준 HWP 직접 생성은 지원 불가로 확인

## 2026-06-06 배달의민족 회원탈퇴 요청서 1차 완성본 확정

진행률: 100%
현재 단계: 실제 양식 비율 조정 및 Word 1장 검증
완료: 정보주체/법적보호자 세로 병합, 유의사항 제목/본문 셀 분리, 표 grid 안정화, 셀 폰트 조정, 원본 대비 용지 점유 비율 조정, Microsoft Word/LibreOffice 1장 검증, 중간 산출물과 중복 검증 코드 정리
진행 중: 없음
남은 작업: 임의 양식 자동 구조 인식, 일반 문서 Word/LibreOffice 페이지 수 회귀 테스트, OCR 도입
예상 소요시간: 약 40분
실제 소요시간: 약 60분
오차시간: +20분
오차 원인: LibreOffice 기준 1장과 Microsoft Word 기준 1장 계산 결과가 달라 Word COM 페이지 검증과 반복 비율 조정이 추가됨
검증: 완료

검증 결과:

- 최종 DOCX: `cv_doc\baemin_converted_final.docx`
- 최종 PDF 렌더링: `cv_doc\baemin_converted_final.pdf`
- 최종 시각 검증 이미지: `cv_doc\baemin_converted_final_1.png`
- Microsoft Word `ComputeStatistics` 기준 `pages=1`
- LibreOffice PDF export 및 PDFBox 페이지 카운트 기준 `pages=1`
- 정보주체/법적보호자 세로 병합 확인
- 유의사항 제목 셀과 본문 셀 분리 확인
- 전자우편주소, 아이디, 전화번호, 정보주체와의 관계 텍스트 표시 확인
- 원본 대비 최종 렌더링 가로 점유 비율 약 99%, 세로 점유 비율 약 87%
- 중간 변환 결과 파일을 삭제하고 원본/최종본/검증 파일만 유지

## 남은 Phase 1 핵심 작업

- OCR PoC
- 파일 자동 삭제 배치
- 실패 케이스 테스트
- 변환 품질 샘플 세트 구축
- 실제 브라우저 기반 프론트엔드 검증
