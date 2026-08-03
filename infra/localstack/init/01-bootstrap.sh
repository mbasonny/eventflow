#!/bin/bash
# Joué par LocalStack une fois les services prêts (/etc/localstack/init/ready.d).
# Crée les ressources AWS dont EventFlow a besoin, pour qu'aucune étape manuelle
# ne traîne dans le README.
set -euo pipefail

REGION="${AWS_DEFAULT_REGION:-eu-west-3}"
BUCKET="eventflow-tickets"
TOPIC="eventflow-notifications"

awslocal s3 mb "s3://${BUCKET}" --region "${REGION}"
awslocal sns create-topic --name "${TOPIC}" --region "${REGION}"

echo "LocalStack prêt : bucket ${BUCKET}, topic SNS ${TOPIC}"
