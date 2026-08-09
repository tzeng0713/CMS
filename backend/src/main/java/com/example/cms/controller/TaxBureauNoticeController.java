package com.example.cms.controller;

import com.example.cms.dto.TaxBureauNoticeGenerateRequest;
import com.example.cms.service.TaxBureauNoticeService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.Map;

@RestController
@RequestMapping("/api/tax-bureau-notices")
public class TaxBureauNoticeController {
    private final TaxBureauNoticeService service;

    public TaxBureauNoticeController(TaxBureauNoticeService service) {
        this.service = service;
    }

    @GetMapping("/preview")
    public Map<String, Object> preview(@RequestParam String yearMonth,
                                        @RequestParam(required = false) String type) {
        return service.preview(yearMonth, type);
    }

    @PostMapping("/generate")
    public ResponseEntity<byte[]> generate(@RequestBody TaxBureauNoticeGenerateRequest request) {
        byte[] bytes = service.generate(request);
        String filename = "國稅局通報_" + request.yearMonth() + ".xlsx";
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
                .body(bytes);
    }
}
