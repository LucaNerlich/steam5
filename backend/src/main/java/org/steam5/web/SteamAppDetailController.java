package org.steam5.web;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.steam5.domain.details.SteamAppDetail;
import org.steam5.repository.details.SteamAppDetailRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/details")
@Validated
public class SteamAppDetailController {

    private final SteamAppDetailRepository detailRepository;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> indexJson() {
        final Map<String, String> links = new LinkedHashMap<>();
        links.put("self", "/api/details");
        links.put("byAppId", "/api/details/{appId}");
        return ResponseEntity.ok(links);
    }

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> indexHtml() {
        final String html = """
                <!doctype html>
                <html lang=\"en\">
                <head>
                  <meta charset=\"utf-8\" />
                  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />
                  <title>Details Index</title>
                  <style>
                	body{font-family:system-ui,-apple-system,Segoe UI,Roboto,Ubuntu,Cantarell,Noto Sans,sans-serif;padding:20px;line-height:1.5}
                	h1{margin-top:0}
                	ul{padding-left:18px}
                	code{background:#f4f4f4;padding:2px 6px;border-radius:4px}
                	.form{margin-top:16px}
                	input[type=number]{padding:6px 8px;font-size:14px;width:200px}
                	button, a.button{display:inline-block;margin-left:8px;padding:6px 10px;background:#0b5fff;color:#fff;text-decoration:none;border:none;border-radius:4px;cursor:pointer}
                	a.button:visited{color:#fff}
                	.link{margin-top:10px}
                  </style>
                </head>
                <body>
                  <h1>Details Index</h1>
                  <ul>
                	<li><a href=\"/api/details\">self</a> <code>GET /api/details</code></li>
                	<li>byAppId <code>GET /api/details/{appId}</code></li>
                  </ul>
                  <div class=\"form\">
                	<label for=\"appIdInput\">App ID:</label>
                	<input id=\"appIdInput\" type=\"number\" min=\"1\" placeholder=\"e.g. 570\" />
                	<a id=\"goLink\" href=\"#\" class=\"button\" target=\"_blank\">Open details</a>
                	<div class=\"link\"><code id=\"urlPreview\">/api/details/{appId}</code></div>
                  </div>
                  <script>
                	(function(){
                	  const input = document.getElementById('appIdInput');
                	  const link = document.getElementById('goLink');
                	  const preview = document.getElementById('urlPreview');
                	  function update(){
                	    const v = (input.value || '').trim();
                	    const url = v ? `/api/details/${v}` : '#';
                	    link.href = url;
                	    preview.textContent = v ? url : '/api/details/{appId}';
                	  }
                	  input.addEventListener('input', update);
                	  update();
                	})();
                  </script>
                </body>
                </html>
                """;
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    @GetMapping("/{appId}")
    @Transactional(readOnly = true)
    @Cacheable(value = "one-day", key = "#appId", unless = "#result == null || !#result.statusCode.is2xxSuccessful()")
    public ResponseEntity<SteamAppDetail> getDetails(@PathVariable("appId") Long appId) {
        final Optional<SteamAppDetail> detailOpt = detailRepository.findById(appId);
        if (detailOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        final SteamAppDetail detail = detailOpt.get();
        // Touch associations to ensure they are initialized within the transaction
        if (detail.getDevelopers() != null) detail.getDevelopers().size();
        if (detail.getPublisher() != null) detail.getPublisher().size();
        if (detail.getCategories() != null) detail.getCategories().size();
        if (detail.getGenres() != null) detail.getGenres().size();
        if (detail.getScreenshots() != null) detail.getScreenshots().size();
        if (detail.getMovies() != null) detail.getMovies().size();
        detail.getPriceOverview();

        return ResponseEntity.ok(detail);
    }

}


