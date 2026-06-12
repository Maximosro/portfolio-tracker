package com.sro.myportfoliotracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sro.myportfoliotracker.model.Position;
import com.sro.myportfoliotracker.repository.DcaEntryRepository;
import com.sro.myportfoliotracker.repository.PositionDetailRepository;
import com.sro.myportfoliotracker.repository.PositionRepository;
import com.sro.myportfoliotracker.repository.PriceHistoryRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PositionServiceTest {

  @Mock
  private PositionRepository positionRepository;

  @Mock
  private PositionDetailRepository positionDetailRepository;

  @Mock
  private DcaEntryRepository dcaEntryRepository;

  @Mock
  private PriceHistoryRepository priceHistoryRepository;

  @Mock
  private MarketScheduleService marketScheduleService;

  @Mock
  private DcaService dcaService;

  @InjectMocks
  private PositionService positionService;

  @Test
  void create_normalizesTicker() {
    when(positionRepository.existsById("VWCE")).thenReturn(false);
    when(positionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    Position pos = Position.builder()
        .ticker("vwce")
        .name("Test")
        .shares(10.0)
        .avgPrice(100.0)
        .build();

    Position result = positionService.create(pos);
    assertEquals("VWCE", result.getTicker());
  }

  @Test
  void create_duplicateTicker_throws() {
    when(positionRepository.existsById("VWCE")).thenReturn(true);

    Position pos = Position.builder()
        .ticker("VWCE")
        .shares(10.0)
        .avgPrice(100.0)
        .build();

    assertThrows(IllegalArgumentException.class, () -> positionService.create(pos));
  }

  @Test
  void create_setsDefaultTargetPct() {
    when(positionRepository.existsById("TEST")).thenReturn(false);
    when(positionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    Position pos = Position.builder()
        .ticker("TEST")
        .shares(10.0)
        .avgPrice(100.0)
        .targetPct(null)
        .build();

    Position result = positionService.create(pos);
    assertEquals(0.0, result.getTargetPct());
  }

  @Test
  void update_preservesSector() {
    Position existing = Position.builder()
        .ticker("VWCE")
        .name("Old Name")
        .shares(10.0)
        .avgPrice(100.0)
        .sector("tecnología")
        .build();

    when(positionRepository.findById("VWCE")).thenReturn(Optional.of(existing));
    when(positionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    Position updated = Position.builder()
        .ticker("VWCE")
        .name("New Name")
        .shares(20.0)
        .avgPrice(105.0)
        .sector("energía nuclear")
        .build();

    Position result = positionService.update("VWCE", updated);

    assertEquals("New Name", result.getName());
    assertEquals(20.0, result.getShares());
    assertEquals("energía nuclear", result.getSector());
  }

  @Test
  void update_nonExistent_throws() {
    when(positionRepository.findById("NONE")).thenReturn(Optional.empty());

    Position updated = Position.builder()
        .ticker("NONE")
        .shares(10.0)
        .avgPrice(100.0)
        .build();

    assertThrows(IllegalArgumentException.class, () -> positionService.update("NONE", updated));
  }

  @Test
  void delete_nonExistent_throws() {
    when(positionRepository.existsById("NONE")).thenReturn(false);
    assertThrows(IllegalArgumentException.class, () -> positionService.delete("NONE"));
  }

  @Test
  void delete_existing_deletesPositionAndAllRelatedData() {
    when(positionRepository.existsById("VWCE")).thenReturn(true);
    when(positionDetailRepository.existsById("VWCE")).thenReturn(true);

    positionService.delete("VWCE");

    // Verifica que se eliminan TODOS los datos asociados
    verify(positionDetailRepository).deleteById("VWCE");
    verify(dcaEntryRepository).deleteAllByTicker("VWCE");
    verify(priceHistoryRepository).deleteAllByTicker("VWCE");
    verify(positionRepository).deleteById("VWCE");
  }

  @Test
  void delete_existing_withoutDetail_deletesAllRelatedData() {
    when(positionRepository.existsById("AAPL")).thenReturn(true);
    when(positionDetailRepository.existsById("AAPL")).thenReturn(false);

    positionService.delete("AAPL");

    // No debe intentar borrar el detalle que no existe
    verify(positionDetailRepository, never()).deleteById("AAPL");
    // Pero sí debe borrar DCA y PriceHistory
    verify(dcaEntryRepository).deleteAllByTicker("AAPL");
    verify(priceHistoryRepository).deleteAllByTicker("AAPL");
    verify(positionRepository).deleteById("AAPL");
  }
}


