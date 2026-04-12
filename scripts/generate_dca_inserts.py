#!/usr/bin/env python3
"""
Convierte el CSV de aportaciones ETFs al formato SQL INSERT para la tabla dca_history.
Columnas destino: ticker (varchar 10), date (date), shares (double), price (double = importe/shares)
"""

import csv
import io

CSV_DATA = """Fecha;ETF;ISIN;Tipo;Cantidad;Importe_EUR
16/04/2025;HANetf Future of Defence;IE000OJ5TQP4;Savings plan;18,813967;250,0
23/04/2025;HANetf Future of Defence;IE000OJ5TQP4;Buy;76,08034;1001,0
02/05/2025;HANetf Future of Defence;IE000OJ5TQP4;Savings plan;7,083156;100,0
09/05/2025;HANetf Future of Defence;IE000OJ5TQP4;Savings plan;6,920415;100,0
16/05/2025;HANetf Future of Defence;IE000OJ5TQP4;Savings plan;6,752194;100,0
23/05/2025;HANetf Future of Defence;IE000OJ5TQP4;Savings plan;6,701514;100,0
02/06/2025;HANetf Future of Defence;IE000OJ5TQP4;Savings plan;6,597176;100,0
09/06/2025;HANetf Future of Defence;IE000OJ5TQP4;Savings plan;6,534239;100,0
16/06/2025;HANetf Future of Defence;IE000OJ5TQP4;Savings plan;9,736466;150,0
23/06/2025;HANetf Future of Defence;IE000OJ5TQP4;Savings plan;9,771986;150,0
02/07/2025;HANetf Future of Defence;IE000OJ5TQP4;Savings plan;9,918011;150,0
09/07/2025;HANetf Future of Defence;IE000OJ5TQP4;Savings plan;9,647543;150,0
16/07/2025;HANetf Future of Defence;IE000OJ5TQP4;Savings plan;9,686168;150,0
23/07/2025;HANetf Future of Defence;IE000OJ5TQP4;Savings plan;9,627727;150,0
04/08/2025;HANetf Future of Defence;IE000OJ5TQP4;Savings plan;9,582215;150,0
11/08/2025;HANetf Future of Defence;IE000OJ5TQP4;Savings plan;9,921947;150,0
18/08/2025;HANetf Future of Defence;IE000OJ5TQP4;Savings plan;9,766896;150,0
25/08/2025;HANetf Future of Defence;IE000OJ5TQP4;Savings plan;9,819324;150,0
02/09/2025;HANetf Future of Defence;IE000OJ5TQP4;Savings plan;9,67617;150,0
09/09/2025;HANetf Future of Defence;IE000OJ5TQP4;Savings plan;9,761811;150,0
16/09/2025;HANetf Future of Defence;IE000OJ5TQP4;Savings plan;6,210408;100,0
23/09/2025;HANetf Future of Defence;IE000OJ5TQP4;Savings plan;6,14326;100,0
02/10/2025;HANetf Future of Defence;IE000OJ5TQP4;Savings plan;5,899705;100,0
09/10/2025;HANetf Future of Defence;IE000OJ5TQP4;Savings plan;5,756389;100,0
16/10/2025;HANetf Future of Defence;IE000OJ5TQP4;Savings plan;6,075334;100,0
23/10/2025;HANetf Future of Defence;IE000OJ5TQP4;Savings plan;5,978;100,0
03/11/2025;HANetf Future of Defence;IE000OJ5TQP4;Savings plan;5,847953;100,0
10/11/2025;HANetf Future of Defence;IE000OJ5TQP4;Savings plan;6,037918;100,0
17/11/2025;HANetf Future of Defence;IE000OJ5TQP4;Savings plan;6,14326;100,0
24/11/2025;HANetf Future of Defence;IE000OJ5TQP4;Savings plan;6,588483;100,0
02/12/2025;HANetf Future of Defence;IE000OJ5TQP4;Savings plan;6,601531;100,0
09/12/2025;HANetf Future of Defence;IE000OJ5TQP4;Savings plan;6,297229;100,0
16/12/2025;HANetf Future of Defence;IE000OJ5TQP4;Savings plan;6,454111;100,0
23/12/2025;HANetf Future of Defence;IE000OJ5TQP4;Savings plan;6,25;100,0
02/01/2026;HANetf Future of Defence;IE000OJ5TQP4;Savings plan;6,274312;100,0
09/01/2026;HANetf Future of Defence;IE000OJ5TQP4;Savings plan;5,755064;100,0
16/01/2026;HANetf Future of Defence;IE000OJ5TQP4;Savings plan;8,299214;150,0
23/01/2026;HANetf Future of Defence;IE000OJ5TQP4;Savings plan;8,530482;150,0
02/02/2026;HANetf Future of Defence;IE000OJ5TQP4;Savings plan;5,86579;100,0
09/02/2026;HANetf Future of Defence;IE000OJ5TQP4;Savings plan;5,921364;100,0
23/04/2025;iShares Core S&P 500;IE00B5BMR087;Buy;1,989258;1001,04
02/05/2025;iShares Core S&P 500;IE00B5BMR087;Savings plan;0,189136;100,0
09/05/2025;iShares Core S&P 500;IE00B5BMR087;Savings plan;0,186254;100,0
16/05/2025;iShares Core S&P 500;IE00B5BMR087;Savings plan;0,177645;100,0
23/05/2025;iShares Core S&P 500;IE00B5BMR087;Savings plan;0,184243;100,0
02/06/2025;iShares Core S&P 500;IE00B5BMR087;Savings plan;0,273473;150,0
16/06/2025;iShares Core S&P 500;IE00B5BMR087;Savings plan;0,180362;100,0
02/07/2025;iShares Core S&P 500;IE00B5BMR087;Savings plan;0,177967;100,0
16/07/2025;iShares Core S&P 500;IE00B5BMR087;Savings plan;0,173593;100,0
04/08/2025;iShares Core S&P 500;IE00B5BMR087;Savings plan;0,1722;100,0
18/08/2025;iShares Core S&P 500;IE00B5BMR087;Savings plan;0,169669;100,0
02/09/2025;iShares Core S&P 500;IE00B5BMR087;Savings plan;0,170386;100,0
16/09/2025;iShares Core S&P 500;IE00B5BMR087;Savings plan;0,16724;100,0
02/10/2025;iShares Core S&P 500;IE00B5BMR087;Savings plan;0,163169;100,0
16/10/2025;iShares Core S&P 500;IE00B5BMR087;Savings plan;0,162898;100,0
03/11/2025;iShares Core S&P 500;IE00B5BMR087;Savings plan;0,157381;100,0
02/12/2025;iShares Core S&P 500;IE00B5BMR087;Savings plan;0,158538;100,0
16/12/2025;iShares Core S&P 500;IE00B5BMR087;Savings plan;0,161775;100,0
02/01/2026;iShares Core S&P 500;IE00B5BMR087;Savings plan;0,158881;100,0
16/01/2026;iShares Core S&P 500;IE00B5BMR087;Savings plan;0,155904;100,0
02/02/2026;iShares Core S&P 500;IE00B5BMR087;Savings plan;0,158498;100,0
16/02/2026;iShares Core S&P 500;IE00B5BMR087;Savings plan;0,161739;100,0
02/03/2026;iShares Core S&P 500;IE00B5BMR087;Savings plan;0,00174;1,09
02/03/2026;iShares Core S&P 500;IE00B5BMR087;Savings plan;0,159678;100,0
16/03/2026;iShares Core S&P 500;IE00B5BMR087;Savings plan;0,159672;100,0
02/04/2026;iShares Core S&P 500;IE00B5BMR087;Savings plan;0,002106;1,28
02/04/2026;iShares Core S&P 500;IE00B5BMR087;Savings plan;0,164614;100,0
19/12/2025;VanEck Uranium and Nuclear;IE000M7V94E1;Buy;7,0;332,17
12/01/2026;VanEck Uranium and Nuclear;IE000M7V94E1;Buy;5,0;269,15
12/01/2026;VanEck Uranium and Nuclear;IE000M7V94E1;Buy;9,0;483,31
12/01/2026;VanEck Uranium and Nuclear;IE000M7V94E1;Buy;0,330098;17,69
16/01/2026;VanEck Uranium and Nuclear;IE000M7V94E1;Savings plan;3,981596;225,0
23/01/2026;VanEck Uranium and Nuclear;IE000M7V94E1;Buy;8,0;461,88
23/01/2026;VanEck Uranium and Nuclear;IE000M7V94E1;Savings plan;3,896778;225,0
23/01/2026;VanEck Uranium and Nuclear;IE000M7V94E1;Buy;0,470751;27,12
02/02/2026;VanEck Uranium and Nuclear;IE000M7V94E1;Savings plan;9,082652;500,0
05/02/2026;VanEck Uranium and Nuclear;IE000M7V94E1;Buy;14,0;741,04
05/02/2026;VanEck Uranium and Nuclear;IE000M7V94E1;Buy;0,193792;10,24
09/02/2026;VanEck Uranium and Nuclear;IE000M7V94E1;Savings plan;8,873114;500,0
13/02/2026;VanEck Uranium and Nuclear;IE000M7V94E1;Buy;9,0;482,41
13/02/2026;VanEck Uranium and Nuclear;IE000M7V94E1;Buy;0,333582;17,84
16/02/2026;VanEck Uranium and Nuclear;IE000M7V94E1;Savings plan;1,861504;100,0
23/02/2026;VanEck Uranium and Nuclear;IE000M7V94E1;Savings plan;1,813236;100,0
02/03/2026;VanEck Uranium and Nuclear;IE000M7V94E1;Savings plan;0,878425;50,0
09/03/2026;VanEck Uranium and Nuclear;IE000M7V94E1;Buy;0,033762;1,68
09/03/2026;VanEck Uranium and Nuclear;IE000M7V94E1;Buy;7,0;349,32
09/03/2026;VanEck Uranium and Nuclear;IE000M7V94E1;Savings plan;0,978282;50,0
16/03/2026;VanEck Uranium and Nuclear;IE000M7V94E1;Savings plan;0,963948;50,0
20/03/2026;VanEck Uranium and Nuclear;IE000M7V94E1;Buy;0,229132;11,2
20/03/2026;VanEck Uranium and Nuclear;IE000M7V94E1;Buy;10,0;489,8
23/03/2026;VanEck Uranium and Nuclear;IE000M7V94E1;Savings plan;1,005935;50,0
02/04/2026;VanEck Uranium and Nuclear;IE000M7V94E1;Savings plan;0,995024;50,0
09/04/2026;VanEck Uranium and Nuclear;IE000M7V94E1;Savings plan;0,991669;50,0
16/02/2026;WisdomTree Europe Defence;IE0002Y8CX98;Savings plan;1,48478;50,0
18/02/2026;WisdomTree Europe Defence;IE0002Y8CX98;Buy;0,361625;12,59
18/02/2026;WisdomTree Europe Defence;IE0002Y8CX98;Buy;14,0;488,41
23/02/2026;WisdomTree Europe Defence;IE0002Y8CX98;Savings plan;1,445922;50,0
02/03/2026;WisdomTree Europe Defence;IE0002Y8CX98;Savings plan;2,153316;75,0
09/03/2026;WisdomTree Europe Defence;IE0002Y8CX98;Savings plan;2,209456;75,0
16/03/2026;WisdomTree Europe Defence;IE0002Y8CX98;Savings plan;2,179915;75,0
23/03/2026;WisdomTree Europe Defence;IE0002Y8CX98;Savings plan;2,381708;75,0
02/04/2026;WisdomTree Europe Defence;IE0002Y8CX98;Savings plan;2,23181;75,0
09/04/2026;WisdomTree Europe Defence;IE0002Y8CX98;Savings plan;2,216312;75,0
16/02/2026;VanEck Defense UCITS ETF;IE000YYE6WK5;Savings plan;0,877963;50,0
23/02/2026;VanEck Defense UCITS ETF;IE000YYE6WK5;Savings plan;0,844737;50,0
02/03/2026;VanEck Defense UCITS ETF;IE000YYE6WK5;Savings plan;0,802954;50,0
16/03/2026;VanEck Defense UCITS ETF;IE000YYE6WK5;Savings plan;0,798339;50,0
02/04/2026;VanEck Defense UCITS ETF;IE000YYE6WK5;Savings plan;0,828775;50,0
02/03/2026;VanEck Space Innovators;IE000YU9K6K2;Savings plan;0,736051;50,0
09/03/2026;VanEck Space Innovators;IE000YU9K6K2;Savings plan;0,727061;50,0
16/03/2026;VanEck Space Innovators;IE000YU9K6K2;Savings plan;0,69252;50,0
23/03/2026;VanEck Space Innovators;IE000YU9K6K2;Savings plan;0,694927;50,0
02/04/2026;VanEck Space Innovators;IE000YU9K6K2;Savings plan;0,690512;50,0
09/04/2026;VanEck Space Innovators;IE000YU9K6K2;Savings plan;0,614779;50,0"""

