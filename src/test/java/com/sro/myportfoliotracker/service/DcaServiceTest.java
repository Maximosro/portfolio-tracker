package com.sro.myportfoliotracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sro.myportfoliotracker.model.DcaEntry;
import com.sro.myportfoliotracker.model.Position;
import com.sro.myportfoliotracker.repository.DcaEntryRepository;
import com.sro.myportfoliotracker.repository.PositionRepository;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DcaServiceTest {

  @Mock
  private DcaEntryRepository dcaEntryRepository;
  @Mock
  private PositionRepository positionRepository;

  @InjectMocks
  private DcaService dcaService;

  private Position existingPosition;

  @BeforeEach
  void setUp() {
    existingPosition = Position.builder()
        .ticker("VWCE")
        .name("Vanguard FTSE All-World")
        .shares(10.0)
        .avgPrice(100.0)
        .build();
  }

  // ──────────── addEntry ────────────

  @Test
  void addEntry_recalculatesFromAllDca() {
    when(positionRepository.findById("VWCE")).thenReturn(Optional.of(existingPosition));
    when(dcaEntryRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    when(positionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    DcaEntry newEntry = DcaEntry.builder()
        .ticker("VWCE")
        .shares(5.0)
        .price(110.0)
        .date(LocalDate.of(2025, 4, 1))
        .build();

    // Simular que después de guardar, findByTickerOrderByDateAsc devuelve todas las entradas
    DcaEntry existingDca = DcaEntry.builder().ticker("VWCE").shares(10.0).price(100.0)
        .date(LocalDate.of(2025, 1, 1)).build();
    when(dcaEntryRepository.findByTickerOrderByDateAsc("VWCE"))
        .thenReturn(List.of(existingDca, newEntry));

    dcaService.addEntry(newEntry);

    ArgumentCaptor<Position> posCaptor = ArgumentCaptor.forClass(Position.class);
    verify(positionRepository).save(posCaptor.capture());
    Position saved = posCaptor.getValue();

    // (10*100 + 5*110) / 15 = 1550/15 ≈ 103.33
    assertEquals(15.0, saved.getShares(), 0.001);
    assertEquals(103.333, saved.getAvgPrice(), 0.01);
  }

  @Test
  void addEntry_tickerNormalized() {
    when(positionRepository.findById("VWCE")).thenReturn(Optional.of(existingPosition));
    when(dcaEntryRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    when(positionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    DcaEntry entry = DcaEntry.builder()
        .ticker("vwce")
        .shares(1.0)
        .price(100.0)
        .date(LocalDate.of(2025, 4, 1))
        .build();

    when(dcaEntryRepository.findByTickerOrderByDateAsc("VWCE"))
        .thenReturn(List.of(entry));

    DcaEntry result = dcaService.addEntry(entry);
    assertEquals("VWCE", result.getTicker());
  }

  @Test
  void addEntry_nonExistentPosition_throws() {
    when(positionRepository.findById("NONE")).thenReturn(Optional.empty());

    DcaEntry entry = DcaEntry.builder()
        .ticker("NONE")
        .shares(1.0)
        .price(50.0)
        .date(LocalDate.now())
        .build();

    assertThrows(IllegalArgumentException.class, () -> dcaService.addEntry(entry));
  }

  @Test
  void addEntry_zeroShares_throws() {
    when(positionRepository.findById("VWCE")).thenReturn(Optional.of(existingPosition));

    DcaEntry entry = DcaEntry.builder()
        .ticker("VWCE")
        .shares(0.0)
        .price(50.0)
        .date(LocalDate.now())
        .build();

    assertThrows(IllegalArgumentException.class, () -> dcaService.addEntry(entry));
  }

  @Test
  void addEntry_nullPrice_throws() {
    when(positionRepository.findById("VWCE")).thenReturn(Optional.of(existingPosition));

    DcaEntry entry = DcaEntry.builder()
        .ticker("VWCE")
        .shares(1.0)
        .price(null)
        .date(LocalDate.now())
        .build();

    assertThrows(IllegalArgumentException.class, () -> dcaService.addEntry(entry));
  }

  // ──────────── deleteEntry ────────────

  @Test
  void deleteEntry_nonExistent_throws() {
    when(dcaEntryRepository.findById(999L)).thenReturn(Optional.empty());
    assertThrows(IllegalArgumentException.class, () -> dcaService.deleteEntry(999L));
  }

  @Test
  void deleteEntry_recalculatesPositionFromRemainingDca() {
    // Tenemos 2 entradas DCA: (10 @ 100) y (5 @ 120). Eliminamos la segunda.
    DcaEntry toDelete = DcaEntry.builder().id(2L).ticker("VWCE").shares(5.0).price(120.0)
        .date(LocalDate.of(2025, 3, 1)).build();
    DcaEntry remaining = DcaEntry.builder().id(1L).ticker("VWCE").shares(10.0).price(100.0)
        .date(LocalDate.of(2025, 1, 1)).build();

    when(dcaEntryRepository.findById(2L)).thenReturn(Optional.of(toDelete));
    when(positionRepository.findById("VWCE")).thenReturn(Optional.of(existingPosition));
    when(positionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    // Después de borrar la entrada 2, solo queda la entrada 1
    when(dcaEntryRepository.findByTickerOrderByDateAsc("VWCE"))
        .thenReturn(List.of(remaining));

    dcaService.deleteEntry(2L);

    verify(dcaEntryRepository).deleteById(2L);

    ArgumentCaptor<Position> posCaptor = ArgumentCaptor.forClass(Position.class);
    verify(positionRepository).save(posCaptor.capture());
    Position saved = posCaptor.getValue();

    // Solo queda (10 @ 100)
    assertEquals(10.0, saved.getShares(), 0.001);
    assertEquals(100.0, saved.getAvgPrice(), 0.001);
  }

  @Test
  void deleteEntry_lastEntry_setsPositionToZero() {
    DcaEntry lastEntry = DcaEntry.builder().id(1L).ticker("VWCE").shares(10.0).price(100.0)
        .date(LocalDate.of(2025, 1, 1)).build();

    when(dcaEntryRepository.findById(1L)).thenReturn(Optional.of(lastEntry));
    when(positionRepository.findById("VWCE")).thenReturn(Optional.of(existingPosition));
    when(positionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    // Después de borrar, no quedan entradas DCA
    when(dcaEntryRepository.findByTickerOrderByDateAsc("VWCE"))
        .thenReturn(Collections.emptyList());

    dcaService.deleteEntry(1L);

    ArgumentCaptor<Position> posCaptor = ArgumentCaptor.forClass(Position.class);
    verify(positionRepository).save(posCaptor.capture());
    Position saved = posCaptor.getValue();

    assertEquals(0.0, saved.getShares());
    assertEquals(0.0, saved.getAvgPrice());
  }

  // ──────────── updateEntry ────────────

  @Test
  void updateEntry_nonExistent_throws() {
    when(dcaEntryRepository.findById(999L)).thenReturn(Optional.empty());

    DcaEntry updated = DcaEntry.builder().shares(5.0).price(110.0).date(LocalDate.now()).build();
    assertThrows(IllegalArgumentException.class, () -> dcaService.updateEntry(999L, updated));
  }

  @Test
  void updateEntry_recalculatesPosition() {
    // Tenemos 2 entradas: (10 @ 100) y (5 @ 120). Modificamos la segunda a (5 @ 80).
    DcaEntry existingDca1 = DcaEntry.builder().id(1L).ticker("VWCE").shares(10.0).price(100.0)
        .date(LocalDate.of(2025, 1, 1)).build();
    DcaEntry existingDca2 = DcaEntry.builder().id(2L).ticker("VWCE").shares(5.0).price(120.0)
        .date(LocalDate.of(2025, 3, 1)).build();

    when(dcaEntryRepository.findById(2L)).thenReturn(Optional.of(existingDca2));
    when(dcaEntryRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    when(positionRepository.findById("VWCE")).thenReturn(Optional.of(existingPosition));
    when(positionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    // Después de actualizar, las entradas serán (10 @ 100) y (5 @ 80)
    DcaEntry updatedDca2 = DcaEntry.builder().id(2L).ticker("VWCE").shares(5.0).price(80.0)
        .date(LocalDate.of(2025, 3, 1)).build();
    when(dcaEntryRepository.findByTickerOrderByDateAsc("VWCE"))
        .thenReturn(List.of(existingDca1, updatedDca2));

    DcaEntry update = DcaEntry.builder().shares(5.0).price(80.0).date(LocalDate.of(2025, 3, 1))
        .build();
    dcaService.updateEntry(2L, update);

    ArgumentCaptor<Position> posCaptor = ArgumentCaptor.forClass(Position.class);
    verify(positionRepository).save(posCaptor.capture());
    Position saved = posCaptor.getValue();

    // (10*100 + 5*80) / 15 = 1400/15 ≈ 93.33
    assertEquals(15.0, saved.getShares(), 0.001);
    assertEquals(93.333, saved.getAvgPrice(), 0.01);
  }

  @Test
  void updateEntry_zeroShares_throws() {
    DcaEntry existing = DcaEntry.builder().id(1L).ticker("VWCE").shares(10.0).price(100.0)
        .date(LocalDate.of(2025, 1, 1)).build();
    when(dcaEntryRepository.findById(1L)).thenReturn(Optional.of(existing));

    DcaEntry update = DcaEntry.builder().shares(0.0).price(100.0).date(LocalDate.now()).build();
    assertThrows(IllegalArgumentException.class, () -> dcaService.updateEntry(1L, update));
  }

  @Test
  void updateEntry_nullPrice_throws() {
    DcaEntry existing = DcaEntry.builder().id(1L).ticker("VWCE").shares(10.0).price(100.0)
        .date(LocalDate.of(2025, 1, 1)).build();
    when(dcaEntryRepository.findById(1L)).thenReturn(Optional.of(existing));

    DcaEntry update = DcaEntry.builder().shares(5.0).price(null).date(LocalDate.now()).build();
    assertThrows(IllegalArgumentException.class, () -> dcaService.updateEntry(1L, update));
  }

  @Test
  void updateEntry_nullDate_throws() {
    DcaEntry existing = DcaEntry.builder().id(1L).ticker("VWCE").shares(10.0).price(100.0)
        .date(LocalDate.of(2025, 1, 1)).build();
    when(dcaEntryRepository.findById(1L)).thenReturn(Optional.of(existing));

    DcaEntry update = DcaEntry.builder().shares(5.0).price(100.0).date(null).build();
    assertThrows(IllegalArgumentException.class, () -> dcaService.updateEntry(1L, update));
  }

  // ──────────── recalculatePositionFromDca ────────────

  @Test
  void recalculate_multipleEntries_correctAverage() {
    // 3 compras: (10 @ 100), (5 @ 120), (15 @ 90)
    List<DcaEntry> entries = List.of(
        DcaEntry.builder().ticker("VWCE").shares(10.0).price(100.0).date(LocalDate.of(2025, 1, 1))
            .build(),
        DcaEntry.builder().ticker("VWCE").shares(5.0).price(120.0).date(LocalDate.of(2025, 2, 1))
            .build(),
        DcaEntry.builder().ticker("VWCE").shares(15.0).price(90.0).date(LocalDate.of(2025, 3, 1))
            .build()
    );
    when(dcaEntryRepository.findByTickerOrderByDateAsc("VWCE")).thenReturn(entries);
    when(positionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    dcaService.recalculatePositionFromDca("VWCE", existingPosition);

    // Total shares = 10 + 5 + 15 = 30
    // Total cost = 1000 + 600 + 1350 = 2950
    // Avg = 2950 / 30 = 98.333...
    assertEquals(30.0, existingPosition.getShares(), 0.001);
    assertEquals(98.333, existingPosition.getAvgPrice(), 0.01);
  }
}

