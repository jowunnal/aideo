---
name: sync-portfolio
description: PDF 포트폴리오의 Aideo 앱 Key Points 내용을 documentation/review/ 마크다운 파일들에 동기화합니다.
disable-model-invocation: true
argument-hint: [pdf-path]
---

# PDF 포트폴리오 → 마크다운 리뷰 문서 동기화

PDF 포트폴리오 파일의 Aideo 앱 파트에서 각 Key Points 섹션의 내용을 `documentation/review/` 하위 마크다운 파일들에 동기화하는 작업입니다.

## PDF 파일 경로

- `$ARGUMENTS` 가 제공되면 해당 경로의 PDF를 사용합니다.
- 제공되지 않으면 프로젝트 루트의 `포트폴리오_안드로이드_황진호.pdf` 를 사용합니다.

## 동기화 대상 매핑

PDF의 Aideo 앱 파트 Key Points 섹션과 마크다운 파일의 매핑:

| PDF Key Point | 마크다운 파일 |
|---|---|
| 비디오의 음성 추출 → 텍스트 추론 → 번역까지의 End-to-End 파이프 라인을 구축 | `documentation/review/비디오_자막_End-to-End_Pipeline_구축.md` |
| SOLID 원칙을 기반으로 추론 관련 클래스 설계 | `documentation/review/SOLID_원칙_적용한_추론_클래스_설계.md` |

향후 새로운 Key Point와 마크다운 파일이 추가되면 위 매핑 테이블에 추가하세요.

## 실행 절차

1. **PDF 텍스트 추출**: `pdftotext` (poppler) 를 사용하여 PDF 전체 텍스트를 추출합니다. poppler가 설치되어 있지 않으면 `brew install poppler` 로 설치합니다.

2. **Key Points 영역 식별**: 추출된 텍스트에서 Aideo 앱 파트의 각 "Key Points" 섹션 경계를 파악합니다. 각 Key Point는 다음 Key Point 또는 다음 앱 파트(예: "아이모 잡학도구 App") 시작 전까지의 내용입니다.

3. **마크다운 파일 업데이트**: 각 Key Point의 PDF 내용을 대응하는 마크다운 파일에 반영합니다.
   - PDF 텍스트 내용을 **그대로** 마크다운 파일에 작성합니다. 내용을 추가하거나 삭제하지 않습니다.
   - 마크다운 파일에 기존에 있는 `<img>` 태그는 **절대 변경하지 않고 그대로 유지**합니다.
   - 마크다운 서식(제목, 볼드, 리스트 등)은 내용의 구조에 맞게 적절히 적용합니다.
   - PDF의 섹션 번호 체계(1, 2, 2-1, 2-2 등)를 마크다운 제목 레벨로 반영합니다.

4. **검증**: 변경된 파일의 이미지 태그가 원본과 동일한지 확인합니다.

## 주의사항

- 마크다운 파일 내의 이미지(`<img>` 태그)는 모두 동일하므로 절대 건드리지 않습니다.
- PDF 내용을 임의로 수정, 추가, 삭제하지 않습니다. PDF가 원본이며 마크다운은 PDF와 동일해야 합니다.
- PDF에 새로운 섹션이 추가되었으면 마크다운에도 추가하고, PDF에서 삭제되었으면 마크다운에서도 삭제합니다.