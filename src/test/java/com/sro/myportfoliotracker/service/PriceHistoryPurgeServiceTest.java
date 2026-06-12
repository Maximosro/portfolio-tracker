package com.sro.myportfoliotracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sro.myportfoliotracker.model.PriceHistory;
import com.sro.myportfoliotracker.repository.PriceHistoryRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PriceHistoryPurgeServiceTest {

  @Mock
  private PriceHistoryRepository priceHistoryRepository;

  @Mock
  private ActivityLogService activityLog;

  @InjectMocks
  private PriceHistoryPurgeService purgeService;

  @Captor
  private ArgumentCaptor<List<Long>> idsCaptor;

  private static final String TICKER = "AAPL";

  @BeforeEach
  void setUp() {
    lenient().when(priceHistoryRepository.findDistinctTickers()).thenReturn(List.of(TICKER));
  }

  /**
   * Registros de hace 10 días (zona horaria): con 6 registros en la misma hora, debe eliminar 5 y
   * mantener solo el último de cada hora.
   */
  @Test
  void purge_compactsHourlyZone_keepsLastPerHour() {
    // 6 registros separados por 10 minutos, todos en la misma hora, hace 10 días
    Instant base = Instant.now().minus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    List<PriceHistory> records = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      records.add(PriceHistory.builder()
          .id((long) (i + 1))
          .ticker(TICKER)
          .timestamp(base.plus(i * 10L, ChronoUnit.MINUTES))
          .rawPrice(150.0 + i)
          .currency("USD")
          .priceEur(140.0 + i)
          .build());
    }

    // 3 zonas: 1-7d (hourly), 7-30d (daily), 30-365d (weekly)
    // Records de hace 10 días → zona 7-30d (daily bucket)
    when(priceHistoryRepository.findByTickerAndTimestampBetweenOrderByTimestampAsc(
        eq(TICKER), any(Instant.class), any(Instant.class)))
        .thenReturn(List.of())  // zona 1-7d: vacía (records son más antiguos)
        .thenReturn(records)    // zona 7-30d: 6 registros en el mismo día
        .thenReturn(List.of()); // zona 30-365d: vacía

    int removed = purgeService.purge();

    // 6 registros en el mismo día → compactación diaria: elimina 5, mantiene el último (id=6)
    assertEquals(5, removed);
    verify(priceHistoryRepository).deleteAllByIdIn(idsCaptor.capture());
    List<Long> deletedIds = idsCaptor.getValue();
    assertEquals(5, deletedIds.size());
    assertTrue(deletedIds.containsAll(List.of(1L, 2L, 3L, 4L, 5L)));
    assertFalse(deletedIds.contains(6L)); // El último se mantiene
  }

  /**
   * Registros de hace 45 días (zona 30-365d, weekly): con 3 registros en el mismo día (misma semana),
   * debe eliminar 2 y mantener solo el último.
   */
  @Test
  void purge_compactsDailyZone_keepsLastPerDay() {
    Instant base = Instant.now().minus(45, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
    List<PriceHistory> records = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      records.add(PriceHistory.builder()
          .id((long) (100 + i))
          .ticker(TICKER)
          .timestamp(base.plus(i * 4L, ChronoUnit.HOURS))
          .rawPrice(200.0)
          .currency("USD")
          .priceEur(190.0)
          .build());
    }

    // 3 zonas: 1-7d (hourly), 7-30d (daily), 30-365d (weekly)
    // Records de hace 45 días → zona 30-365d (weekly bucket)
    when(priceHistoryRepository.findByTickerAndTimestampBetweenOrderByTimestampAsc(
        eq(TICKER), any(Instant.class), any(Instant.class)))
        .thenReturn(List.of())  // zona 1-7d: vacía
        .thenReturn(List.of())  // zona 7-30d: vacía
        .thenReturn(records);   // zona 30-365d: 3 registros en la misma semana

    int removed = purgeService.purge();

    assertEquals(2, removed);
    verify(priceHistoryRepository).deleteAllByIdIn(idsCaptor.capture());
    List<Long> deletedIds = idsCaptor.getValue();
    assertEquals(2, deletedIds.size());
    assertTrue(deletedIds.containsAll(List.of(100L, 101L)));
    assertFalse(deletedIds.contains(102L)); // El último se mantiene
  }

  /**
   * Registros recientes (< 7 días) NO deben ser purgados.
   */
  @Test
  void purge_doesNotTouchRecentData() {
    // Ambas zonas devuelven vacío (todo está en zona reciente)
    when(priceHistoryRepository.findByTickerAndTimestampBetweenOrderByTimestampAsc(
        eq(TICKER), any(Instant.class), any(Instant.class)))
        .thenReturn(List.of());

    int removed = purgeService.purge();

    assertEquals(0, removed);
    verify(priceHistoryRepository, never()).deleteAllByIdIn(any());
  }

  /**
   * Si solo hay 1 registro por bucket, no se elimina nada.
   */
  @Test
  void purge_singleRecordPerBucket_noDeletes() {
    // Un registro por DÍA diferente, en zona 7-30d (daily bucket) → 1 por bucket
    Instant base = Instant.now().minus(15, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
    List<PriceHistory> records = List.of(
        PriceHistory.builder().id(1L).ticker(TICKER).timestamp(base).rawPrice(100.0).currency("USD")
            .priceEur(95.0).build(),
        PriceHistory.builder().id(2L).ticker(TICKER).timestamp(base.plus(1, ChronoUnit.DAYS))
            .rawPrice(101.0).currency("USD").priceEur(96.0).build(),
        PriceHistory.builder().id(3L).ticker(TICKER).timestamp(base.plus(2, ChronoUnit.DAYS))
            .rawPrice(102.0).currency("USD").priceEur(97.0).build()
    );

    // 3 zonas: zona 1-7d vacía, zona 7-30d con 3 registros en 3 días distintos (1/bucket → 0 borrados), zona 30-365d vacía
    when(priceHistoryRepository.findByTickerAndTimestampBetweenOrderByTimestampAsc(
        eq(TICKER), any(Instant.class), any(Instant.class)))
        .thenReturn(List.of(), records, List.of());

    int removed = purgeService.purge();

    assertEquals(0, removed);
    verify(priceHistoryRepository, never()).deleteAllByIdIn(any());
  }

  /**
   * Múltiples tickers: cada uno se compacta independientemente.
   */
  @Test
  void purge_multipleTickersCompactedIndependently() {
    when(priceHistoryRepository.findDistinctTickers()).thenReturn(List.of("AAPL", "MSFT"));

    Instant base = Instant.now().minus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);

    List<PriceHistory> aaplRecords = List.of(
        PriceHistory.builder().id(1L).ticker("AAPL").timestamp(base).rawPrice(150.0).currency("USD")
            .priceEur(140.0).build(),
        PriceHistory.builder().id(2L).ticker("AAPL").timestamp(base.plus(5, ChronoUnit.MINUTES))
            .rawPrice(151.0).currency("USD").priceEur(141.0).build()
    );
    List<PriceHistory> msftRecords = List.of(
        PriceHistory.builder().id(3L).ticker("MSFT").timestamp(base).rawPrice(300.0).currency("USD")
            .priceEur(280.0).build(),
        PriceHistory.builder().id(4L).ticker("MSFT").timestamp(base.plus(5, ChronoUnit.MINUTES))
            .rawPrice(301.0).currency("USD").priceEur(281.0).build(),
        PriceHistory.builder().id(5L).ticker("MSFT").timestamp(base.plus(10, ChronoUnit.MINUTES))
            .rawPrice(302.0).currency("USD").priceEur(282.0).build()
    );

    // 3 zonas por ticker: AAPL (zona 7-30d) y MSFT (zona 7-30d)
    when(priceHistoryRepository.findByTickerAndTimestampBetweenOrderByTimestampAsc(
        eq("AAPL"), any(Instant.class), any(Instant.class)))
        .thenReturn(List.of()).thenReturn(aaplRecords).thenReturn(List.of());
    when(priceHistoryRepository.findByTickerAndTimestampBetweenOrderByTimestampAsc(
        eq("MSFT"), any(Instant.class), any(Instant.class)))
        .thenReturn(List.of()).thenReturn(msftRecords).thenReturn(List.of());

    int removed = purgeService.purge();

    // AAPL: 1 eliminado (mantener id=2), MSFT: 2 eliminados (mantener id=5)
    assertEquals(3, removed);
  }

  /**
   * Sin tickers → 0 eliminados, sin llamadas al repo.
   */
  @Test
  void purge_noTickers_returnsZero() {
    when(priceHistoryRepository.findDistinctTickers()).thenReturn(List.of());

    int removed = purgeService.purge();

    assertEquals(0, removed);
    verify(priceHistoryRepository, never()).findByTickerAndTimestampBetweenOrderByTimestampAsc(
        any(), any(), any());
  }

  @Test
  void getStats_returnsCorrectMap() {
    when(priceHistoryRepository.count()).thenReturn(5000L);
    // 4 llamadas a countByTimestampBefore: dayAgo, weekAgo, monthAgo, yearAgo
    when(priceHistoryRepository.countByTimestampBefore(any(Instant.class)))
        .thenReturn(3000L)  // olderThan1Day  (call 1)
        .thenReturn(1000L)  // olderThan7Days  (call 2)
        .thenReturn(500L)   // olderThan30Days (call 3)
        .thenReturn(100L);  // olderThan365Days (call 4)

    Map<String, Object> stats = purgeService.getStats();

    assertEquals(5000L, stats.get("totalRecords"));
    assertEquals(2000L, stats.get("today"));            // 5000 - 3000
    assertEquals(3000L, stats.get("olderThan1Day"));
    assertEquals(1000L, stats.get("olderThan7Days"));
    assertEquals(500L, stats.get("olderThan30Days"));
    assertEquals(100L, stats.get("olderThan365Days"));
  }
}



