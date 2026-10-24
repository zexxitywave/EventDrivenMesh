#!/bin/bash
set -euxo pipefail

# -----------------------------------------------------------------------------
# EC2 user-data bootstrap for Event-Driven Mesh - order-service
#
# Attach this as the "User data" of an EC2 launch template / Auto Scaling Group.
# One instance = one order-service replica. Each replica registers itself in
# Eureka (unique instance-id); the api-gateway load-balances across all of them.
#
# Set JAR_S3_URI (or change the fetch step) to your artifact source. The env
# block below is written once per instance - for many identical replicas keep it
# in a shared source instead (SSM Parameter Store / instance tags).
# -----------------------------------------------------------------------------

JAR_S3_URI="${JAR_S3_URI:-s3://your-bucket/artifacts/order-service.jar}"
APP_DIR=/opt/order-service
APP_USER=ec2-user

# --- 1. Java 21 (Amazon Corretto) -------------------------------------------
dnf install -y java-21-amazon-corretto-headless

# --- 2. App directory + artifact --------------------------------------------
mkdir -p "$APP_DIR"
if [[ "$JAR_S3_URI" == s3://* ]]; then
  aws s3 cp "$JAR_S3_URI" "$APP_DIR/order-service.jar"
else
  curl -fsSL "$JAR_S3_URI" -o "$APP_DIR/order-service.jar"
fi
chown -R "$APP_USER:$APP_USER" "$APP_DIR"

# --- 3. Per-instance environment --------------------------------------------
# INSTANCE_ID left blank -> falls back to the EC2 hostname (unique per instance).
# To use the EC2 instance-id instead, uncomment the IMDSv2 line below.
# IMDS_INSTANCE_ID=$(TOKEN=$(curl -sX PUT "http://169.254.169.254/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 60"); curl -s -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/instance-id)
cat > /etc/order-service.env <<ENV
INSTANCE_ID=${IMDS_INSTANCE_ID:-}
SERVER_PORT=8081
EUREKA_HOST=eureka.internal
EUREKA_PORT=8761
DB_HOST=order-db.internal
DB_PORT=5432
DB_USER=postgres
DB_PASSWORD=change-me
DB_POOL_MAX=5
DB_POOL_MIN=2
KAFKA_BOOTSTRAP_SERVERS=broker1.internal:9092,broker2.internal:9092
ENV
chmod 600 /etc/order-service.env

# --- 4. systemd unit ---------------------------------------------------------
cat > /etc/systemd/system/order-service.service <<'UNIT'
[Unit]
Description=Event-Driven Mesh - order-service
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=ec2-user
Group=ec2-user
WorkingDirectory=/opt/order-service
EnvironmentFile=/etc/order-service.env
ExecStart=/usr/bin/java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom -jar /opt/order-service/order-service.jar
KillSignal=SIGTERM
SuccessExitStatus=143
TimeoutStopSec=45
Restart=on-failure
RestartSec=10
NoNewPrivileges=true
PrivateTmp=true

[Install]
WantedBy=multi-user.target
UNIT

systemctl daemon-reload
systemctl enable --now order-service

# --- 5. Health ---------------------------------------------------------------
# ALB / ASG target group health check: path /actuator/health, port 8081
echo "order-service bootstrap complete on $(hostname)"
