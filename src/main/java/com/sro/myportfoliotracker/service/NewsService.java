package com.sro.myportfoliotracker.service;

import com.sro.myportfoliotracker.model.Position;
import com.sro.myportfoliotracker.repository.PositionRepository;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@Service
@Slf4j
public class NewsService {

  private final PositionRepository positionRepository;
  private final HttpClient httpClient;
  private final DocumentBuilderFactory docFactory;

  public NewsService(PositionRepository positionRepository) {
    this.positionRepository = positionRepository;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    this.docFactory = DocumentBuilderFactory.newInstance();
  }

  public record NewsItem(String title, String source, String link, String date,
                         String dateIso, String relatedTicker) {

  }

  /**
   * Busca noticias recientes de Google News RSS para cada posición de la cartera. Cada noticia
   * incluye el ticker relacionado y se ordenan por fecha descendente.
   */
  private static final int NEWS_PER_ETF = 2;

  public List<NewsItem> fetchNews() {
    List<Position> positions = positionRepository.findAll().stream()
        .filter(p -> p.getShares() > 0)  // Excluir posiciones cerradas
        .toList();
    log.info("=== Inicio búsqueda de noticias para {} posiciones activas ===", positions.size());

    if (positions.isEmpty()) {
      return Collections.emptyList();
    }

    // Recopilar exactamente NEWS_PER_ETF noticias por posición
    Map<String, List<NewsItem>> newsByTicker = new LinkedHashMap<>();

    for (Position p : positions) {
      String ticker = p.getTicker();
      String name = p.getName();
      String sector = p.getSector();
      log.info("[{}] Buscando noticias (nombre: '{}', sector: '{}')", ticker, name, sector);

      // Construir varias queries de búsqueda de más específica a más genérica
      List<String> queries = buildQueries(ticker, name, sector);

      List<NewsItem> unique = new ArrayList<>();
      for (String query : queries) {
        if (unique.size() >= NEWS_PER_ETF) {
          break;
        }

        List<NewsItem> items = fetchForQuery(query, ticker);
        log.info("[{}]   Query '{}' → {} resultados", ticker, query, items.size());

        for (NewsItem item : items) {
          if (unique.size() >= NEWS_PER_ETF) {
            break;
          }

          // Filtrar noticias no relevantes financieramente
          if (!isFinanciallyRelevant(item.title(), ticker, sector)) {
            log.debug("[{}]   Descartada (no financiera): '{}'", ticker, item.title());
            continue;
          }

          boolean alreadyExists = unique.stream()
              .anyMatch(existing -> existing.title().equals(item.title()))
              || newsByTicker.values().stream()
              .flatMap(List::stream)
              .anyMatch(existing -> existing.title().equals(item.title()));
          if (!alreadyExists) {
            unique.add(item);
          }
        }
      }

      newsByTicker.put(ticker, unique);
      log.info("[{}] → {} noticias seleccionadas", ticker, unique.size());
      if (unique.isEmpty()) {
        log.warn("[{}] ⚠ No se encontraron noticias con ninguna query", ticker);
      }
    }

    List<NewsItem> result = newsByTicker.values().stream()
        .flatMap(List::stream)
        .sorted((a, b) -> {
          if (a.dateIso() == null || a.dateIso().isEmpty()) {
            return 1;
          }
          if (b.dateIso() == null || b.dateIso().isEmpty()) {
            return -1;
          }
          return b.dateIso().compareTo(a.dateIso());
        })
        .collect(Collectors.toList());

    log.info("=== Búsqueda completada: {} noticias totales ===", result.size());
    return result;
  }

  /**
   * Palabras clave que indican que una noticia es financieramente relevante.
   */
  private static final Set<String> FINANCIAL_KEYWORDS = Set.of(
      "bolsa", "mercado", "cotización", "cotizacion", "inversión", "inversion",
      "inversor", "inversores", "acciones", "acción", "accion", "etf",
      "fondo", "fondos", "dividendo", "dividendos", "rentabilidad",
      "cartera", "portfolio", "wall street", "ibex", "s&p", "nasdaq",
      "dow jones", "rendimiento", "beneficio", "beneficios", "resultados",
      "trimestral", "trimestre", "bpa", "per", "valoración", "valoracion",
      "analista", "analistas", "recomendación", "recomendacion",
      "subida", "bajada", "rally", "caída", "caida", "desplome",
      "máximo", "maximo", "mínimo", "minimo", "récord", "record",
      "inflación", "inflacion", "tipos de interés", "tipos de interes",
      "bce", "fed", "banco central", "política monetaria", "politica monetaria",
      "recesión", "recesion", "pib", "gdp", "crecimiento",
      "bonos", "renta fija", "renta variable", "deuda",
      "sector", "sectorial", "tecnología", "tecnologia", "energía", "energia",
      "financiero", "financiera", "bancario", "banca",
      "trading", "trader", "broker", "stock", "market", "index",
      "bull", "bear", "bearish", "bullish", "hedge",
      "yield", "earnings", "revenue", "profit", "loss",
      "commodities", "oro", "petróleo", "petroleo", "materias primas",
      "cripto", "bitcoin", "volatilidad", "riesgo",
      "compra", "venta", "rebalanceo", "exposición", "exposicion",
      "emerging", "markets", "growth", "value", "small cap", "large cap",
      "msci", "ftse", "stoxx", "eurostoxx", "dax", "cac"
  );

