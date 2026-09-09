#!/bin/bash
# 재배포 스크립트. 이미 install.sh로 설치된 상태에서 새 jar로 교체·재기동한다.
#
# 전제조건: $HOME/todolist.jar 에 새로 빌드한 jar를 WinSCP로 미리 업로드해둔다.
# 실패 시 자동 롤백은 하지 않는다 — 백업 파일 경로와 롤백 명령을 안내만 한다.

set -euo pipefail

APP_DIR=/etc/todolist
SOURCE_JAR="$HOME/todolist.jar"
HEALTH_URL="http://localhost:8080/api/health"
KEEP_BACKUPS=3

echo "==> 1. 사전 점검"
if ! systemctl is-enabled --quiet todolist 2>/dev/null; then
	echo "ERROR: todolist 서비스가 설치되어 있지 않습니다. install.sh를 먼저 실행하세요." >&2
	exit 1
fi
if [ ! -f "$SOURCE_JAR" ]; then
	echo "ERROR: $SOURCE_JAR 가 없습니다. WinSCP로 새 jar를 홈 디렉토리에 업로드하세요." >&2
	exit 1
fi

echo "==> 2. 기존 jar 백업"
TIMESTAMP="$(date +%Y%m%d%H%M%S)"
BACKUP="$APP_DIR/todolist.jar.bak.$TIMESTAMP"
sudo cp "$APP_DIR/todolist.jar" "$BACKUP"
# 오래된 백업은 최근 KEEP_BACKUPS개만 남기고 정리
ls -1t "$APP_DIR"/todolist.jar.bak.* 2>/dev/null | tail -n +$((KEEP_BACKUPS + 1)) | xargs -r sudo rm -f

echo "==> 3. 신규 jar 교체 (원자적 mv)"
cp "$SOURCE_JAR" "$APP_DIR/todolist.jar.new"
sudo mv "$APP_DIR/todolist.jar.new" "$APP_DIR/todolist.jar"

echo "==> 4. 재기동"
sudo systemctl restart todolist

echo "==> 5. 헬스체크 대기 (최대 30초)"
for i in $(seq 1 15); do
	if curl -sf "$HEALTH_URL" >/dev/null 2>&1; then
		echo "==> 재배포 성공."
		exit 0
	fi
	sleep 2
done

echo "ERROR: 헬스체크 실패. 최근 로그:" >&2
sudo journalctl -u todolist -n 50 --no-pager
echo >&2
echo "이전 버전으로 롤백하려면:" >&2
echo "  sudo cp $BACKUP $APP_DIR/todolist.jar && sudo systemctl restart todolist" >&2
exit 1
