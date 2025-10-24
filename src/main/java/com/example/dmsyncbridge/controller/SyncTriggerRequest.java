package com.example.dmsyncbridge.controller;

import java.util.Collections;
import java.util.List;

public class SyncTriggerRequest {

    private List<String> tableNames;

    public List<String> getTableNames() {
        return tableNames == null ? Collections.emptyList() : tableNames;
    }

    public void setTableNames(List<String> tableNames) {
        this.tableNames = tableNames;
    }
}