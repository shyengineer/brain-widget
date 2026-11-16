# brain-widget

Obsidian 볼트(GitHub 저장소)에서 할 일과 시간표를 읽어 보여 주는 안드로이드 앱과 홈 화면 위젯.

- 할 일: 노트의 `- [ ] 할 일 📅 YYYY-MM-DD` 줄 (마감일 있는 것 + 최근 7일 데일리·이번 주 주간 노트의 마감일 없는 것)
- 시간표: 볼트의 `시간표.md` 표 (`| 요일 | 시작 | 끝 | 과목 | 교수 | 장소 |`)
- 읽기 전용. 체크는 Obsidian에서.

## 설치
main 에 push 하면 GitHub Actions 가 APK 를 빌드해서 Releases 에 올린다.
휴대폰에서 [최신 릴리스](../../releases/latest)의 `brain-widget.apk` 를 받아 설치.

## 설정
앱 첫 실행 시 GitHub 토큰(볼트 저장소 Contents 읽기 전용)을 입력한다.
