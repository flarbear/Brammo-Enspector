/*
 * Copyright 2013, Jim Graham, Flarbear Widgets
 */
package org.flarbear;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.XYChart.Data;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;

/**
 * @author Flar
 */
public class EmpulseLogInspector extends Application {
    static Calendar calendar = Calendar.getInstance();

    Stage stage;
    TabPane tabs;
    CheckBox VinPrivacy;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        BorderPane pane = new BorderPane();

        pane.setTop(makeButtons());
        pane.setCenter(makeList());

        Scene scene = new Scene(pane, 1024, 768);
        scene.getStylesheets().add("org/flarbear/stylesheet.css");
        
        stage.setTitle("Brammo Empulse Log Inspector");
        stage.setScene(scene);
        stage.show();
    }

    Node makeButtons() {
        FlowPane pane = new FlowPane(10, 10);
        Button loadButton = new Button("Load log file");
        loadButton.addEventHandler(ActionEvent.ACTION, new EventHandler<ActionEvent>() {
            @Override public void handle(ActionEvent t) { loadLogs(); }
        });
        VinPrivacy = new CheckBox("Hide VIN");
        VinPrivacy.setSelected(true);
        VinPrivacy.addEventHandler(ActionEvent.ACTION, new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent t) {
                for (Tab tab : tabs.getTabs()) {
                    TableView<LogEntry> curTableView =
                        (TableView<LogEntry>) tab.getContent();
                    List<LogEntry> items = curTableView.getItems();
                    int nitems = items.size();
                    for (int i = 0; i < nitems; i++) {
                        LogEntry entry = items.get(i);
                        if (entry instanceof VINEntry) {
                            items.set(i, ((VINEntry) entry).replacement());
                        }
                    }
                }
            }
        });
        Button chartOneButton = new Button("Chart File SoC");
        chartOneButton.addEventHandler(ActionEvent.ACTION, new EventHandler<ActionEvent>() {
            @Override public void handle(ActionEvent t) { graphOneLog(); }
        });
        Button chartAllButton = new Button("Chart All SoC");
        chartAllButton.addEventHandler(ActionEvent.ACTION, new EventHandler<ActionEvent>() {
            @Override public void handle(ActionEvent t) { graphAllLogs(); }
        });
        Button chartOneModuleButton = new Button("Chart File SoC Per Module");
        chartOneModuleButton.addEventHandler(ActionEvent.ACTION, new EventHandler<ActionEvent>() {
            @Override public void handle(ActionEvent t) { graphOneLogPerModule(); }
        });
        Button chartAllModuleButton = new Button("Chart All SoC Per Module");
        chartAllModuleButton.addEventHandler(ActionEvent.ACTION, new EventHandler<ActionEvent>() {
            @Override public void handle(ActionEvent t) { graphAllLogsPerModule(); }
        });
        pane.getChildren().addAll(loadButton,
                                  chartOneButton, chartAllButton,
                                  chartOneModuleButton, chartAllModuleButton,
                                  VinPrivacy);
        return pane;
    }

    FileChooser chooser;
    void loadLogs() {
        if (chooser == null) {
            chooser = new FileChooser();
            chooser.setTitle("Open Empulse Log File");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Empulse Log Files", "*.chg", "*.drv"));
        }
        List<File> files = chooser.showOpenMultipleDialog(stage.getOwner());
        if (files != null) {
//            long start = System.currentTimeMillis();
            for (File f : files) {
                chooser.setInitialDirectory(f.getParentFile());
//                System.out.println("open file: "+f);
                loadLogFile(f);
            }
//            long end = System.currentTimeMillis();
//            System.out.println(files.size()+" files loaded in "+(end-start)/1000.0+"seconds");
        }
    }

    void graphOneLog() {
        long timebounds[] = { Integer.MAX_VALUE, Integer.MIN_VALUE };
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName("Battery SoC %");
        Tab t = tabs.getSelectionModel().getSelectedItem();
        fillSoCSeries(t, timebounds, -1, series.getData());
        showSoCGraph(timebounds, series);
    }

    void graphOneLogPerModule() {
        long timebounds[] = { Integer.MAX_VALUE, Integer.MIN_VALUE };
        Tab t = tabs.getSelectionModel().getSelectedItem();
        List<XYChart.Series<Number, Number>> serieslist = new ArrayList<>(7);
        for (int i = 1; i <= 7; i++) {
            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            series.setName("Module "+i+" SoC %");
            serieslist.add(series);
            fillSoCSeries(t, timebounds, i, series.getData());
        }
        showSoCGraph(timebounds, serieslist);
    }

    void graphAllLogs() {
        long timebounds[] = { Integer.MAX_VALUE, Integer.MIN_VALUE };
        // Note: accumulate data in a list for faster sorting before committing
        // the entries to the ObservableList in the Series...
        List<XYChart.Data<Number, Number>> list = new ArrayList<>();
        for (Tab t : tabs.getTabs()) {
            fillSoCSeries(t, timebounds, -1, list);
        }
        sortByDate(list);
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.getData().setAll(list);
        series.setName("Battery SoC %");
        showSoCGraph(timebounds, series);
    }

    void graphAllLogsPerModule() {
        long timebounds[] = { Integer.MAX_VALUE, Integer.MIN_VALUE };
        // Note: accumulate data in a list for faster sorting before committing
        // the entries to the ObservableList in the Series...
        List<XYChart.Series<Number, Number>> serieslist = new ArrayList<>(7);
        for (int i = 1; i < 7; i++) {
            List<XYChart.Data<Number, Number>> list = new ArrayList<>();
            for (Tab t : tabs.getTabs()) {
                fillSoCSeries(t, timebounds, i, list);
            }
            sortByDate(list);
            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            series.getData().setAll(list);
            series.setName("Module"+i+" SoC %");
            serieslist.add(series);
        }
        showSoCGraph(timebounds, serieslist);
    }

    void sortByDate(List<Data<Number, Number>> list) {
        Collections.sort(list, new Comparator<XYChart.Data<Number, Number>>() {
            @Override
            public int compare(XYChart.Data<Number, Number> o1,
                               XYChart.Data<Number, Number> o2)
            {
                return Long.compare(o1.getXValue().longValue(),
                                    o2.getXValue().longValue());
            }
        });
    }

    void fillSoCSeries(Tab tab, long[] timebounds, int modulenum,
                       List<XYChart.Data<Number, Number>> list)
    {
        TableView<LogEntry> table = (TableView<LogEntry>) tab.getContent();
        boolean prevomitted = false;
        long prevsec = -1;
        double prevsoc = -1;
        int totalentries = 0;
        int omitentries = 0;
        for (LogEntry entry : table.getItems()) {
            if (entry instanceof BatteryStatusEntry) {
                BatteryStatusEntry bes = (BatteryStatusEntry) entry;
                long sec = bes.getSecond();
                double soc = 100.0 * (modulenum < 0
                                      ? bes.getOverallSoC()
                                      : bes.getModuleSoC(modulenum));
                totalentries++;
                if (soc == prevsoc) {
                    if (prevomitted) {
                        omitentries++;
                    }
                    prevomitted = true;
                } else {
                    if (prevomitted) {
                        list.add(new XYChart.Data<>((Number) prevsec, (Number) prevsoc));
                    }
                    list.add(new XYChart.Data<>((Number) sec, (Number) soc));
                    prevomitted = false;
                }
                prevsec = sec;
                prevsoc = soc;
                timebounds[0] = Math.min(timebounds[0], sec);
                timebounds[1] = Math.max(timebounds[1], sec);
            }
        }
        if (prevomitted) {
            list.add(new XYChart.Data<>((Number) prevsec, (Number) prevsoc));
        }
        System.out.println(omitentries+" omitted out of "+totalentries);
    }

    static class TickStrategy {
        long cutoff;
        long tickunits;
        int minorticks;
        StringConverter formatter;

        public TickStrategy(long cutoff, long tickunits, int minorticks,
                            StringConverter formatter)
        {
            this.cutoff = cutoff;
            this.tickunits = tickunits;
            this.minorticks = minorticks;
            this.formatter = formatter;
        }
    }
    static class SecondConverter extends StringConverter<Number> {
        SimpleDateFormat formatter;

        SecondConverter(String format) {
            this.formatter = new SimpleDateFormat(format);
        }

        @Override
        public String toString(Number t) {
            calendar.setTimeInMillis(t.longValue() * 1000);
            return formatter.format(calendar.getTime());
        }

        @Override
        public Number fromString(String string) {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }
    private static final SecondConverter datetimeFormatter =
        new SecondConverter("MM/dd/yyyy\nHH:mm:ss");
    private static final SecondConverter dateFormatter =
        new SecondConverter("MM/dd/yyyy");
    private static final SecondConverter yearFormatter =
        new SecondConverter("yyyy");
    private static final long MINUTES = 60;
    private static final long HOURS = MINUTES * 60;
    private static final long DAYS = HOURS * 24;
    private static final long WEEKS = DAYS * 7;
    private static final long MONTHS = DAYS * 31;
    private static final long YEARS = DAYS * 365;
    private static final TickStrategy strategies[] = {
        new TickStrategy(YEARS, YEARS, 12, yearFormatter),
        new TickStrategy(MONTHS, WEEKS, 7, dateFormatter),
        new TickStrategy(WEEKS, DAYS, 4, dateFormatter),
        new TickStrategy(DAYS, DAYS, 4, dateFormatter),
        new TickStrategy(HOURS, HOURS, 4, datetimeFormatter),
    };
    private static final TickStrategy backupStrategy =
        new TickStrategy(0, MINUTES*10, 10, datetimeFormatter);
    static TickStrategy getTickStrategy(long seconds) {
        for (TickStrategy ts : strategies) {
            if (seconds >= ts.cutoff) {
                return ts;
            }
        }
        return backupStrategy;
    }

    static void showSoCGraph(long[] timebounds, Series<Number, Number> series) {
        List<XYChart.Series<Number, Number>> serieslist = new ArrayList<>(1);
        serieslist.add(series);
        showSoCGraph(timebounds, serieslist);
    }

    static void showSoCGraph(long[] timebounds, List<XYChart.Series<Number, Number>> serieslist) {
        final TickStrategy ts = getTickStrategy(timebounds[1] - timebounds[0]);
        NumberAxis xAxis = new NumberAxis("Date/Time", timebounds[0], timebounds[1], ts.tickunits);
        xAxis.setMinorTickCount(ts.minorticks);
        xAxis.setTickLabelFormatter(ts.formatter);
        NumberAxis yAxis = new NumberAxis("SoC %", -5, 105, 10);
        LineChart<Number, Number> lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.getData().addAll(serieslist);
        lineChart.setCreateSymbols(false);
        lineChart.setTitle("SoC % over "+datetimeFormatter.toString(timebounds[0])+
                           " to "+datetimeFormatter.toString(timebounds[1]));
        lineChart.setLegendSide(Side.RIGHT);

        Scene scene = new Scene(lineChart, 800, 600);
        Stage chartStage = new Stage();
        chartStage.setScene(scene);
        chartStage.show();
    }

    class LogFileLoader extends Task<TableView<LogEntry>> {
        File file;

        LogFileLoader(File file) {
            this.file = file;
        }

        @Override
        protected TableView<LogEntry> call() {
            byte log[] = readLogFileFully(file);
            if (log == null) {
                return null;
            }
            TableView<LogEntry> tableView = createLogEntryList(log);
            return tableView;
        }
    }

    static int toDecimal(byte b) {
        return ((b & 0xf0) >> 4) * 10 + (b & 0xf);
    }

    static boolean checkDate(byte buf[], int off) {
        if (buf[off + 0] != '<' || buf[off + 7] != '>') {
            System.err.println("no brackets for date in stream");
            return false;
        }
        return true;
    }

    static String parseDate(byte buf[], int off) {
        int Y = buf[off + 0] & 0xff;
        int M = buf[off + 1] & 0xff;
        int D = buf[off + 2] & 0xff;
        int h = buf[off + 3] & 0xff;
        int m = buf[off + 4] & 0xff;
        int s = buf[off + 5] & 0xff;
        if (Y == ' ' && M == ' ' && D == ' ' && h == ' ' && m == ' ' && s == ' ') {
            return "No Date";
        }
        return String.format("20%02X/%02X/%02X %02X:%02X:%02X", Y, M, D, h, m, s);
    }

    static byte[] readLogFileFully(File f) {
        long fsize = f.length();
        if (fsize < 0 || fsize >= Integer.MAX_VALUE) {
            return null;
        }
        try {
            FileInputStream fis = new FileInputStream(f);
            byte buf[] = new byte[(int) fsize];
            int nloaded = 0;
            while (nloaded < buf.length) {
                int nread = fis.read(buf, nloaded, buf.length - nloaded);
                if (nread <= 0) {
                    return null;
                }
                nloaded += nread;
            }
            return buf;
        } catch (IOException e) {
            return null;
        }
    }

    public static class LogEntry {
        final byte log[];
        final int code;
        final int offset;
        final int datalength;

        LogEntry(byte log[], int code, int offset, int datalength) {
            this.log = log;
            this.code = code;
            this.offset = offset;
            this.datalength = datalength;
        }

        public int getDatalength() {
            return datalength;
        }

        public long getSecond() {
            int Y = toDecimal(log[offset + 1]);
            int M = toDecimal(log[offset + 2]);
            int D = toDecimal(log[offset + 3]);
            int h = toDecimal(log[offset + 4]);
            int m = toDecimal(log[offset + 5]);
            int s = toDecimal(log[offset + 6]);
            if (Y == 20 && M == 20 && D == 20 && h == 20 && m == 20 && s == 20) {
                return -1;
            }
            calendar.set(2000+Y, M-1, D, h, m, s);
            return (calendar.getTimeInMillis() / 1000);
        }

        public String getDateTime() {
            return parseDate(log, offset+1);
        }

        public String getCode() {
            return Character.toString((char) code);
        }

        public String getData() {
            StringBuilder sb = new StringBuilder(datalength);
            appendDataString(sb);
            return sb.toString();
        }

        static char HEXDIGITS[] = { '0', '1', '2', '3',
                                    '4', '5', '6', '7',
                                    '8', '9', 'A', 'B',
                                    'C', 'D', 'E', 'F' };
        void appendDataString(StringBuilder sb) {
            int datastart = offset + 9;
            for (int i = 0; i < datalength; i++) {
                if (i > 0) {
                    sb.append((i & 0x1f) == 0 ? '\n' : ' ');
                }
                int data = log[datastart + i] & 0xff;
                sb.append(HEXDIGITS[data >> 4]);
                sb.append(HEXDIGITS[data & 0xf]);
            }
        }

        void appendAscii(StringBuilder sb, int off, int len) {
            for (int i = 0; i < len; i++) {
                sb.append((char) log[off + i]);
            }
        }
    }

    static class AsciiEntry extends LogEntry {
        AsciiEntry(byte log[], int code, int offset, int datalength) {
            super(log, code, offset, datalength);
        }

        @Override
        void appendDataString(StringBuilder sb) {
            appendAscii(sb, offset + 9, datalength);
        }
    }

    class VINEntry extends LogEntry {
        VINEntry(byte log[], int offset) {
            super(log, 'V', offset, 18);
        }

        public VINEntry replacement() {
            return new VINEntry(log, offset);
        }

        @Override
        void appendDataString(StringBuilder sb) {
            sb.append("VIN: ");
            if (VinPrivacy.isSelected()) {
                sb.append("(obscured for privacy)");
            } else {
                appendAscii(sb, offset + 9, 17);
            }
        }
    }

    static class BatteryStatusEntry extends LogEntry {
        BatteryStatusEntry(byte log[], int offset) {
            super(log, 'B', offset, 144);
        }

        double getOverallSoC() {
            int soc = 0;
            for (int i = offset + 9; i < offset + 16; i++) {
                soc += log[i] & 0xff;
            }
            // 0 <= soc <= 7 * 255
            return soc / 7.0 / 255.0;
        }

        double getModuleSoC(int modulenum) {
            int soc = log[offset+8+modulenum] & 0xff;
            // 0 <= soc <= 255
            return soc / 255.0;
        }

        @Override
        void appendDataString(StringBuilder sb) {
            sb.append("SOC: ");
            sb.append(Math.round(getOverallSoC() * 1000.0) / 10.0);
            String sep = "% (";
            for (int i = 1; i <= 7; i++) {
                sb.append(sep);
                sep = ", ";
                sb.append(Math.round(getModuleSoC(i) * 1000.0) / 10.0);
            }
            sb.append(")\n");
            super.appendDataString(sb);
        }
    }

//    static File theFile;
    void loadLogFile(File f) {
        byte log[] = readLogFileFully(f);
        if (log == null) {
            return;
        }
//        theFile = f;
        TableView<LogEntry> tableView = createLogEntryList(log);
//        theFile = null;
        Tab tab = new Tab(f.getName());
        tab.setContent(tableView);
        tabs.getTabs().add(tab);
    }

    TableView<LogEntry> createLogEntryList(byte[] log) {
        int predicteditems = (log.length / (17 + 144 + 32 + 35 + 7*46 + 9*11)) * 11 + 3;
        List<LogEntry> items = new ArrayList<>(predicteditems);
        int off = 0;
        while (off < log.length) {
            if (!checkDate(log, off)) {
                break;
            }
            int code = log[off + 8] & 0xff;
            LogEntry entry;
            switch (code) {
                case 'I': entry = new LogEntry(log, code, off, 16); break;
                case 'M': entry = new LogEntry(log, code, off, 46); break;
                case 'D': entry = new LogEntry(log, code, off, 17); break;
                case 'B': entry = new BatteryStatusEntry(log, off); break;
                case 'E': entry = new LogEntry(log, code, off, 32); break;
                case 'F': entry = new LogEntry(log, code, off, 35); break;
                case 'C': entry = new AsciiEntry(log, code, off, 72); break;
                case 'V': entry = new VINEntry(log, off); break;
                default: {
                    int i;
                    for (i = off + 9; i < log.length; i++) {
                        if (log[i] == '<' && i+7 < log.length && log[i+7] == '>') {
                            break;
                        }
                    }
                    int datalen = i - (off + 9);
                    entry = new LogEntry(log, code, off, datalen);
                    System.out.println(String.format("Entry (%c) has data length: %d",
                                                     code, datalen));
                    break;
                }
            }
            off += 9 + entry.getDatalength();
            items.add(entry);
        }

//        if (items.size() != predicteditems) {
//            System.out.println("predicted "+(predicteditems+1)+" items for "+
//                               log.length+" sized log ("+theFile+"), "+
//                               "but got "+items.size());
//        }

        TableColumn<LogEntry, String> datetimeColumn = new TableColumn<>("Date/Time");
        datetimeColumn.setCellValueFactory(new PropertyValueFactory("DateTime"));
        datetimeColumn.setSortable(false);
        TableColumn<LogEntry, String> codeColumn = new TableColumn<>("Code");
        codeColumn.setCellValueFactory(new PropertyValueFactory("Code"));
        codeColumn.setPrefWidth(40);
        codeColumn.setSortable(false);
        TableColumn<LogEntry, String> dataColumn = new TableColumn<>("Data");
        dataColumn.setCellValueFactory(new PropertyValueFactory("Data"));
        dataColumn.setSortable(false);

        TableView<LogEntry> tableView = new TableView<>(FXCollections.observableList(items));
        tableView.getSelectionModel().setCellSelectionEnabled(true);
        tableView.getColumns().setAll(datetimeColumn, codeColumn, dataColumn);
        return tableView;
    }

    Node makeList() {
        tabs = new TabPane();
        return tabs;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
