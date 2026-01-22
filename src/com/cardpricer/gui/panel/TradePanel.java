package com.cardpricer.gui.panel;

import com.cardpricer.service.CsvExportService;
import com.cardpricer.service.ScryfallApiService;

import javax.swing.*;

public class TradePanel extends JPanel {
    private final ScryfallApiService apiService;
    private final CsvExportService csvService;

    public TradePanel() {
        this.apiService = new ScryfallApiService();
        this.csvService = new CsvExportService();


    }
}
