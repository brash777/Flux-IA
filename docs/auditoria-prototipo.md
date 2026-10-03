# Auditoría de cifras del prototipo

Documento de referencia para sustentar por qué el backend calcula los totales en
lugar de copiarlos del prototipo.

El prototipo móvil (conservado en [prototipo-movil.html](prototipo-movil.html))
tenía 12 movimientos escritos a mano en el arreglo `TXS`. Sumándolos, las cifras
reales son:

| Concepto | Valor real | Cómo se obtiene |
|---|---:|---|
| Ingresos | **58.000** | 15.000 + 43.000 |
| Gastos | **17.250** | suma de los 10 movimientos negativos |
| Saldo | **40.750** | 58.000 − 17.250 |
| Tasa de ahorro | **70,26 %** | 40.750 / 58.000 |

## Lo que mostraba cada pantalla

| Pantalla | Qué afirmaba | Diferencia con lo real |
|---|---|---|
| Inicio (tarjeta de saldo) | saldo 45.230 · ingresos 58.000 · gastos 12.840 · ahorro 78 % | saldo +4.480 · gastos −4.410 · ahorro +7,7 pp |
| Reportes (período mes) | gastos 12.840 · ingresos 58.070 | gastos −4.410 · ingresos +70 |
| Reportes (gráfico de dona) | total 12.840 repartido en 4.623 / 2.953 / 3.595 / 1.669 | ninguno de los cuatro montos corresponde a una categoría real |
| Chat IA («resumen del mes») | gastos 12.840 · ingresos 58.000 · ahorro 45.160 (78 %) | gastos −4.410 · ahorro +4.410 |
| Chat IA («¿en qué gasté más?») | «Comida (4.570), seguido de Supermercado (5.800)» | el monto de Comida es correcto, pero Supermercado es **mayor**: el orden está invertido |

Cinco lugares, cuatro cifras distintas para el mismo mes.

## Gastos reales por categoría

Este es el orden correcto, y el que el prototipo contradecía en el chat:

| Categoría | Monto | % del gasto |
|---|---:|---:|
| Supermercado | 5.800 | 33,62 % |
| Servicios | 4.700 | 27,25 % |
| Comida | 4.570 | 26,49 % |
| Ocio | 1.880 | 10,90 % |
| Transporte | 300 | 1,74 % |
| **Total** | **17.250** | **100,00 %** |

Compárese con el gráfico de dona del prototipo, que reparte 36 % / 23 % / 28 % /
13 % sobre un total de 12.840 entre cuatro categorías que no coinciden con
ninguna de estas cinco.

## Problema adicional: las fechas no eran fechas

En `TXS` las fechas eran cadenas de texto: `'Hoy'`, `'Ayer'`, `'Lun 16'`,
`'Dom 15'`, `'Sáb 14'`. Con eso no se puede ordenar, filtrar por rango ni
agrupar por mes.

Además eran internamente inconsistentes: el chat decía «Septiembre 2026», pero
en septiembre de 2026 el día 14 es lunes, no sábado. Los rótulos corresponden al
12, 13 y 14 de septiembre, no al 14, 15 y 16.

En el backend, `occurred_at` es `TIMESTAMPTZ`.

## Qué se hizo

1. **Una sola fuente de verdad.** Toda cifra sale de un `SUM()` sobre
   `transactions`. No existe ninguna columna con un total precalculado ni
   ninguna constante numérica en el código de los reportes.

2. **Los porcentajes se calculan en el servidor**, sobre la suma efectiva del
   período. Por construcción cierran en 100,00 %; hay una prueba automatizada
   que lo verifica (`ReportServiceTest`).

3. **La semilla reproduce los 12 movimientos** con fechas reales. Verificado:
   consultando el rango de los últimos cinco días, la API devuelve exactamente
   58.000 de ingresos, 17.250 de gastos y 40.750 de neto.

4. **El contexto de la IA se arma con esas mismas consultas**, así que el
   asistente no puede contradecir lo que el usuario está viendo en pantalla.

## Consecuencia visible

Cuando el frontend se conecte a este backend, **el saldo mostrará 40.750 y no
45.230**, y los gastos 17.250 y no 12.840.

No es un error: es la cifra correcta. Las del prototipo eran valores de relleno
puestos para que el diseño se viera bien, sin relación con los datos.

## Nota sobre la semilla y el inicio de mes

La semilla coloca los 12 movimientos del prototipo en los últimos cinco días,
relativos a hoy. Si se ejecuta en los primeros días de un mes, parte de ellos
cae en el mes anterior y el reporte de «este mes» muestra solo los que quedan
dentro —lo cual es correcto, pero no reproduce los 58.000 / 17.250 en esa
pantalla.

Para una demostración donde el reporte del mes deba coincidir con la auditoría,
en `V900__semilla_demo.sql` puede cambiarse el ancla de
`date_trunc('day', now())` a `date_trunc('month', now()) + interval '13 days'`,
lo que fija los movimientos a los días 14 a 18 del mes en curso. La
contrapartida es que, antes del día 18, algunos quedarían con fecha futura.

El comportamiento actual es el que mantiene los datos semánticamente correctos.