  /**
   * Construye varias queries para un ETF, de más específica a más genérica. Todas las queries
   * incluyen contexto financiero para evitar resultados irrelevantes.
   */
  private List<String> buildQueries(String ticker, String name, String sector) {
    List<String> queries = new ArrayList<>();

    // 1. Ticker + contexto bursátil directo
    queries.add(ticker + " cotización bolsa");

    // 2. Sector + bolsa/mercado (prioridad alta)
    if (sector != null && !sector.isBlank()) {
      queries.add(sector + " bolsa mercado inversión");
    }

    // 3. Nombre del ETF + mercado
    if (name != null && !name.isBlank() && name.length() > 5) {
      queries.add("\"" + name + "\" mercado");
    }

    // 4. Ticker + ETF + inversión
    queries.add(ticker + " ETF inversión");

    // 5. Palabras clave del nombre con contexto financiero
    if (name != null && !name.isBlank()) {
      String keywords = Arrays.stream(name.split("\\s+"))
          .filter(w -> w.length() > 3)
          .filter(w -> !Set.of("UCITS", "Dist", "Acc", "EUR", "USD", "GBP", "ETF", "Fund", "Index",
                  "iShares", "Xtrackers", "Amundi", "SPDR", "Vanguard", "Invesco", "VanEck", "Core",
                  "Global")
              .contains(w))
          .limit(3)
          .collect(Collectors.joining(" "));
      if (!keywords.isBlank()) {
        queries.add(keywords + " mercado financiero");
      }
    }

    // 6. Sector genérico financiero como fallback
    if (sector != null && !sector.isBlank()) {
      queries.add(sector + " análisis financiero");
    }

    return queries;
  }

  /**
   * Comprueba si el título de una noticia es financieramente relevante. Busca la presencia de al
   * menos una palabra clave financiera.
   */
  private boolean isFinanciallyRelevant(String title, String ticker, String sector) {
    if (title == null || title.isBlank()) {
      return false;
    }
    String lower = title.toLowerCase();

    // Si menciona el ticker directamente, siempre es relevante
    if (lower.contains(ticker.toLowerCase())) {
      return true;
    }

    // Si menciona el sector, es relevante
    if (sector != null && !sector.isBlank()) {
      for (String word : sector.toLowerCase().split("\\s+")) {
        if (word.length() > 3 && lower.contains(word)) {
          return true;
        }
      }
    }

    // Comprobar presencia de palabras clave financieras
    for (String keyword : FINANCIAL_KEYWORDS) {
      if (lower.contains(keyword)) {
        return true;
      }
    }

    return false;
  }

  private List<NewsItem> fetchForQuery(String query, String relatedTicker) {
    try {
      return searchGoogleNews(query, relatedTicker);
    } catch (Exception e) {
      log.warn("Error buscando noticias para '{}': {}", query, e.getMessage());
      return Collections.emptyList();
    }
  }

  private List<NewsItem> searchGoogleNews(String query, String relatedTicker) throws Exception {
    String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
    String url = "https://news.google.com/rss/search?q=" + encoded
        + "+when:3d&hl=es&gl=ES&ceid=ES:es";

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        .timeout(Duration.ofSeconds(15))
        .GET()
        .build();

    HttpResponse<InputStream> response = httpClient.send(request,
        HttpResponse.BodyHandlers.ofInputStream());

    if (response.statusCode() != 200) {
      log.warn("Google News RSS returned status {} for query '{}'", response.statusCode(), query);
      return Collections.emptyList();
    }

    return parseRss(response.body(), relatedTicker);
  }

  private List<NewsItem> parseRss(InputStream inputStream, String relatedTicker) throws Exception {
    DocumentBuilder builder = docFactory.newDocumentBuilder();
    Document doc = builder.parse(inputStream);
    doc.getDocumentElement().normalize();

    NodeList items = doc.getElementsByTagName("item");
    List<NewsItem> result = new ArrayList<>();

    for (int i = 0; i < items.getLength() && i < 15; i++) {
      Element item = (Element) items.item(i);

      String title = getTagText(item, "title");
      String link = getTagText(item, "link");
      String pubDate = getTagText(item, "pubDate");
      String source = getTagText(item, "source");

      // Parsear fecha para ordenación y formato display
      String dateIso = parseIsoDate(pubDate);
      String dateFormatted = formatDate(pubDate);

      // Limpiar título (Google News añade " - Fuente" al final)
      String cleanTitle = title;
      if (source != null && !source.isEmpty() && title.endsWith(" - " + source)) {
        cleanTitle = title.substring(0, title.length() - source.length() - 3);
      }

      result.add(new NewsItem(cleanTitle, source, link, dateFormatted, dateIso, relatedTicker));
    }

    return result;
  }

  private String getTagText(Element parent, String tag) {
    NodeList nodes = parent.getElementsByTagName(tag);
    if (nodes.getLength() > 0 && nodes.item(0).getTextContent() != null) {
      return nodes.item(0).getTextContent().trim();
    }
    return "";
  }

  private String parseIsoDate(String pubDate) {
    if (pubDate == null || pubDate.isEmpty()) {
      return "";
    }
    try {
      ZonedDateTime zdt = ZonedDateTime.parse(pubDate, DateTimeFormatter.RFC_1123_DATE_TIME);
      return zdt.toInstant().toString();
    } catch (Exception e) {
      return "";
    }
  }

  private String formatDate(String pubDate) {
    if (pubDate == null || pubDate.isEmpty()) {
      return "";
    }
    try {
      ZonedDateTime zdt = ZonedDateTime.parse(pubDate, DateTimeFormatter.RFC_1123_DATE_TIME);
      return zdt.format(DateTimeFormatter.ofPattern("d MMM, HH:mm", new Locale("es", "ES")));
    } catch (Exception e) {
      return pubDate;
    }
  }
}

