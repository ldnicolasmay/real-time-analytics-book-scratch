#!/usr/bin/env bash

docker compose \
  -f ./docker-compose-0-base.yml \
  -f ./docker-compose-1-pinot.yml \
  -f ./docker-compose-2-kafka-streams-rest-api.yml \
  -f ./docker-compose-3-dashboard.yml down
