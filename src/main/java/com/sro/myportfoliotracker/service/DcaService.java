package com.sro.myportfoliotracker.service;

import com.sro.myportfoliotracker.dto.ImportOperationDto;
import com.sro.myportfoliotracker.dto.ImportResultDto;
import com.sro.myportfoliotracker.model.DcaEntry;
import com.sro.myportfoliotracker.model.Position;
import com.sro.myportfoliotracker.repository.DcaEntryRepository;
import com.sro.myportfoliotracker.repository.PositionRepository;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DcaService {

  private final DcaEntryRepository dcaEntryRepository;
  private final PositionRepository positionRepository;
  private final ActivityLogService activityLog;

  public List<DcaEntry> findAll() {
    return this.dcaEntryRepository.findAllByOrderByDateDesc();
  }

  public List<DcaEntry> findByTicker(String ticker) {
    return this.dcaEntryRepository.findByTickerOrderByDateDesc(ticker.toUpperCase());
  }

  /**
   * Registra una compra o venta DCA y recalcula shares + avgPrice de la posición desde TODOS los
   * registros DCA para garantizar consistencia.
   */
  @Transactional
  public DcaEntry addEntry(DcaEntry entry) {
    final String ticker = entry.getTicker().toUpperCase();
    entry.setTicker(ticker);

    // Normalizar tipo: por defecto BUY
    if (entry.getType() == null || entry.getType().isBlank()) {
      entry.setType("BUY");
    }
    entry.setType(entry.getType().toUpperCase());

    if (!"BUY".equals(entry.getType()) && !"SELL".equals(entry.getType())) {
      throw new IllegalArgumentException(
          "Tipo de operación inválido: " + entry.getType() + ". Usa BUY o SELL");
    }

    final Position position = this.positionRepository.findById(ticker)
        .orElseThrow(
            () -> new IllegalArgumentException("No existe posición con ticker: " + ticker));

    if (entry.getShares() == null || entry.getShares() <= 0) {
      throw new IllegalArgumentException("El número de acciones debe ser mayor que 0");
    }
    if (entry.getPrice() == null || entry.getPrice() <= 0) {
      throw new IllegalArgumentException("El precio debe ser mayor que 0");
    }
    if (entry.getDate() == null) {
      throw new IllegalArgumentException("La fecha es obligatoria");
    }

    // Validar que no se vendan más acciones de las que se tienen
    if ("SELL".equals(entry.getType())) {
      final double currentShares = this.calculateCurrentShares(ticker);
      if (entry.getShares() > currentShares + 0.000001) {
        throw new IllegalArgumentException(
            String.format("No puedes vender %.6f acciones. Solo tienes %.6f",
                entry.getShares(), currentShares));
      }
    }

    // Guardar primero la entrada DCA
    final DcaEntry saved = this.dcaEntryRepository.save(entry);

    // Recalcular la posición desde TODOS los DCA (incluido el nuevo)
    this.recalculatePositionFromDca(ticker, position);

    final String typeLabel = "SELL".equals(entry.getType()) ? "Venta" : "Compra";
    this.activityLog.success("DCA",
        typeLabel + " " + ticker + ": " + String.format("%.6f", entry.getShares()) + " acc. a "
            + String.format("%.4f", entry.getPrice()) + " €",
        ticker, "SELL".equals(entry.getType()) ? "🔴" : "🟢");

    return saved;
  }

  /**
   * Modifica una entrada DCA existente y recalcula los agregados de la posición.
   */
  @Transactional
  public DcaEntry updateEntry(Long id, DcaEntry updated) {
    final DcaEntry existing = this.dcaEntryRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("No existe entrada DCA con id: " + id));

    if (updated.getShares() == null || updated.getShares() <= 0) {
      throw new IllegalArgumentException("El número de acciones debe ser mayor que 0");
    }
    if (updated.getPrice() == null || updated.getPrice() <= 0) {
      throw new IllegalArgumentException("El precio debe ser mayor que 0");
    }
    if (updated.getDate() == null) {
      throw new IllegalArgumentException("La fecha es obligatoria");
    }

    final String ticker = existing.getTicker();

    // Actualizar campos de la entrada DCA
    existing.setShares(updated.getShares());
    existing.setPrice(updated.getPrice());
    existing.setDate(updated.getDate());
    final DcaEntry saved = this.dcaEntryRepository.save(existing);

    // Recalcular la posición desde todos los DCA
    final Position position = this.positionRepository.findById(ticker)
        .orElseThrow(
            () -> new IllegalArgumentException("No existe posición con ticker: " + ticker));
    this.recalculatePositionFromDca(ticker, position);

    log.info(
        "Entrada DCA id={} actualizada para ticker {}. Posición recalculada: shares={}, avgPrice={}",
        id,
        ticker, position.getShares(), position.getAvgPrice());

    return saved;
  }

  /**
   * Elimina una entrada DCA y recalcula shares + avgPrice de la posición desde los registros DCA
   * restantes.
   */
  @Transactional
  public void deleteEntry(Long id) {
    final DcaEntry entry = this.dcaEntryRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("No existe entrada DCA con id: " + id));

    final String ticker = entry.getTicker();

    // Eliminar la entrada
    this.dcaEntryRepository.deleteById(id);

    // Recalcular la posición desde los DCA restantes
    final Position position = this.positionRepository.findById(ticker)
        .orElseThrow(
            () -> new IllegalArgumentException("No existe posición con ticker: " + ticker));
    this.recalculatePositionFromDca(ticker, position);

    log.info(
        "Entrada DCA id={} eliminada para ticker {}. Posición recalculada: shares={}, avgPrice={}",
        id, ticker,
        position.getShares(), position.getAvgPrice());
    this.activityLog.info("DCA", "Entrada DCA eliminada para " + ticker, ticker, "🗑️");
  }

  /**
   * Calcula las acciones actuales de un ticker sumando compras y restando ventas.
   */
  private double calculateCurrentShares(String ticker) {
    final List<DcaEntry> allEntries = this.dcaEntryRepository.findByTickerOrderByDateAsc(ticker);
    final double buyShares = allEntries.stream().filter(e -> !"SELL".equals(e.getType()))
        .mapToDouble(DcaEntry::getShares).sum();
    final double sellShares = allEntries.stream().filter(e -> "SELL".equals(e.getType()))
        .mapToDouble(DcaEntry::getShares).sum();
    return buyShares - sellShares;
  }

  /**
   * Recalcula shares y avgPrice de una posición sumando TODOS los registros DCA existentes para ese
   * ticker.
   * <p>
   * - avgPrice se calcula solo con las COMPRAS (BUY): Σ(shares_i * price_i) / Σ(shares_i) - shares
   * actuales = Σ(BUY shares) - Σ(SELL shares) - Si no quedan entradas DCA, pone shares=0 y
   * avgPrice=0.
   */
  void recalculatePositionFromDca(String ticker, Position position) {
    final List<DcaEntry> allEntries = this.dcaEntryRepository.findByTickerOrderByDateAsc(ticker);

    if (allEntries.isEmpty()) {
      position.setShares(0.0);
      position.setAvgPrice(0.0);
      log.warn("No quedan entradas DCA para ticker {}. Posición puesta a shares=0, avgPrice=0",
          ticker);
    } else {
      // avgPrice basado SOLO en compras (las ventas no afectan el precio medio de
      // compra)
      final double buyShares = allEntries.stream().filter(e -> !"SELL".equals(e.getType()))
          .mapToDouble(DcaEntry::getShares).sum();
      final double buyCost = allEntries.stream().filter(e -> !"SELL".equals(e.getType()))
          .mapToDouble(e -> e.getShares() * e.getPrice()).sum();
      final double avgPrice = buyShares > 0 ? buyCost / buyShares : 0.0;

      // Shares actuales = compras - ventas
      final double sellShares = allEntries.stream().filter(e -> "SELL".equals(e.getType()))
          .mapToDouble(DcaEntry::getShares).sum();
      final double currentShares = buyShares - sellShares;

      position.setShares(Math.max(0.0, currentShares));
      position.setAvgPrice(avgPrice);
    }

    this.positionRepository.save(position);
  }

  /**
   * Importación masiva de operaciones desde JSON. Crea las posiciones que no existan y registra
   * todos los DCA entries.
   */
  @Transactional
  public ImportResultDto importBulk(List<ImportOperationDto> operations) {
    final List<String> warnings = new ArrayList<>();
    final List<String> errors = new ArrayList<>();
    int positionsCreated = 0;
    int operationsImported = 0;

    if (operations == null || operations.isEmpty()) {
      throw new IllegalArgumentException("La lista de operaciones está vacía");
    }

    // Coleccionar tickers únicos para crear posiciones si no existen
    final Map<String, ImportOperationDto> tickerFirstOp = new LinkedHashMap<>();
    for (final ImportOperationDto op : operations) {
      if (op.getTicker() == null || op.getTicker().isBlank()) {
        errors.add("Operación sin ticker — ignorada");
        continue;
      }
      final String ticker = op.getTicker().toUpperCase().trim();
      tickerFirstOp.putIfAbsent(ticker, op);
    }

    // Paso 1: Crear posiciones que no existan
    final String[] defaultColors = {"#4f98a3", "#e8af34", "#6daa45", "#5591c7", "#fdab43",
        "#a86fdf", "#dd6974",
        "#bb653b", "#d163a7", "#01696f"};
    int colorIdx = 0;
    for (final Map.Entry<String, ImportOperationDto> entry : tickerFirstOp.entrySet()) {
      final String ticker = entry.getKey();
      final ImportOperationDto firstOp = entry.getValue();

      if (!this.positionRepository.existsById(ticker)) {
        final Position newPos = Position.builder().ticker(ticker)
            .name(firstOp.getName() != null && !firstOp.getName().isBlank() ? firstOp.getName()
                : ticker)
            .yahooTicker(firstOp.getYahooTicker() != null && !firstOp.getYahooTicker().isBlank()
                ? firstOp.getYahooTicker()
                : ticker)
            .shares(0.0).avgPrice(0.0)
            .color(firstOp.getColor() != null && !firstOp.getColor().isBlank()
                ? firstOp.getColor()
                : defaultColors[colorIdx % defaultColors.length])
            .sector(firstOp.getSector()).targetPct(0.0).build();
        this.positionRepository.save(newPos);
        positionsCreated++;
        colorIdx++;
        log.info("Importación: posición {} creada automáticamente", ticker);
      }
    }

    // Paso 2: Registrar todas las operaciones DCA (ordenadas por fecha)
    final List<ImportOperationDto> sorted = new ArrayList<>(operations);
    sorted.sort((a, b) -> {
      final String da = a.getDate() != null ? a.getDate() : "";
      final String db = b.getDate() != null ? b.getDate() : "";
      return da.compareTo(db);
    });

    for (int i = 0; i < sorted.size(); i++) {
      final ImportOperationDto op = sorted.get(i);
      final int lineNum = i + 1;

      // Validaciones
      if (op.getTicker() == null || op.getTicker().isBlank()) {
        continue; // ya reportado arriba
      }
      final String ticker = op.getTicker().toUpperCase().trim();

      if (op.getShares() == null || op.getShares() <= 0) {
        errors.add("Línea " + lineNum + " (" + ticker + "): acciones inválidas (" + op.getShares()
            + ") — ignorada");
        continue;
      }
      if (op.getPrice() == null || op.getPrice() <= 0) {
        errors.add(
            "Línea " + lineNum + " (" + ticker + "): precio inválido (" + op.getPrice()
                + ") — ignorada");
        continue;
      }

      final LocalDate date;
      try {
        date = LocalDate.parse(op.getDate());
      } catch (final DateTimeParseException | NullPointerException e) {
        errors.add("Línea " + lineNum + " (" + ticker + "): fecha inválida '" + op.getDate()
            + "' — ignorada");
        continue;
      }

      String type = "BUY";
      if (op.getType() != null && !op.getType().isBlank()) {
        type = op.getType().toUpperCase().trim();
        // SAVINGS_PLAN (Trade Republic, etc.) se trata como compra
        if ("SAVINGS_PLAN".equals(type) || "SAVING".equals(type) || "PLAN".equals(type)) {
          type = "BUY";
        } else if (!"BUY".equals(type) && !"SELL".equals(type)) {
          warnings.add("Línea " + lineNum + " (" + ticker + "): tipo '" + op.getType()
              + "' no reconocido, se usará BUY");
          type = "BUY";
        }
      }

      final DcaEntry dcaEntry = DcaEntry.builder().ticker(ticker).date(date).shares(op.getShares())
          .price(op.getPrice()).type(type).build();

      this.dcaEntryRepository.save(dcaEntry);
      operationsImported++;
    }

    // Paso 3: Recalcular todas las posiciones afectadas
    for (final String ticker : tickerFirstOp.keySet()) {
      final Position position = this.positionRepository.findById(ticker).orElse(null);
      if (position != null) {
        this.recalculatePositionFromDca(ticker, position);
      }
    }

    this.activityLog.success("DCA",
        "Importación masiva: " + operationsImported + " operaciones importadas, "
            + positionsCreated + " posiciones nuevas", null, "📥");
    log.info(
        "Importación masiva completada: {} operaciones, {} posiciones nuevas, {} warnings, {} errores",
        operationsImported, positionsCreated, warnings.size(), errors.size());

    return ImportResultDto.builder().totalOperations(operations.size())
        .positionsCreated(positionsCreated)
        .operationsImported(operationsImported).warnings(warnings).errors(errors).build();
  }
}
