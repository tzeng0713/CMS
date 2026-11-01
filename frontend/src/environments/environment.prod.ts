// docker-compose 架構下前端由 nginx 服務，/api 會被同一個 nginx 反向代理到後端容器，
// 跟前端同源，所以用相對路徑就好，不會有 CORS 問題。
// 如果之後改成前後端分開網域部署，這裡要換成完整網址（例如 https://api.example.com/api），
// 並記得在後端設定 CMS_CORS_ALLOWED_ORIGINS。
export const environment = {
  apiBaseUrl: '/api'
};
