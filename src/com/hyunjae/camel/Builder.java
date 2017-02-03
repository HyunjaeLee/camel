package com.hyunjae.camel;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Builder {

    private static final Pattern PATTERN = Pattern.compile("var videoID = '(.*?)'");
    private static final String USERAGENT = "Mozilla/5.0";
    private static final int TRIES = 10;
    
    private ExecutorService executorService = Executors.newFixedThreadPool(64);
    private Derby derby;
    
    Builder() throws Exception {
        derby = new Derby();
    }

    private int parseId(String url) { // Returns ID from given URL
        int lastIndex = url.lastIndexOf('-') + 1;
        String idString = url.substring(lastIndex);
        return Integer.parseInt(idString);
    }

    void buildIndex() {
        String[] days = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

        try {
            Document document = Jsoup.connect("https://anigod.com/")
                    .userAgent(USERAGENT)
                    .get();

            Elements elements = document.select(".index-table-container");

            for (int i = 0; i < 7; i++) {
                final int index = i; // Variable used in lambda should be final or effectively final
                elements.get(index)
                        .select(".index-image-container.badge")
                        .forEach(element -> {
                            String title = element.attr("title");
                            int id = parseId(element.attr("href"));
                            String thumbnailUrl = element.select(".index-image").first().attr("src");
                            int timestamp = Integer.parseInt(element.attr("timestamp"));
                            String day = days[index];

                            derby.insertSeries(id, title, thumbnailUrl, timestamp, day);
                        });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void buildFinale() {
        try {
            Document document = Jsoup.connect("https://anigod.com/animations/finale/text")
                    .userAgent(USERAGENT)
                    .get();

            int size = document.select(".table-link").size();
            int page = (size / 30) + 1; // Each page has 30 elements

            List<Future> futures = new ArrayList<>();

            for (int i = 1; i <= page; i++) {
                final int index = i;
                Future future = executorService.submit(() -> buildFinale(index));
                futures.add(future);
            }

            for (Future future : futures) {
                future.get(); // Blocking
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int buildFinale(int page) throws IOException { // returns size
        Document document = Jsoup.connect("https://anigod.com/animations/finale/title/asc/" + page)
                .userAgent(USERAGENT)
                .get();

        Elements elements = document.select(".table-image-container");
        int size = elements.size();

        elements.forEach(element -> {
            String title = element.attr("title");
            int id = parseId(element.attr("href"));
            String thumbnailUrl = element.select(".lazy").first().attr("data-original");
            int timestamp = Integer.parseInt(element.attr("timestamp"));

            derby.insertSeries(id, title, thumbnailUrl, timestamp, null);
        });

        return size;
    }

    void buildEpisode() {
        List<Future> futures = new ArrayList<>();

        derby.selectSeriesId().forEach(seriesId -> {
            Future future = executorService.submit(() -> buildEpisode(seriesId));
            futures.add(future);
        });

        futures.forEach(future -> {
            try {
                future.get(); // Blocking
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void buildEpisode(int seriesId) {
        int size, page = 1;
        do {
            size = buildEpisode(seriesId, page);
            page++;
        } while(size > 0);
    }

    private int buildEpisode(int seriesId, int page) { // returns size
        int size = 0;
        String url = "https://anigod.com/animation/" + seriesId + "/" + page;

        try {
            Document document = Jsoup.connect(url)
                    .userAgent(USERAGENT)
                    .get();

            Elements elements = document.select(".table-image-container");
            size = elements.size();

            elements.forEach(element -> {
                String title = element.attr("title");
                int id = parseId(element.attr("href"));
                String thumbnailUrl = element.select(".lazy").first().attr("data-original");
                int timestamp = Integer.parseInt(element.attr("timestamp"));

                derby.insertEpisode(id, title, thumbnailUrl, null, timestamp, seriesId);
            });
        } catch (IOException e) {
            e.printStackTrace();
        }

        return size;
    }

    void buildVideoUrl() {
        List<Future> futures = new ArrayList<>();

        derby.selectEpisodeId().forEach(episodeId -> {
            Future future = executorService.submit(() -> {
                try {
                    tryBuildVideoUrl(episodeId);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            futures.add(future);
        });

        futures.forEach(future -> {
            try {
                future.get(); // Blocking
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void tryBuildVideoUrl(int episodeId) throws Exception {
        for (int i = 0; i < TRIES; i++) {
            try {
                buildVideoUrl(episodeId);
                return;
            } catch (IOException e) {
                // Does nothing
            }
        }

        throw new Exception("Failed to load https://anigod.com/episode/" + episodeId);
    }

    private void buildVideoUrl(int episodeId) throws Exception {
        String url = "https://anigod.com/episode/" + episodeId;

        Document document = Jsoup.connect(url)
                .userAgent(USERAGENT)
                .timeout(60000)
                .referrer("http://sh.st/")
                .get();

        String html = document.outerHtml();

        String videoId;
        Matcher matcher = PATTERN.matcher(html);
        if(matcher.find())
            videoId = matcher.group(1);
        else
            throw new Exception("Invalid HTML page");

        String escapedVideoId = URLDecoder.decode(videoId, "UTF-8")
                .replace("\\x", "%")
                .replace("\\", "");

        String redirectableUrl = "https://anigod.com/video?id=" + escapedVideoId + "&ts=" + System.currentTimeMillis();
        String videoUrl = tryGetRedirectedUrl(redirectableUrl);

        derby.updateVideoUrl(videoUrl, episodeId);
    }

    private String tryGetRedirectedUrl(String url) throws IOException {
        for (int i = 0; i < TRIES; i++) {
            try {
                return getRedirectedUrl(url);
            } catch (IOException e) {
                // Does nothing
            }
        }

        throw new IOException("Failed to load " + url);
    }

    private String getRedirectedUrl(String url) throws IOException {
        String redirectUrl = url;

        HttpURLConnection connection = (HttpURLConnection) new URL(redirectUrl).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", USERAGENT);
        connection.setRequestMethod("GET");
        connection.connect();

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
            String location = connection.getHeaderField("Location");
            if (location != null)
                redirectUrl = location;
        }

        return redirectUrl;
    }
    
    void close() {
        if (derby != null)
            derby.close();
        if (executorService != null)
            executorService.shutdown();
    }
}
