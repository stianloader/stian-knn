package org.stianloader.stianknn;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.jetbrains.annotations.NotNull;

import com.github.miachm.sods.Borders;
import com.github.miachm.sods.Range;
import com.github.miachm.sods.Sheet;
import com.github.miachm.sods.SpreadSheet;
import com.github.miachm.sods.Style;

import xmlparser.XmlParser;
import xmlparser.model.XmlElement;
import xmlparser.model.XmlElement.XmlTextElement;

public class ComplexityBenchmark {
    private static final int STAR_COUNT = 50_000;
    private static final int MEASUREMENT_COUNT = 60;

    private static final Borders BORDERS_BOTTOM_ONLY = new Borders(false, true, false, false);
    private static final Borders BORDERS_RIGHT_ONLY = new Borders(false, false, false, true);

    public static void main(String[] args) throws IOException {
        SpreadSheet resultSpreadsheet = new SpreadSheet();

        List<SpatialIndexKNN<Map.@NotNull Entry<Float, Float>>> indices = new ArrayList<>();

        TestStarGenerator generator = new TestStarGenerator();
        List<Map.Entry<Float, Float>> stars = generator.generateStars(ComplexityBenchmark.STAR_COUNT);
        List<@NotNull PointObjectPair<Map.@NotNull Entry<Float, Float>>> points = new ArrayList<>(stars.size());
        for (Map.Entry<Float, Float> star : stars) {
            points.add(new PointObjectPair<>(star, star.getKey(), star.getValue()));
        }
        float width = generator.getMapWidth(ComplexityBenchmark.STAR_COUNT);
        float height = generator.getMapHeight(ComplexityBenchmark.STAR_COUNT);

        indices.add(new SpatialQueryArray<>(points, 0, 0, width, height, 4, 4));
        {
            @SuppressWarnings("deprecation")
            SpatialIndexKNN<Map.@NotNull Entry<Float, Float>> temp = new SpatialQueryArrayLegacy<>(points);
            indices.add(temp);
        }
        indices.add(new SpatialBufferedQueryArray<>(points));

        List<Sheet> resultSheets = new ArrayList<>();
        for (int i = 0; i < indices.size(); i++) {
            SpatialIndexKNN<Map.@NotNull Entry<Float, Float>> index = indices.get(i);
            if (index instanceof SpatialIndexIterable) {
                Sheet sheet = new Sheet(index.getClass().getSimpleName() + "-iterator");
                resultSheets.add(sheet);
                sheet.appendColumns(ComplexityBenchmark.MEASUREMENT_COUNT + 2);
                sheet.getRange(0, 0).setValue("n");
                for (int j = 0; j < ComplexityBenchmark.MEASUREMENT_COUNT;) {
                    Range range = sheet.getRange(0, ++j);
                    range.setValue("t" + j + "[ns]");
                    Style style = range.getStyle();
                    style.setBorders(ComplexityBenchmark.BORDERS_BOTTOM_ONLY);
                    range.setStyle(style);
                }
                sheet.getRange(0, ComplexityBenchmark.MEASUREMENT_COUNT + 1).setValue("Avg");
                sheet.getRange(0, ComplexityBenchmark.MEASUREMENT_COUNT + 2).setValue("STDEV");
            }
            Sheet sheet = new Sheet(index.getClass().getSimpleName());
            resultSheets.add(sheet);
            sheet.appendColumns(ComplexityBenchmark.MEASUREMENT_COUNT + 2);
            sheet.getRange(0, 0).setValue("n");
            for (int j = 0; j < ComplexityBenchmark.MEASUREMENT_COUNT;) {
                Range range = sheet.getRange(0, ++j);
                range.setValue("t" + j + "[ns]");
                Style style = range.getStyle();
                style.setBorders(ComplexityBenchmark.BORDERS_BOTTOM_ONLY);
                style.setBold(true);
                range.setStyle(style);
            }
            sheet.getRange(0, ComplexityBenchmark.MEASUREMENT_COUNT + 1).setValue("Avg");
            sheet.getRange(0, ComplexityBenchmark.MEASUREMENT_COUNT + 2).setValue("STDEV");
        }

        AtomicBoolean keyboardInterrupt = new AtomicBoolean();
        AtomicLong magicLong = new AtomicLong();

        new Thread(() -> {
            try {
                if (System.in.read() < 0) {
                    System.out.println("End of stream?");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            keyboardInterrupt.set(true);
            System.out.println("Magic long (I'm just here to avoid inlining games): " + magicLong.get());
        }, "keyboard-interrupt-watcher-thread").start();

        System.out.println("Press enter to stop.");
        System.out.print("Running for n = 0");
        for (int j = 1; j < ComplexityBenchmark.STAR_COUNT && !keyboardInterrupt.get(); j++) {
            String eraser = "";
            for (int i = Integer.toString(j - 1).length(); i > 0; i--) {
                eraser += "\b";
            }
            System.out.print(eraser + Integer.toString(j));

            final int neighboursFetched = j;
            for (int i = 0, n = 0; i < indices.size(); i++) {
                SpatialIndexKNN<Map.@NotNull Entry<Float, Float>> index = indices.get(i);

                if (index instanceof SpatialIndexIterable) {
                    ComplexityBenchmark.benchmarkAction(resultSheets.get(n++), j, () -> {
                        SpatialIndexIterable<Map.@NotNull Entry<Float, Float>> iterableIndex = (SpatialIndexIterable<Map.@NotNull Entry<Float, Float>>) index;
                        Iterator<Map.@NotNull Entry<Float, Float>> it = iterableIndex.createIterator((float) (Math.random() * width), (float) (Math.random() * height));
                        for (int x = 0; x < neighboursFetched; x++) {
                            Map.@NotNull Entry<Float, Float> entry = it.next();
                            magicLong.addAndGet(entry.getKey().longValue() + entry.getValue().longValue());
                        }
                    });
                }

                ComplexityBenchmark.benchmarkAction(resultSheets.get(n++), j, () -> {
                    index.queryKnn((float) (Math.random() * width), (float) (Math.random() * height), neighboursFetched, o -> {
                        magicLong.addAndGet(o.getKey().longValue() + o.getValue().longValue());
                    });
                });
            }
        }

        Sheet summarySheet = new Sheet("Summary");
        resultSpreadsheet.appendSheet(summarySheet);
        resultSheets.forEach(resultSpreadsheet::appendSheet);

        Path outputPath = Paths.get("complexity-benchmark.ods");
        try (OutputStream os = Files.newOutputStream(outputPath)) {
            resultSpreadsheet.save(os);
        }

        System.out.println(genChartObject(resultSheets.get(0).getRange(0, ComplexityBenchmark.MEASUREMENT_COUNT + 1,  resultSheets.get(0).getMaxRows(), 2)));

    }

    private static void benchmarkAction(Sheet outputSheet, int actionId, Runnable action) {
        List<Long> timeTaken = new ArrayList<>();

        for (int m = 0; m < ComplexityBenchmark.MEASUREMENT_COUNT; m++) {
            long start = System.nanoTime();
            action.run();
            timeTaken.add(System.nanoTime() - start);
        }

        outputSheet.appendRow();
        Range sheetData = outputSheet.getDataRange();
        int row = sheetData.getLastRow();
        Range headerCell = sheetData.getCell(row, 0);
        headerCell.setValue(actionId);
        Style headerStyle = headerCell.getStyle();
        headerStyle.setBold(true);
        headerStyle.setBorders(ComplexityBenchmark.BORDERS_RIGHT_ONLY);
        headerCell.setStyle(headerStyle);
        for (int m = 0; m < ComplexityBenchmark.MEASUREMENT_COUNT; m++) {
            sheetData.getCell(row, m + 1).setValue(timeTaken.get(m));
        }
        sheetData.getCell(row, ComplexityBenchmark.MEASUREMENT_COUNT + 1).setFormula("AVERAGE(" + ComplexityBenchmark.toA1Notation(row, 1) + ":" + ComplexityBenchmark.toA1Notation(row, ComplexityBenchmark.MEASUREMENT_COUNT) + ")");
        sheetData.getCell(row, ComplexityBenchmark.MEASUREMENT_COUNT + 2).setFormula("STDEV(" + ComplexityBenchmark.toA1Notation(row, 1) + ":" + ComplexityBenchmark.toA1Notation(row, ComplexityBenchmark.MEASUREMENT_COUNT) + ")");
    }

    private static final String toA1Notation(int row, int column) {
        if (column < 0) {
            throw new IndexOutOfBoundsException("column < 0");
        }
        return ComplexityBenchmark.base26(column) + Integer.toString(row + 1);
    }

    private static final String base26(int num) {
        // Create base 26 string with the characters A-Z.
        num++;
        int len = 16;
        byte[] characters = new byte[len];
        int i;
        for (i = len - 1; num != 0; i--) {
            characters[i] = (byte) ((--num % 26) + 'A');
            num /= 26;
        }
        return new String(characters, ++i, len - i, StandardCharsets.US_ASCII);
    }

    @NotNull
    private static final String toCellRangeAddress(String sheetName, int fromRow, int fromColumn, int toRow, int toColumn) {
        return sheetName + "." +  ComplexityBenchmark.toA1Notation(fromRow, fromColumn)
            + ":" + sheetName + "." + ComplexityBenchmark.toA1Notation(toRow, toColumn);
    }

    @NotNull
    private static final String toCellRangeAddress(Range range) {
        return ComplexityBenchmark.toCellRangeAddress(range.getSheet().getName(), range.getRow(), range.getColumn(), range.getLastRow(), range.getLastColumn());
    }

    @NotNull
    private static String genChartObject(Range range) {
        XmlParser parser = XmlParser.newXmlParser().charset(StandardCharsets.UTF_8).build();
        Map<String, String> documentAttributes = new HashMap<>();
        documentAttributes.put("xmlns:css3t", "http://www.w3.org/TR/css3-text/");
        documentAttributes.put("xmlns:grddl", "http://www.w3.org/2003/g/data-view#");
        documentAttributes.put("xmlns:xhtml", "http://www.w3.org/1999/xhtml");
        documentAttributes.put("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
        documentAttributes.put("xmlns:xsd", "http://www.w3.org/2001/XMLSchema");
        documentAttributes.put("xmlns:xforms", "http://www.w3.org/2002/xforms");
        documentAttributes.put("xmlns:dom", "http://www.w3.org/2001/xml-events");
        documentAttributes.put("xmlns:script", "urn:oasis:names:tc:opendocument:xmlns:script:1.0");
        documentAttributes.put("xmlns:form", "urn:oasis:names:tc:opendocument:xmlns:form:1.0");
        documentAttributes.put("xmlns:math", "http://www.w3.org/1998/Math/MathML");
        documentAttributes.put("xmlns:office", "urn:oasis:names:tc:opendocument:xmlns:office:1.0");
        documentAttributes.put("xmlns:ooo", "http://openoffice.org/2004/office");
        documentAttributes.put("xmlns:chartooo", "http://openoffice.org/2010/chart");
        documentAttributes.put("xmlns:fo", "urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0");
        documentAttributes.put("xmlns:ooow", "http://openoffice.org/2004/writer");
        documentAttributes.put("xmlns:xlink", "http://www.w3.org/1999/xlink");
        documentAttributes.put("xmlns:drawooo", "http://openoffice.org/2010/draw");
        documentAttributes.put("xmlns:oooc", "http://openoffice.org/2004/calc");
        documentAttributes.put("xmlns:dc", "http://purl.org/dc/elements/1.1/");
        documentAttributes.put("xmlns:calcext", "urn:org:documentfoundation:names:experimental:calc:xmlns:calcext:1.0");
        documentAttributes.put("xmlns:style", "urn:oasis:names:tc:opendocument:xmlns:style:1.0");
        documentAttributes.put("xmlns:text", "urn:oasis:names:tc:opendocument:xmlns:text:1.0");
        documentAttributes.put("xmlns:of", "urn:oasis:names:tc:opendocument:xmlns:of:1.2");
        documentAttributes.put("xmlns:tableooo", "http://openoffice.org/2009/table");
        documentAttributes.put("xmlns:draw", "urn:oasis:names:tc:opendocument:xmlns:drawing:1.0");
        documentAttributes.put("xmlns:dr3d", "urn:oasis:names:tc:opendocument:xmlns:dr3d:1.0");
        documentAttributes.put("xmlns:rpt", "http://openoffice.org/2005/report");
        documentAttributes.put("xmlns:formx", "urn:openoffice:names:experimental:ooxml-odf-interop:xmlns:form:1.0");
        documentAttributes.put("xmlns:svg", "urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0");
        documentAttributes.put("xmlns:chart", "urn:oasis:names:tc:opendocument:xmlns:chart:1.0");
        documentAttributes.put("xmlns:table", "urn:oasis:names:tc:opendocument:xmlns:table:1.0");
        documentAttributes.put("xmlns:meta", "urn:oasis:names:tc:opendocument:xmlns:meta:1.0");
        documentAttributes.put("xmlns:loext", "urn:org:documentfoundation:names:experimental:office:xmlns:loext:1.0");
        documentAttributes.put("xmlns:number", "urn:oasis:names:tc:opendocument:xmlns:datastyle:1.0");
        documentAttributes.put("xmlns:field", "urn:openoffice:names:experimental:ooo-ms-interop:xmlns:field:1.0");
        documentAttributes.put("office:version", "1.3");
        XmlElement document = new XmlElement(null, "office:document-content", documentAttributes);

        // TODO Automatic styles maybe???

        XmlElement officeBody = new XmlElement(document, "office:body", Collections.emptyMap());
        XmlElement officeChart = new XmlElement(officeBody, "office:chart", Collections.emptyMap());

        Map<String, String> chartAttributes = new HashMap<>();
        chartAttributes.put("svg:width", "28.884cm");
        chartAttributes.put("svg:height", "20.905cm");
        chartAttributes.put("xlink:href", "..");
        chartAttributes.put("xlink:type", "simple");
        chartAttributes.put("chart:class", "chart:line");
//        chartAttributes.put("chart:style-name", "ch1"); // TODO (urgently?)
        XmlElement chartChart = new XmlElement(officeChart, "chart:chart", chartAttributes);

        Map<String, String> legendAttributes = new HashMap<>();
        legendAttributes.put("chart:legend-position", "end");
        legendAttributes.put("svg:x", "26.393cm");
        legendAttributes.put("svg:y", "9.904cm");
        legendAttributes.put("style:legend-expansion", "high");
//        legendAttributes.put("chart:style-name", "ch2"); // TODO (urgently?)
        XmlElement chartLegend = new XmlElement(chartChart, "chart:legend", legendAttributes);

        Map<String, String> plotAreaAttributes = new HashMap<>();
//        plotAreaAttributes.put("chart:style-name", "ch3");// TODO (urgently?)
        plotAreaAttributes.put("table:cell-range-address", ComplexityBenchmark.toCellRangeAddress(range));
        plotAreaAttributes.put("chart:data-source-has-labels", "row");
        plotAreaAttributes.put("svg:x", "1.703cm");
        plotAreaAttributes.put("svg:y", "0.865cm");
        plotAreaAttributes.put("svg:width", "22.14cm");
        plotAreaAttributes.put("svg:height", "18.564cm");
        XmlElement chartPlotArea = new XmlElement(chartChart, "chart:plot-area", plotAreaAttributes);

        Map<String, String> coordRegionAttributes = new HashMap<>();
        coordRegionAttributes.put("svg:x", "3.102cm");
        coordRegionAttributes.put("svg:y", "1.064cm");
        coordRegionAttributes.put("svg:width", "20.705cm");
        coordRegionAttributes.put("svg:height", "17.429cm");
        XmlElement chartCoordinateRegion = new XmlElement(chartPlotArea, "chart:coordinate-region", coordRegionAttributes);

        Map<String, String> axisXAttributes = new HashMap<>();
        axisXAttributes.put("chart:dimension", "x");
        axisXAttributes.put("chart:name", "primary-x");
//        axisXAttributes.put("chart:style-name", "ch4"); // TODO (urgently?)
        XmlElement axisX = new XmlElement(chartPlotArea, "chart:axis", axisXAttributes);

        Map<String, String> axisYAttributes = new HashMap<>();
        axisYAttributes.put("chart:dimension", "y");
        axisYAttributes.put("chart:name", "primary-y");
//        axisYAttributes.put("chart:style-name", "ch4"); // TODO (urgently?)
        XmlElement axisY = new XmlElement(chartPlotArea, "chart:axis", axisYAttributes);

        Map<String, String> axisGridAttributes = new HashMap<>();
//      axisGridAttributes.put("chart:style-name", "ch5"); // TODO (urgently?)
        axisGridAttributes.put("chart:class", "major");
        XmlElement axisGrid = new XmlElement(axisY, "chart:grid", axisGridAttributes);

        for (int column = range.getColumn(); column <= range.getLastColumn(); column++) {
            Map<String, String> seriesAttributes = new HashMap<>();
//            seriesAttributes.put("chart:style-name", "ch6"); // TODO (urgently?)
            String valuesRange = ComplexityBenchmark.toCellRangeAddress(range.getSheet().getName(), range.getRow() + 1, column, range.getLastRow(), column);
            seriesAttributes.put("chart:values-cell-range-address", valuesRange);
            String labelRange = ComplexityBenchmark.toCellRangeAddress(range.getSheet().getName(), range.getRow(), column, range.getRow(), column);
            seriesAttributes.put("chart:label-cell-address", labelRange);
            seriesAttributes.put("chart:class", "chart:line");

            XmlElement series = new XmlElement(chartPlotArea, "chart:series", seriesAttributes);

            Map<String, String> seriesDataPointAttributes = new HashMap<>();
            seriesDataPointAttributes.put("chart:repeated", Integer.toString(range.getNumRows() - 1));
            XmlElement chartDataPoint = new XmlElement(series, "chart:data-point", seriesDataPointAttributes);

            series.appendChild(chartDataPoint);
            chartPlotArea.appendChild(series);
        }

        // TODO
        // <chart:wall chart:style-name="ch8"/>
        // <chart:floor chart:style-name="ch9"/>

        Map<String, String> tableAttributes = new HashMap<>();
        tableAttributes.put("table:name", "local-table");
        XmlElement localTable = new XmlElement(chartChart, "table:table", tableAttributes);

        XmlElement headerColumns = new XmlElement(localTable, "table:table-header-columns", Collections.emptyMap());
        XmlElement headerColumn = new XmlElement(headerColumns, "table:table-column", Collections.emptyMap());

        XmlElement tableColumns = new XmlElement(localTable, "table:table-columns", Collections.emptyMap());
        Map<String, String> tableColumnAttributes = new HashMap<>();
        tableColumnAttributes.put("table:number-columns-repeated", Integer.toString(range.getNumColumns()));
        XmlElement tableColumn = new XmlElement(tableColumns, "table:table-column", tableColumnAttributes);

        XmlElement tableHeaderRows = new XmlElement(localTable, "table:table-header-rows", Collections.emptyMap());
        XmlElement tableHeaderRow = new XmlElement(tableHeaderRows, "table:table-row", Collections.emptyMap());

        {
            XmlElement cell = new XmlElement(tableHeaderRow, "table:table-cell", Collections.emptyMap());
            tableHeaderRow.appendChild(cell);
            cell.appendChild(new XmlElement(cell, "text:p", Collections.emptyMap()));
        }

        for (int column = range.getColumn(); column <= range.getLastColumn(); column++) {
            Map<String, String> cellAttributes = new HashMap<>();
            cellAttributes.put("office:value-type", "string");

            XmlElement tableCell  = new XmlElement(tableHeaderRow, "table:table-cell", cellAttributes);
            tableHeaderRow.appendChild(tableCell);

            XmlElement cellText = new XmlElement(tableCell, "text:p", Collections.emptyMap());
            tableCell.appendChild(cellText);
            cellText.appendChild(new XmlTextElement(cellText, range.getCell(0, column - range.getColumn()).getValue().toString()));

            XmlElement cellDraw = new XmlElement(tableCell, "draw:g", Collections.emptyMap());
            tableCell.appendChild(cellDraw);
            XmlElement cellDesc = new XmlElement(cellDraw, "svg:desc", Collections.emptyMap());
            cellDraw.appendChild(cellDesc);
            String descContent = ComplexityBenchmark.toCellRangeAddress(range.getSheet().getName(), range.getRow(), column, range.getRow(), column);
            cellDesc.appendChild(new XmlTextElement(cellDesc, descContent));
        }

        XmlElement tableRows = new XmlElement(localTable, "table:table-rows", Collections.emptyMap());
        localTable.appendChild(tableRows);

        for (int row = range.getRow() + 1; row <= range.getLastRow(); row++) {
            XmlElement tableRow = new XmlElement(tableRows, "table:table-row", Collections.emptyMap());
            tableRows.appendChild(tableRow);

            Map<String, String> headerCellAttributes = new HashMap<>();
            headerCellAttributes.put("office:value-type", "string");
            XmlElement rowHeaderCell = new XmlElement(tableRow, "table:table-cell", headerCellAttributes);
            tableRow.appendChild(rowHeaderCell);
            XmlElement headerCellText = new XmlElement(rowHeaderCell, "text:p", Collections.emptyMap());
            rowHeaderCell.appendChild(headerCellText);
            headerCellText.appendChild(new XmlTextElement(headerCellText, Integer.toString(row)));

            for (int column = range.getColumn(); column <= range.getLastColumn(); column++) {
                Object spreadsheetCellValue = ComplexityBenchmark.evaluate(range.getCell(row, column - range.getColumn()));
                Map<String, String> cellAttributes = new HashMap<>();
                cellAttributes.put("office:value-type", "float");
                cellAttributes.put("office:value", spreadsheetCellValue.toString());

                XmlElement tableCell  = new XmlElement(tableHeaderRow, "table:table-cell", cellAttributes);
                tableRow.appendChild(tableCell);

                XmlElement cellText = new XmlElement(tableCell, "text:p", Collections.emptyMap());
                tableCell.appendChild(cellText);
                cellText.appendChild(new XmlTextElement(cellText, spreadsheetCellValue.toString()));

                XmlElement cellDraw = new XmlElement(tableCell, "draw:g", Collections.emptyMap());
                tableCell.appendChild(cellDraw);
                XmlElement cellDesc = new XmlElement(cellDraw, "svg:desc", Collections.emptyMap());
                cellDraw.appendChild(cellDesc);
                String descContent = ComplexityBenchmark.toCellRangeAddress(range.getSheet().getName(), range.getRow() + 1, column, range.getLastRow(), column);
                cellDesc.appendChild(new XmlTextElement(cellDesc, descContent));
            }
        }

        tableHeaderRows.appendChild(tableHeaderRow);
        localTable.appendChild(tableHeaderRow);
        tableColumns.appendChild(tableColumn);
        localTable.appendChild(tableColumns);
        headerColumns.appendChild(headerColumn);
        localTable.appendChild(headerColumns);
        chartChart.appendChild(localTable);
        axisY.appendChild(axisGrid);
        chartPlotArea.appendChild(axisY);
        chartPlotArea.appendChild(axisX);
        chartPlotArea.appendChild(chartCoordinateRegion);
        chartChart.appendChild(chartPlotArea);
        chartChart.appendChild(chartLegend);
        officeChart.appendChild(chartChart);
        officeBody.appendChild(officeChart);
        document.appendChild(officeBody);

        return Objects.requireNonNull(parser.compressXml(parser.domToXml(document)));
    }

    private static double evaluate(Range cell) {
        String spreadsheetFormula = cell.getFormula();
        if (spreadsheetFormula == null) {
            Object value = cell.getValue();
            if (value == null) {
                throw new IllegalArgumentException("Cell is empty");
            }

            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            } else {
                throw new IllegalStateException("Cannot cast to number: '" + value + "'");
            }
        }

        int functionParanthesis = spreadsheetFormula.indexOf('(');
        if (functionParanthesis < 0) {
            throw new IllegalStateException("Formula not a function?");
        } else if (spreadsheetFormula.indexOf('(', functionParanthesis + 1) > 0) {
            throw new UnsupportedOperationException("Nested functions not supported.");
        } else if (spreadsheetFormula.indexOf(')') != spreadsheetFormula.length() - 1) {
            throw new IllegalStateException("Invalid formula (rogue or missing ')' detected)");
        }

        String functionArgs = spreadsheetFormula.substring(functionParanthesis + 1, spreadsheetFormula.length() - 1);
        String functionName = spreadsheetFormula.substring(0, functionParanthesis);

        int argColon = functionArgs.indexOf(':');

        if (argColon < 0) {
            throw new UnsupportedOperationException("Functions can as of now only take in ranges.");
        } else if (functionArgs.indexOf(':', argColon + 1) >= 0) {
            throw new IllegalStateException("Multiple colons (':') in arguments.");
        } else if (functionArgs.indexOf(',') >= 0) {
            throw new UnsupportedOperationException("Multiple arguments are (as of now) unsupported.");
        }

        String fromCellA1 = functionArgs.split(":")[0];
        String toCellA1 = functionArgs.split(":")[1];

        if (fromCellA1.indexOf('.') >= 0 || toCellA1.indexOf('.') >= 0) {
            throw new UnsupportedOperationException("<table>.<a1notationcord> syntax not yet supported.");
        }

        Map.Entry<Integer, Integer> fromCell = ComplexityBenchmark.readA1Notation(fromCellA1);
        Map.Entry<Integer, Integer> toCell = ComplexityBenchmark.readA1Notation(toCellA1);

        Sheet sheet = cell.getSheet();
        Range selectedCells = sheet.getRange(fromCell.getKey(), fromCell.getValue(), toCell.getKey() - fromCell.getKey() + 1, toCell.getValue() - fromCell.getValue() + 1);

        if (selectedCells.getNumValues() == 0) {
            throw new IllegalStateException("No columns selected");
        }

        switch (functionName) {
        case "AVERAGE": {
            BigDecimal accumulator = BigDecimal.ZERO;
            for (int x = 0; x < selectedCells.getNumColumns(); x++) {
                for (int y = 0; y < selectedCells.getNumRows(); y++) {
                    accumulator = accumulator.add(BigDecimal.valueOf(ComplexityBenchmark.evaluate(selectedCells.getCell(y, x))));
                }
            }
            return accumulator.divide(BigDecimal.valueOf(selectedCells.getNumValues()), RoundingMode.DOWN).doubleValue();
        }
        case "STDEV": {
            BigDecimal accumulator = BigDecimal.ZERO;
            for (int x = 0; x < selectedCells.getNumColumns(); x++) {
                for (int y = 0; y < selectedCells.getNumRows(); y++) {
                    accumulator = accumulator.add(BigDecimal.valueOf(ComplexityBenchmark.evaluate(selectedCells.getCell(y, x))));
                }
            }

            BigDecimal average = accumulator.divide(BigDecimal.valueOf(selectedCells.getNumValues()), RoundingMode.DOWN);
            accumulator = BigDecimal.ZERO;

            for (int x = 0; x < selectedCells.getNumColumns(); x++) {
                for (int y = 0; y < selectedCells.getNumRows(); y++) {
                    BigDecimal delta = BigDecimal.valueOf(ComplexityBenchmark.evaluate(selectedCells.getCell(y, x)));
                    delta = delta.subtract(average);
                    accumulator = accumulator.add(delta.multiply(delta));
                }
            }

            return Math.sqrt(accumulator.doubleValue());
        }
        default:
            throw new UnsupportedOperationException("Unknown/Unsupported function: '" + functionName + "'");
        }
    }

    private static Map.Entry<Integer, Integer> readA1Notation(@NotNull String notation) {
        int column = 0;
        int row;

        int exponent = 0;
        for (int ch = notation.codePointAt(exponent++); ch >= 'A' && ch <= 'Z'; ch = notation.codePointAt(exponent++));

        row = Integer.parseInt(notation.substring(--exponent));

        int i = 0;
        while (exponent-- != 0) {
            column *= 26;
            column += notation.codePointAt(i++) - 'A' + 1;
        }

        return new AbstractMap.SimpleImmutableEntry<>(row - 1, column - 1);
    }
}
