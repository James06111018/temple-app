package tw.org.il.dongsheng.templeapp.update;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AppUpdateService {
    private static final URI LATEST_RELEASE_API = URI.create(
            "https://api.github.com/repos/James06111018/temple-app/releases/latest"
    );
    private static final Pattern TAG_PATTERN = Pattern.compile("\\\"tag_name\\\"\\s*:\\s*\\\"v?([^\\\"]+)\\\"");
    private static final Pattern WINDOWS_ASSET_PATTERN = Pattern.compile(
            "\\\"name\\\"\\s*:\\s*\\\"(TempleApp-Windows-[^\\\"]+\\.exe)\\\"[\\s\\S]*?"
                    + "\\\"browser_download_url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\""
    );

    private final HttpClient httpClient;

    public AppUpdateService() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    AppUpdateService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public Optional<AppUpdate> findAvailableUpdate(String currentVersion) throws IOException, InterruptedException {
        if (!isWindows()) {
            return Optional.empty();
        }

        HttpRequest request = HttpRequest.newBuilder(LATEST_RELEASE_API)
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "TempleApp-Updater/" + currentVersion)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("GitHub 版本服務回應 " + response.statusCode());
        }

        Matcher tagMatcher = TAG_PATTERN.matcher(response.body());
        Matcher assetMatcher = WINDOWS_ASSET_PATTERN.matcher(response.body());
        if (!tagMatcher.find() || !assetMatcher.find()) {
            return Optional.empty();
        }

        String latestVersion = tagMatcher.group(1);
        if (compareVersions(latestVersion, currentVersion) <= 0) {
            return Optional.empty();
        }
        return Optional.of(new AppUpdate(
                latestVersion,
                URI.create(assetMatcher.group(2).replace("\\/", "/")),
                assetMatcher.group(1)
        ));
    }

    public Path download(AppUpdate update, DownloadProgress progress) throws IOException, InterruptedException {
        Path target = Files.createTempFile("TempleApp-update-", ".exe");
        HttpRequest request = HttpRequest.newBuilder(update.downloadUri())
                .timeout(Duration.ofMinutes(10))
                .header("User-Agent", "TempleApp-Updater/" + update.version())
                .GET()
                .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            Files.deleteIfExists(target);
            throw new IOException("安裝檔下載失敗，HTTP " + response.statusCode());
        }

        long total = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        try (InputStream input = response.body();
             var output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8192];
            long downloaded = 0;
            int length;
            while ((length = input.read(buffer)) >= 0) {
                output.write(buffer, 0, length);
                downloaded += length;
                progress.changed(downloaded, total);
            }
        } catch (IOException e) {
            Files.deleteIfExists(target);
            throw e;
        }
        return target;
    }

    public void launchInstaller(Path installer) throws IOException {
        new ProcessBuilder(installer.toAbsolutePath().toString()).start();
    }

    static int compareVersions(String left, String right) {
        String[] leftParts = normalizeVersion(left).split("\\.");
        String[] rightParts = normalizeVersion(right).split("\\.");
        int length = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < length; i++) {
            int leftValue = i < leftParts.length ? parsePart(leftParts[i]) : 0;
            int rightValue = i < rightParts.length ? parsePart(rightParts[i]) : 0;
            if (leftValue != rightValue) {
                return Integer.compare(leftValue, rightValue);
            }
        }
        return 0;
    }

    private static String normalizeVersion(String version) {
        if (version == null) {
            return "0";
        }
        return version.trim().replaceFirst("^[vV]", "").split("[-+]", 2)[0];
    }

    private static int parsePart(String part) {
        try {
            return Integer.parseInt(part.replaceAll("[^0-9].*$", ""));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    @FunctionalInterface
    public interface DownloadProgress {
        void changed(long downloadedBytes, long totalBytes);
    }
}
