// 正式網域尚未決定前的佔位值；部署前務必換成正式後端網址，
// 否則 `ng build --configuration production` 打包出來的前端仍會打這個假網址。
export const environment = {
  apiBaseUrl: 'https://REPLACE_WITH_PRODUCTION_DOMAIN/api'
};
