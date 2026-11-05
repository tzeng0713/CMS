export const environment = {
  // 用目前頁面的 hostname 組 API 網址：電腦用 localhost 開就打 localhost:8080，
  // 手機用區網 IP（例如 192.168.0.103:4200）開，就會自動打同一個 IP 的 8080，
  // 不用每次切換測試裝置都手動改這個檔案。
  apiBaseUrl: `http://${window.location.hostname}:8080/api`
};