# Mapeo de nombre ETF → ticker (max 10 chars)
ETF_TO_TICKER = {
    "HANetf Future of Defence": "ASWC",
    "iShares Core S&P 500": "CSPX",
    "VanEck Uranium and Nuclear": "NUKL",
    "WisdomTree Europe Defence": "EUDF",
    "VanEck Defense UCITS ETF": "DFEN",
    "VanEck Space Innovators": "JEDI",
}

def parse_decimal(value: str) -> float:
    """Convierte formato europeo (coma decimal) a float."""
    return float(value.replace(",", "."))

def parse_date(date_str: str) -> str:
    """Convierte dd/MM/yyyy a yyyy-MM-dd."""
    parts = date_str.strip().split("/")
    return f"{parts[2]}-{parts[1]}-{parts[0]}"

lines = []
reader = csv.DictReader(io.StringIO(CSV_DATA), delimiter=";")

for row in reader:
    etf_name = row["ETF"].strip()
    ticker = ETF_TO_TICKER.get(etf_name)
    if not ticker:
        print(f"-- WARNING: ETF desconocido: {etf_name}")
        continue

    date = parse_date(row["Fecha"])
    shares = parse_decimal(row["Cantidad"])
    importe = parse_decimal(row["Importe_EUR"])
    price = round(importe / shares, 6)

    lines.append(
        f"  ('{ticker}', '{date}', {shares}, {price})"
    )

print("-- =========================================================")
print("-- Inserción de historial DCA desde CSV de aportaciones ETFs")
print("-- Generado automáticamente")
print("-- =========================================================")
print("-- Mapeo de ETFs a tickers:")
for etf, ticker in ETF_TO_TICKER.items():
    print(f"--   {etf} -> {ticker}")
print("-- =========================================================")
print()
print("INSERT INTO dca_history (ticker, date, shares, price) VALUES")
print(",\n".join(lines))
print(";")

