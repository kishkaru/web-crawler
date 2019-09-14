import java.io.IOException;
import java.lang.System.Logger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.System.Logger.Level.*;

public class Crawler {

  /**
   * Custom logger class
   */
  public static class ConsoleLogger implements System.Logger {
    @Override
    public String getName() {
      return "ConsoleLogger";
    }

    @Override
    public boolean isLoggable(Level level) {
      return true;
    }

    @Override
    public void log(Level level, ResourceBundle bundle, String msg, Throwable thrown) {
      System.out.printf("[%s %s]: %s - %s%n", level, Thread.currentThread().getName(), msg, thrown);
    }

    @Override
    public void log(Level level, ResourceBundle bundle, String format, Object... params) {
      System.out.printf("[%s %s]: %s%n", level, Thread.currentThread().getName(),
        MessageFormat.format(format, params));
    }
  }

  private static HttpClient CLIENT;
  private static Logger LOGGER;

  public static void main (String[] args) {
    LOGGER = new ConsoleLogger();
    CLIENT = HttpClient.newBuilder()
      .followRedirects(HttpClient.Redirect.NORMAL)
      .build();

    BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    Set<String> visited = Collections.synchronizedSet(new HashSet<>());

    final String START_CRAWL_URL = "https://www.oracle.com";
    final String RESTRICT_TO_DOMAIN = "oracle.com";
    queue.add(START_CRAWL_URL);

    // Create a thread pool with MAX_THREADS equal to number of cores in CPU
    final int MAX_THREADS = Runtime.getRuntime().availableProcessors();
    LOGGER.log(INFO, "Creating thread pool with " + MAX_THREADS + " threads.");
    ExecutorService es = Executors.newFixedThreadPool(MAX_THREADS);

    // Create MAX_THREADS amount of work for the thread pool
    for (int i = 0; i < MAX_THREADS; i++) {
      es.execute( () -> {

        // Execute indefinitely until queue is empty
        while (true) {
          String currUrl = null;
          try {
            // Remove the next URL to process, or return null if nothing to process after waiting
            currUrl = queue.poll(3, TimeUnit.SECONDS);

            // Exit thread if nothing to process.
            if (currUrl == null) {
              LOGGER.log(WARNING, "Exiting due to nothing to process.");
              break;
            }
          } catch (InterruptedException e) {
            e.printStackTrace();
            // Exit thread if error.
            LOGGER.log(ERROR, "Exiting thread due to an error.");
            break;
          }

          if (!visited.contains(currUrl)) {
            visited.add(currUrl);

            // Get the HTML for the URL
            LOGGER.log(INFO, "Processing: " + currUrl);
            String html = getHTML(currUrl, 3);

            // If unable to get HTML, ignore this page
            if (html == null) {
              continue;
            }

            // Get the list of URLs this page contains
            List<String> urls = getUrls(html, RESTRICT_TO_DOMAIN);

            // Add to the queue if not already visited
            queue.addAll(urls);
          }
        }
      });
    }

    // Shut down executor service once all threads are finished
    es.shutdown();
    LOGGER.log(INFO, "Done.");
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
        LOGGER.log(WARNING, "HTTP status code: " + response.statusCode()
          + ". Attempts: " + attempts
          + (attempts < retries ? ". Retrying..." : ". Limit hit, not retrying."));

      } catch (IOException | InterruptedException e) {
        e.printStackTrace();
        attempts += 1;
        LOGGER.log(WARNING, "Attempts: " + attempts
          + (attempts < retries ? ". Retrying..." : ". Limit hit, not retrying."));
      }
    }

    return response == null ? null : response.body();
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

        // Do not crawl in images or resources
        if (m.group(3) == null || !(m.group(3).endsWith(".ico")
          || m.group(3).endsWith(".png")
          || m.group(3).endsWith(".jpg")
          || m.group(3).endsWith(".jpeg")
          || m.group(3).endsWith(".css")
          || m.group(3).endsWith(".js"))) {
          String url = m.group();
          urls.add(url.endsWith("/") ? url.substring(0, url.length()-1) : url);
        }
    }

    return urls;
  }
}
