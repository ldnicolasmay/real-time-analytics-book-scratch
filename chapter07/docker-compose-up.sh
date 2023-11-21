#!/usr/bin/env bash

docker compose -f ./docker-compose-0-base.yml up &
sleep 45
docker compose -f ./docker-compose-1-pinot.yml up &
sleep 45
docker compose -f ./docker-compose-2-kafka-streams-rest-api.yml up &
sleep 10
docker compose -f ./docker-compose-3-dashboard.yml up
