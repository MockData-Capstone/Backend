# zipcheck-api

> AI 맞춤형 집찾기 & 실시간 룸투어 기반 사용자 맞춤형 부동산 서비스 — Backend

허위매물·광각 낚시·어뷰징 리뷰 문제를 해결하고, 비대면으로 주거 공간을 검증할 수 있는 부동산 플랫폼의 백엔드 저장소입니다.
2026 계명대학교 컴퓨터공학전공 캡스톤 디자인(1) 프로젝트, 팀 **목데이터**에서 개발합니다.

## Overview

기존 부동산 플랫폼은 아래 4가지 문제를 안고 있습니다.

- 허위·미끼 매물 범람
- 리뷰 조작 및 어뷰징
- 과한 필터·광각(0.5x) 보정 사진
- 직접 현장 방문 필수

zipcheck-api는 이 문제들을 **AI 기반 사진/리뷰 검증**과 **비대면 룸투어**로 해결하는 것을 목표로 합니다.

## Core Features

| # | 기능 | 설명 |
|---|------|------|
| 1 | AI 광각 이미지 & 허위 검증 | EXIF Focal Length 분석 + Vision AI로 0.5배 광각 사진 자동 판별 |
| 2 | LLM 기반 맞춤형 집 검색 | 채팅으로 입력한 조건(지역·거리·편의시설)을 바탕으로 LLM이 매물/지역 추천 |
| 3 | AI 로드뷰 입지·경사도 분석 | 로드뷰·고도(DEM) 데이터로 경사도, 배리어프리 여부 안내 |
| 4 | IP/GPS 기반 찐후기 검증 | 실거주 이력·위치 검증으로 어뷰징 리뷰 자동 검열 |
| 5 | 1배율 고정 WebRTC 실시간 투어 | 화각 1.0x 고정 라이브 영상통화로 방 크기·하자 비대면 확인 |

## Tech Stack

세부 스택은 설계 단계에서 확정 후 이 섹션을 업데이트합니다. (draft)

- **Backend**: Java / Spring Boot (예정)
- **Realtime**: WebRTC Signaling Server, SFU/TURN
- **AI/Analysis**: EXIF 파서 + 광각 판별 모델, LLM 추천 엔진, 로드뷰·경사도 분석 모듈, 리뷰 어뷰징 스코어링
- **Data**: RDB (미확정), Object Storage (미확정)
- **External API**: 지도 / 로드뷰 / 공공데이터
- **Infra**: Cloud 배포 + CI/CD (GitHub Actions)

## Folder Structure

패키지/디렉터리 구조는 별도로 설계 후 이 섹션에 반영 예정입니다.

## Team — 목데이터

| 이름 | 역할 | 담당 업무 |
|------|------|-----------|
| 신한목 | PM | 일정·이슈 관리, 요구사항 정의, 산출물 총괄 |
| 황성민 | Full-stack | AI 사진 검증 모듈(EXIF·광각 판별), WebRTC 룸투어 시그널링 |
| 이상민 | Backend / Infra | LLM 추천 엔진 서버, 리뷰 어뷰징 스코어링, CI/CD |
| 구대현 | Backend | 매물·리뷰 CRUD API, DB 스키마 설계, 입지·경사도 분석 모듈, PoC 데이터 구축 |
| 손지은 | Frontend / Design | UI/UX 디자인, 매물 검색·상세 화면 |
| 김석현 | Frontend | LLM 채팅 추천 화면, 프론트–백엔드 API 연동 |
| 표다은 | Frontend / Design | 화면 디자인·퍼블리싱, 리뷰·마이페이지 화면 |

## Collaboration Rules

- 브랜치 전략: `main` / `develop` / `feature/*`
- 기능 단위 브랜치 생성 → PR 리뷰 후 merge
- 커밋 컨벤션 통일 (예: `feat`, `fix`, `docs`, `refactor` 등)
- 이슈·PR은 GitHub Issues / Projects로 관리

## Schedule

캡스톤(1) 개발 일정 및 마일스톤은 팀 발표 자료를 참고하세요.

- 10/06 팀 발표 #3 (중간 공유)
- 10/27, 11/17 진행 상황 공유
- 12/08 최종 발표 및 데모 시연
