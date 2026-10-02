#!/usr/bin/env bash
# Разовая подготовка VPS (Ubuntu 24) под авто-деплой сервера ExileForge. Запуск от root: bash setup_vps.sh
# Повторный запуск безопасен: ничего не пересоздаёт, существующие настройки и база не трогаются.
set -euo pipefail

APP=/opt/exileforge
ENV=/etc/exileforge.env

# Java 21 и MongoDB (если её ещё нет на сервере).
apt-get update -q
apt-get install -y -q openjdk-21-jre-headless curl gnupg
if ! command -v mongod >/dev/null; then
  curl -fsSL https://www.mongodb.org/static/pgp/server-8.0.asc | gpg --dearmor -o /usr/share/keyrings/mongodb-server-8.0.gpg
  echo "deb [ signed-by=/usr/share/keyrings/mongodb-server-8.0.gpg ] https://repo.mongodb.org/apt/ubuntu noble/mongodb-org/8.0 multiverse" > /etc/apt/sources.list.d/mongodb-org-8.0.list
  apt-get update -q && apt-get install -y -q mongodb-org
  # Транзакции сервера требуют набора реплик: один узел rs0.
  grep -q "replSetName" /etc/mongod.conf || printf '\nreplication:\n  replSetName: rs0\n' >> /etc/mongod.conf
  systemctl enable --now mongod
  sleep 5
  mongosh --quiet --eval 'try { rs.status() } catch (e) { rs.initiate() }' || true
fi

# Пользователь сервиса: под ним идёт и сервер, и выкладка из GitHub Actions.
id exileforge >/dev/null 2>&1 || useradd --system --create-home --shell /bin/bash exileforge
mkdir -p "$APP" && chown exileforge:exileforge "$APP"

# Настройки окружения (заполняются один раз; пароль администратора - свой).
if [ ! -f "$ENV" ]; then
  cat > "$ENV" <<CONF
MONGO_URI=mongodb://localhost:27017/?replicaSet=rs0
MONGO_DB=mongobase
PORT=8080
ADMIN_PASSWORD=ChangeMe1
CONF
  chmod 600 "$ENV"
  echo ">>> Задайте ADMIN_PASSWORD в $ENV"
fi

cat > /etc/systemd/system/exileforge.service <<UNIT
[Unit]
Description=ExileForge server
After=network-online.target mongod.service
Wants=network-online.target

[Service]
User=exileforge
WorkingDirectory=$APP
EnvironmentFile=$ENV
ExecStart=/usr/bin/java -Xms512m -Xmx2g -jar $APP/server.jar
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
UNIT
systemctl daemon-reload
systemctl enable exileforge

# Выкладка может только перезапустить сервис - больше ничего от root.
echo "exileforge ALL=(root) NOPASSWD: /usr/bin/systemctl restart exileforge" > /etc/sudoers.d/exileforge
chmod 440 /etc/sudoers.d/exileforge

# Ключ для GitHub Actions: открытый - в authorized_keys, закрытый - в секрет DEPLOY_SSH_KEY.
KEYDIR=/home/exileforge/.ssh
mkdir -p "$KEYDIR"
if [ ! -f "$KEYDIR/deploy" ]; then
  ssh-keygen -t ed25519 -N "" -C "github-deploy" -f "$KEYDIR/deploy" -q
  cat "$KEYDIR/deploy.pub" >> "$KEYDIR/authorized_keys"
fi
chown -R exileforge:exileforge "$KEYDIR" && chmod 700 "$KEYDIR" && chmod 600 "$KEYDIR/authorized_keys"

# Порт игры открыт, если включён ufw.
command -v ufw >/dev/null && ufw status | grep -q active && ufw allow 8080/tcp || true

echo
echo "===== Секрет DEPLOY_SSH_KEY (скопируйте целиком) ====="
cat "$KEYDIR/deploy"
echo "======================================================"
echo "DEPLOY_HOST = $(curl -fsS https://api.ipify.org || hostname -I | cut -d' ' -f1)"
echo "DEPLOY_USER = exileforge"
