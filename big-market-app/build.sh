#!/bin/bash

IMAGE_NAME="cyh/big-market-app:1.0"

echo "------ 开始构建后端镜像: $IMAGE_NAME ------"

# 构建镜像
docker build -t $IMAGE_NAME -f ./Dockerfile .

echo "------ 构建完成！ ------"