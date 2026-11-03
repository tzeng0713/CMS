FROM node:22-alpine AS build
WORKDIR /app
# node:22-alpine 內建的 npm 版本比產生 package-lock.json 的版本舊，
# 解析巢狀 optional dependency 的方式不同會導致 npm ci 失敗，先升級對齊。
RUN npm install -g npm@11
COPY package*.json ./
RUN npm ci
# 只複製 npm build 實際需要的檔案，不要整個資料夾一起複製（包含 nginx.conf）——
# 不然改 nginx.conf 這種跟前端程式碼無關的設定檔，也會讓這層快取失效，逼整個 npm build 重跑一次。
COPY angular.json tsconfig*.json ./
COPY src ./src
RUN npm run build -- --configuration production

FROM nginx:1.27-alpine
LABEL org.opencontainers.image.source="https://github.com/tzeng0713/CMS"
COPY --from=build /app/dist/cms-frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
