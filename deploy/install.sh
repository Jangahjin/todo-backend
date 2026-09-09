#!/bin/bash
# 최초 설치 스크립트. Amazon Linux 2023 / t2.micro 기준.
#
# 전제조건 (스크립트가 확인하지만 자동으로 만들지는 않는 것):
#   - $HOME/todolist.jar : WinSCP로 빌드 산출물(todo-backend-*.jar)을 홈 디렉토리에
#     업로드한 뒤 todolist.jar로 이름을 바꿔둔다.
#   - /etc/todolist/todolist.env : DB/JWT/OAuth2/S3 실제 값. WinSCP로 직접 업로드한다.
#     (이 스크립트는 절대 이 파일을 생성하거나 값을 추측하지 않는다.)
#
# 재실행해도 안전하다(idempotent) — 이미 설치된 항목은 건너뛴다.

set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR=/etc/todolist
SOURCE_JAR="$HOME/todolist.jar"
HEALTH_URL="http://localhost:8080/api/health"

echo "==> 1. JDK 21 설치 확인"
if ! java -version 2>&1 | grep -q '"21'; then
	sudo dnf install -y java-21-amazon-corretto
else
	echo "    이미 설치됨, 건너뜀"
fi

echo "==> 2. 스왑 설정 확인 (부팅 초기 메모리 스파이크 안전망)"
if [ -z "$(swapon --show 2>/dev/null)" ]; then
	sudo fallocate -l 1G /swapfile
	sudo chmod 600 /swapfile
	sudo mkswap /swapfile
	sudo swapon /swapfile
	if ! grep -q '^/swapfile' /etc/fstab; then
		echo '/swapfile swap swap defaults 0 0' | sudo tee -a /etc/fstab >/dev/null
	fi
	echo 'vm.swappiness=10' | sudo tee /etc/sysctl.d/99-todolist-swappiness.conf >/dev/null
	sudo sysctl --system >/dev/null
else
	echo "    이미 설정됨, 건너뜀"
fi

echo "==> 3. /etc/todolist 디렉토리 준비"
sudo mkdir -p "$APP_DIR"
sudo chown ec2-user:ec2-user "$APP_DIR"
sudo chmod 750 "$APP_DIR"

echo "==> 4. 산출물 배치"
if [ ! -f "$SOURCE_JAR" ]; then
	echo "ERROR: $SOURCE_JAR 가 없습니다. WinSCP로 jar를 홈 디렉토리에 todolist.jar 이름으로 먼저 업로드하세요." >&2
	exit 1
fi
cp "$SOURCE_JAR" "$APP_DIR/todolist.jar"

if [ ! -f "$APP_DIR/todolist.env" ]; then
	echo "ERROR: $APP_DIR/todolist.env 가 없습니다. WinSCP로 직접 업로드한 뒤 재실행하세요." >&2
	exit 1
fi
chmod 640 "$APP_DIR/todolist.env"

echo "==> 5. journald 로그 rate-limit 적용"
sudo mkdir -p /etc/systemd/journald.conf.d
sudo cp "$DEPLOY_DIR/todolist.conf" /etc/systemd/journald.conf.d/todolist.conf
sudo systemctl restart systemd-journald

echo "==> 6. systemd 유닛 등록"
sudo cp "$DEPLOY_DIR/todolist.service" /etc/systemd/system/todolist.service
sudo systemctl daemon-reload
sudo systemctl enable todolist

echo "==> 7. 서비스 기동"
sudo systemctl restart todolist

echo "==> 8. 헬스체크 대기 (최대 30초)"
for i in $(seq 1 15); do
	if curl -sf "$HEALTH_URL" >/dev/null 2>&1; then
		echo "==> 9. 설치 완료 — 서비스가 정상적으로 응답합니다."
		sudo systemctl status todolist --no-pager -l
		exit 0
	fi
	sleep 2
done

echo "ERROR: 헬스체크 실패. 최근 로그:" >&2
sudo journalctl -u todolist -n 50 --no-pager
exit 1
