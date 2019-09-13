import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Crawler {
  private static HttpClient CLIENT;
  private static Logger LOGGER;

  public static void main (String[] args) {
    LOGGER =  Logger.getLogger(Crawler.class.getName());
    CLIENT = HttpClient.newHttpClient();

    final String START_CRAWL_URL = "https://www.educative.io/courses/grokking-the-system-design-interview/NE5LpPrWrKv";
    final String DOMAIN = "educative.io";

    Deque<String> queue = new ArrayDeque<>();
    queue.add(START_CRAWL_URL);

    Set<String> visited = new HashSet<>();

    while (!queue.isEmpty()) {
      // Remove the next URL to process
      String currUrl = queue.remove();
      LOGGER.info("Processing: " + currUrl);

      // Get the HTML for the URL
      String html = getHTML(currUrl, 3);

      // If unable to get HTML, ignore this page
      if (html == null) {
        continue;
      }

      visited.add(currUrl);

      // Get the list of URLs this page contains
      List<String> urls = getUrls(html, DOMAIN);

      // Add to the queue if not already visited
      for (String url : urls) {
        if (!visited.contains(url))
          queue.add(url);
      }
    }

    LOGGER.info("Done.");
  }

  /**
   *
   * @param html The HTML of the page to process
   * @param domain Filters the URLs in the page to only match this domain
   * @return A list of urls, as Strings.
   */
  private static List<String> getUrls(String html, String domain) {
    List<String> urls = new ArrayList<>();

    Pattern p = Pattern.compile("(http|https)://([\\w_-]+(?:(?:\\.[\\w_-]+)+))([\\w.,@?^=%&:/~+#-]*[\\w@?^=%&/~+#-])?");
    Matcher m = p.matcher(html);

    while (m.find()) {
      // Only crawl in links to the same domain
      if (m.group(2).contains(domain))

        // Do not crawl in images
        if (m.group(3) == null || !(m.group(3).endsWith(".ico")
                                    || m.group(3).endsWith(".png")
                                    || m.group(3).endsWith(".jpg")
                                    || m.group(3).endsWith(".jpeg")))
          urls.add(m.group());
    }

    return urls;
  }

  /**
   *
   * @param url The URL to perform the HTTP GET request
   * @param retries The number of times to try the GET request, if failed
   * @return The body of the HTML page, or null if the HTTP request failed
   */
  private static String getHTML(String url, int retries) {
    HttpRequest getRequest = HttpRequest.newBuilder()
      .uri(URI.create(url))
      .build();

    HttpResponse<String> response = null;
    int attempts = 0;

    while (attempts < retries) {
      try {
        response = CLIENT.send(getRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
          break;
        }

        attempts += 1;
        if (attempts < retries)
          LOGGER.warning("HTTP status code: " + response.statusCode() + ". Attempts: " + attempts + ". Retrying...");
        else
          LOGGER.severe("Attempts: " + attempts + ". Limit hit, not retrying.");
      } catch (IOException | InterruptedException e) {
        e.printStackTrace();
        attempts += 1;
        if (attempts < retries)
          LOGGER.warning("Attempts: " + attempts + ". Retrying...");
        else
          LOGGER.severe("Attempts: " + attempts + ". Limit hit, not retrying.");
      }
    }

    return response == null ? null : response.body();
  }
}
