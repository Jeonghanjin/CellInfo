# CellInfo - 기지국 정보 조회 앱

안드로이드 폰의 TelephonyManager API를 사용해서 현재 접속 중인 기지국 정보를 실시간으로 조회하는 앱입니다.

## 기능

- 📡 Serving Cell 정보 (5G NR / LTE / WCDMA / GSM)
- 📊 신호 측정값: RSRP, RSRQ, SINR, RSSI, CQI, TA
- 🆔 식별 정보: Cell ID, PCI, TAC, EARFCN/NR-ARFCN, PLMN(MCC/MNC), Band
- 🔄 인접 셀 (Neighbor Cells) 표시
- 2초 자동 갱신
- ⏺ CSV 로그 기록 및 내보내기

## GitHub Actions로 APK 빌드하기 (Android Studio 불필요)

### 1단계: GitHub 계정 준비
- [github.com](https://github.com) 에서 회원가입 (이미 있으면 패스)

### 2단계: 새 저장소 만들기
1. GitHub 우측 상단 `+` → `New repository`
2. Repository name: `CellInfo` (아무 이름 가능)
3. **Public** 선택 (Private도 되지만 Public이 무료 빌드 시간 무제한)
4. `Create repository` 클릭

### 3단계: 코드 업로드
**방법 A - 웹에서 드래그 앤 드롭 (제일 쉬움):**
1. 방금 만든 저장소 페이지에서 `uploading an existing file` 링크 클릭
2. 받으신 `CellInfo` 폴더 안의 모든 파일/폴더를 드래그
3. 하단 `Commit changes` 클릭

**방법 B - Git 명령어 사용:**
```bash
cd CellInfo
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/본인계정/CellInfo.git
git push -u origin main
```

### 4단계: 빌드 시작 (자동)
- 코드를 push 하면 GitHub Actions가 자동으로 빌드를 시작합니다
- 저장소 상단 `Actions` 탭에서 진행 상황 확인 가능
- 보통 3~5분 정도 걸립니다

### 5단계: APK 다운로드
1. `Actions` 탭 → 가장 최근 워크플로 클릭 (초록색 체크 표시)
2. 페이지 하단 `Artifacts` 섹션에 `CellInfo-debug-apk` 클릭
3. zip 파일이 다운로드되고, 안에 APK가 있습니다

### 6단계: 폰에 설치
1. APK 파일을 폰으로 전송 (이메일, 클라우드, USB 등)
2. 파일 매니저에서 APK 탭
3. "출처를 알 수 없는 앱 설치 허용" 켜기 (설정에서)
4. 설치 진행

### 7단계: 권한 부여
앱 첫 실행 시:
- 위치 권한 → "앱 사용 중에만 허용"
- 전화 권한 → "허용"

## 빌드가 실패하면

GitHub `Actions` 탭에서 빨간 X 표시된 작업 클릭 → 로그 확인 후 알려주세요.

## 주의사항

- 일부 정보(Band, gNB ID 등)는 안드로이드 버전과 제조사 펌웨어에 따라 마스킹될 수 있습니다
- 5G NR 정보는 Android 10 (API 29) 이상에서 표시됩니다
- Samsung, Xiaomi 등 일부 제조사는 셀 정보를 더 자세히 제공합니다
- 디버그 빌드라 설치 시 "안전하지 않은 앱" 경고가 나올 수 있습니다 (정상)

## 라이선스

개인 용도로 자유롭게 사용하세요.
