#!/usr/bin/env bash
# Разовая настройка авто-деплоя на VPS (запуск от root): bash setup_deploy_key.sh
# Создаёт SSH-ключ для GitHub Actions, который может только одно - запустить /opt/exileforge/update.sh без вопросов:
# обновить код, пересобрать, сохранить базы, проверить и откатить при неудаче. Ни консоли, ни других команд по нему нет.
set -euo pipefail

KEY=/root/.ssh/github-deploy
COMMAND='bash /opt/exileforge/update.sh --update --keep-data --no-logs'
RESTRICT="command=\"$COMMAND\",no-pty,no-port-forwarding,no-agent-forwarding,no-X11-forwarding"

[[ $EUID -eq 0 ]] || { echo 'Запустите от root'; exit 1; }
[[ -f /opt/exileforge/update.sh ]] || { echo 'Нет /opt/exileforge/update.sh'; exit 1; }
mkdir -p /root/.ssh && chmod 700 /root/.ssh
[[ -f $KEY ]] || ssh-keygen -t ed25519 -N '' -C github-deploy -f "$KEY" -q
touch /root/.ssh/authorized_keys && chmod 600 /root/.ssh/authorized_keys
# Ключ попадает в authorized_keys один раз и только с принудительной командой.
grep -qF "$(cut -d' ' -f2 "$KEY.pub")" /root/.ssh/authorized_keys || echo "$RESTRICT $(cat "$KEY.pub")" >> /root/.ssh/authorized_keys

echo
echo '===== Секрет DEPLOY_SSH_KEY (скопируйте целиком, вместе с BEGIN/END) ====='
cat "$KEY"
echo '=========================================================================='
echo "DEPLOY_HOST = $(curl -fsS https://api.ipify.org 2>/dev/null || hostname -I | cut -d' ' -f1)"
